package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.ALREADY_USED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CAPACITY_REACHED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.Clock

/**
 * Owns entrant join, withdrawal and decline transactions.
 * @param db Firestore client for this operation family.
 * @param clock Shared time source for validation and recorded facts.
 */
internal class LotteryMembership(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) {
    /**
     * Builds a validated reference to the lottery document.
     *
     * @param id Lottery document ID.
     */
    private fun ref(id: String) =
        db.document(FirestorePaths.lottery(id))

    /**
     * Joins or rejoins during registration, updating membership and waiting count
     * atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun join(
        id: String,
        accountId: String,
    ) =
        membership(id, accountId, true)

    /**
     * Withdraws during registration, updating membership and waiting count atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun withdraw(
        id: String,
        accountId: String,
    ) =
        membership(id, accountId, false)

    /**
     * Applies an idempotent join/withdraw transaction while validating the window and
     * capacity.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     * @param joining True to join/rejoin, false to withdraw.
     */
    private suspend fun membership(
        id: String,
        accountId: String,
        joining: Boolean,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, accountId, Role.ENTRANT)
            val lottery = tx.require(ref(id), LotteryDocument)
            val now = clock.instant()
            requireOperation(
                lottery.registrationIsOpen(now),
                WINDOW_CLOSED,
                "Registration is closed",
            )
            tx.activeAccount(db, lottery.ownerId, Role.OWNER)
            val entryRef = db.document(FirestorePaths.entry(id, accountId))
            val old = tx.read(entryRef, LotteryEntryDocument)
            if (joining && old?.state == EntryState.WAITING) return@atomic
            if (!joining && (old == null || old.state == EntryState.WITHDRAWN)) return@atomic
            requireOperation(
                old == null || old.state in setOf(EntryState.WAITING, EntryState.WITHDRAWN),
                INVALID_STATE,
                "Entry cannot change membership",
            )
            val count = lottery.waitingCount + if (joining) 1 else -1
            requireOperation(
                !joining ||
                    lottery.configuration.waitlistCapacity == null ||
                    count <= lottery.configuration.waitlistCapacity,
                CAPACITY_REACHED,
                "Waitlist is full",
            )
            requireOperation(count >= 0, INVALID_STATE, "Invalid waiting count")
            val entry =
                LotteryEntry(
                    id,
                    accountId,
                    if (joining) EntryState.WAITING else EntryState.WITHDRAWN,
                    if (joining) now else old!!.joinedAt,
                    now,
                )
            tx.set(entryRef, LotteryEntryDocument.encode(entry))
            tx.update(ref(id), "waitingCount", count)
        }
    }

    /**
     * Declines an unused winning opportunity during booking; existing reservations prevent
     * decline.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun decline(
        id: String,
        accountId: String,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, accountId, Role.ENTRANT)
            val lottery = tx.require(ref(id), LotteryDocument)
            requireOperation(
                lottery.bookingIsOpen(clock.instant()),
                WINDOW_CLOSED,
                "Booking window is closed",
            )
            val entryRef = db.document(FirestorePaths.entry(id, accountId))
            val entry = tx.require(entryRef, LotteryEntryDocument)
            val reservation =
                tx.read(
                    db.document(
                        FirestorePaths.reservation(DocumentKeys.reservation(id, accountId)),
                    ),
                    ReservationDocument,
                )
            requireOperation(reservation == null, ALREADY_USED, "Offer already used")
            if (entry.state == EntryState.DECLINED) return@atomic
            requireOperation(entry.state == EntryState.OFFERED, NOT_ALLOWED, "Entry did not win")
            tx.set(
                entryRef,
                LotteryEntryDocument.encode(
                    entry.copy(state = EntryState.DECLINED, stateChangedAt = clock.instant()),
                ),
            )
        }
    }
}

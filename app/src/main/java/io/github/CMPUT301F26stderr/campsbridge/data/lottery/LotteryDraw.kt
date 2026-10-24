package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.notification.DeliveryScope
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationWrites
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.Clock
import kotlin.random.Random

/**
 * Owns the seed/freeze barrier, idempotent outcomes and final draw completion.
 * @param db Firestore client for the draw's coordinated writes.
 * @param clock Deadline and outcome time source.
 * @param random Source for the proposed seed, persisted before assigning outcomes.
 */
internal class LotteryDraw(
    private val db: FirebaseFirestore,
    private val clock: Clock,
    private val random: Random,
) {
    /** Builds the lottery reference used by each draw transaction.
     * @param id Stable lottery identity.
     */
    private fun ref(id: String) =
        db.document(FirestorePaths.lottery(id))

    /**
     * Starts or resumes a deterministic seeded draw after registration closes. Outcomes and
     * receipt jobs commit per entry; draw completion gates booking and result delivery.
     * Returns whether already complete.
     *
     * @param id Lottery document ID.
     */
    suspend fun draw(id: String): DrawResult {
        val drawRef = db.collection("_draws").document(DocumentKeys.id(id))
        val proposedSeed = random.nextLong().toString()
        val seed =
            db.atomic { tx ->
                val lottery = tx.require(ref(id), LotteryDocument)
                val previous = tx.get(drawRef)
                if (lottery.drawnAt != null) return@atomic null
                requireOperation(
                    lottery.publishedAt != null && lottery.cancellation == null,
                    INACTIVE,
                    "Lottery is inactive",
                )
                requireOperation(
                    clock.instant() >= lottery.configuration.registrationClosesAt,
                    WINDOW_CLOSED,
                    "Registration has not closed",
                )
                if (previous.exists()) {
                    Fields(previous.reference.path, previous.data!!).string("seed")
                } else {
                    tx.set(drawRef, mapOf("seed" to proposedSeed))
                    // Conflict with in-flight membership changes; their retry rechecks the
                    // deadline.
                    tx.update(ref(id), "waitingCount", lottery.waitingCount)
                    proposedSeed
                }
            } ?: return DrawResult.ALREADY_DRAWN
        val lottery =
            ref(id).read(LotteryDocument) ?: throw PersistenceFailure(NOT_FOUND, "Lottery missing")
        // After the freeze barrier, membership is fixed. Include applied outcomes on resume.
        val candidates =
            ref(id).collection("entries").read(LotteryEntryDocument).filter {
                it.state != EntryState.WITHDRAWN
            }

        /**
         * Hashes the persisted draw seed and account ID to reproduce candidate order on
         * retries.
         *
         * @param account Account document ID.
         */
        fun rank(account: String): String =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest("$seed:$account".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        val winners =
            candidates
                .sortedWith(compareBy<LotteryEntry> { rank(it.accountId) }.thenBy { it.accountId })
                .take(
                    lottery.configuration.winnerCount
                        .coerceAtMost(candidates.size.toLong())
                        .toInt(),
                ).map { it.accountId }
                .toSet()
        for (candidate in candidates) {
            db.atomic { tx ->
                val current = tx.require(ref(id), LotteryDocument)
                if (current.drawnAt != null) return@atomic
                requireOperation(current.cancellation == null, INACTIVE, "Lottery cancelled")
                val entryRef = db.document(FirestorePaths.entry(id, candidate.accountId))
                val entry = tx.require(entryRef, LotteryEntryDocument)
                if (entry.state != EntryState.WAITING) return@atomic
                val selected = entry.accountId in winners
                val now = clock.instant()
                requireOperation(current.waitingCount > 0, INVALID_STATE, "Invalid waiting count")
                tx.set(
                    entryRef,
                    LotteryEntryDocument.encode(
                        entry.copy(
                            state = if (selected) EntryState.OFFERED else EntryState.LOST,
                            stateChangedAt = now,
                        ),
                    ),
                )
                tx.update(ref(id), "waitingCount", current.waitingCount - 1)
                val type =
                    if (selected) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST
                val eventId = DocumentKeys.compound(type.name, id, entry.accountId)
                NotificationWrites.enqueue(
                    db,
                    tx,
                    Notification(
                        eventId,
                        null,
                        type,
                        NotificationSubject(SubjectType.LOTTERY, id),
                        null,
                        now,
                    ),
                    listOf(entry.accountId),
                    scope = DeliveryScope.Lottery(id),
                )
            }
        }
        db.atomic { tx ->
            val current = tx.require(ref(id), LotteryDocument)
            if (current.drawnAt != null) return@atomic
            requireOperation(
                current.cancellation == null && current.waitingCount == 0L,
                INVALID_STATE,
                "Draw incomplete or cancelled",
            )
            tx.update(ref(id), "drawnAt", clock.instant().stored())
        }
        return DrawResult.COMPLETED
    }
}

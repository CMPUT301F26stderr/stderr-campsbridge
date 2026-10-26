package io.github.CMPUT301F26stderr.campsbridge.data.moderation

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.cancelReservationInTransaction
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.LocalDate

/**
 * Removal marks the target first; durable jobs resume the dependent cleanup.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param images Image repository used to remove attachments and queue blob cleanup.
 * @param lotteries Lottery implementation providing coordinated cancellation during removal.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreModerationRepository(
    private val db: FirebaseFirestore,
    private val images: ImageRepository,
    private val lotteries: FirestoreLotteryRepository,
    private val clock: Clock,
) : ModerationRepository {
    /**
     * Scrubs profile data while retaining identity/history, then resumes dependent removal
     * work. Self-removal is allowed; removing another account requires admin capability.
     *
     * @param accountId Account ID whose participation or data is addressed.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    override suspend fun removeAccount(
        accountId: String,
        actorId: String,
        reason: String?,
    ) {
        db.atomic { tx ->
            val account =
                tx.require(db.document(FirestorePaths.account(accountId)), AccountDocument)
            if (accountId != actorId) tx.activeAccount(db, actorId, Role.ADMIN)
            val removal = account.removal ?: Removal(clock.instant(), actorId, reason)
            tx.set(
                db.document(FirestorePaths.account(accountId)),
                AccountDocument.encode(
                    account.copy(
                        name = "",
                        email = null,
                        phone = null,
                        roles = emptySet(),
                        preferences = Preferences(),
                        settings = AccountSettings(notificationsEnabled = false),
                        removal = removal,
                    ),
                ),
            )
            enqueue(tx, ImageTarget(ImageTargetType.ACCOUNT, accountId), removal)
        }
        resumeRemovalsFor(actorId)
    }

    /**
     * Marks a listing removed for its owner/admin and resumes dependent cancellation and
     * cleanup.
     *
     * @param campsiteId Campsite document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    override suspend fun removeCampsite(
        campsiteId: String,
        actorId: String,
        reason: String?,
    ) {
        db.atomic { tx ->
            val actor = tx.activeAccount(db, actorId)
            val site =
                tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            requireOperation(
                actor.id == site.ownerId || Role.ADMIN in actor.roles,
                NOT_ALLOWED,
                "Not owner or admin",
            )
            markSite(tx, site, Removal(clock.instant(), actorId, reason))
        }
        resumeRemovalsFor(actorId)
    }

    /**
     * Records durable dependent cleanup within the caller’s removal transaction.
     *
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param target Account or campsite image slot.
     * @param removal Removal metadata, or null for an account/listing not marked removed.
     */
    private fun enqueue(
        tx: com.google.firebase.firestore.Transaction,
        target: ImageTarget,
        removal: Removal,
    ) {
        tx.set(
            db.collection("_removals").document(DocumentKeys.attachment(target)),
            removal.stored(),
        )
    }

    /**
     * Retains the first removal metadata and queues dependent campsite cleanup
     * transactionally.
     *
     * @param tx Caller-owned Firestore transaction; reads must precede writes.
     * @param site Campsite already read within the caller’s transaction.
     * @param requested Requested removal metadata; an existing first-removal record takes
     * precedence.
     */
    private fun markSite(
        tx: com.google.firebase.firestore.Transaction,
        site: Campsite,
        requested: Removal,
    ) {
        val removal = site.removal ?: requested
        tx.update(db.document(FirestorePaths.campsite(site.id)), "removal", removal.stored())
        enqueue(tx, ImageTarget(ImageTargetType.CAMPSITE, site.id), removal)
    }

    /**
     * Drains readable removal jobs, including dependent jobs created while processing.
     */
    override suspend fun resumeRemovals() =
        resumeRemovalsFor(null)

    /**
     * Drains pending removal work, optionally limited to the original actor; deletes jobs
     * only after cleanup.
     *
     * @param actorId Account ID responsible for the requested operation.
     */
    private suspend fun resumeRemovalsFor(actorId: String?) {
        // Account removal may enqueue campsite jobs, so drain until there are no jobs.
        while (true) {
            val pending = db.collection("_removals")
            val query = if (actorId == null) pending else pending.whereEqualTo("actorId", actorId)
            val jobs =
                query
                    .get(Source.SERVER)
                    .await()
                    .documents
            if (jobs.isEmpty()) return
            for (job in jobs) {
                val target = DocumentKeys.target(job.id)
                val removal = Fields(job.reference.path, job.data!!).removal()
                if (target.type == ImageTargetType.ACCOUNT) {
                    cleanupAccount(target.id, removal)
                } else {
                    cleanupSite(target.id, removal)
                }
                images.delete(target)
                job.reference.delete().await()
            }
        }
    }

    /**
     * Queues owned listings, cancels owned lotteries and uncompleted stays, withdraws
     * unfrozen entries, and removes device tokens while retaining completed history.
     *
     * @param id Account document ID.
     * @param removal Removal metadata, or null for an account/listing not marked removed.
     */
    private suspend fun cleanupAccount(
        id: String,
        removal: Removal,
    ) {
        for (
        site in
        db.collection("campsites").whereEqualTo("ownerId", id).read(CampsiteDocument)
        ) {
            db.atomic { tx ->
                val current =
                    tx.require(db.document(FirestorePaths.campsite(site.id)), CampsiteDocument)
                markSite(tx, current, removal)
            }
        }
        for (
        lottery in
        db
            .collection("lotteries")
            .whereEqualTo("ownerId", id)
            .read(LotteryDocument)
        ) {
            cancelLottery(lottery.id, removal)
        }
        for (
        entry in
        db
            .collectionGroup("entries")
            .whereEqualTo("accountId", id)
            .read(LotteryEntryDocument)
        ) {
            db.atomic { tx ->
                val lotteryRef = db.document(FirestorePaths.lottery(entry.lotteryId))
                val lottery = tx.require(lotteryRef, LotteryDocument)
                val entryRef = db.document(FirestorePaths.entry(entry.lotteryId, id))
                val current = tx.require(entryRef, LotteryEntryDocument)
                // Do not change frozen draw membership; the removed account cannot book.
                if (
                    current.state == EntryState.WAITING &&
                    clock.instant() < lottery.configuration.registrationClosesAt
                ) {
                    requireOperation(
                        lottery.waitingCount > 0,
                        INVALID_STATE,
                        "Invalid waiting count",
                    )
                    tx.set(
                        entryRef,
                        LotteryEntryDocument.encode(
                            current.copy(
                                state = EntryState.WITHDRAWN,
                                stateChangedAt = clock.instant(),
                            ),
                        ),
                    )
                    tx.update(lotteryRef, "waitingCount", lottery.waitingCount - 1)
                }
            }
        }
        for (
        reservation in
        db
            .collection("reservations")
            .whereEqualTo("entrantId", id)
            .read(ReservationDocument)
        ) {
            cancelFuture(reservation.id, removal)
        }
        val devices =
            db
                .collection("${FirestorePaths.account(id)}/devices")
                .get(Source.SERVER)
                .await()
                .documents
        for (group in devices.chunked(200)) {
            val batch = db.batch()
            group.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /**
     * Cancels active owned lotteries that include the site and refunds uncompleted
     * reservations.
     *
     * @param id Campsite document ID.
     * @param removal Removal metadata, or null for an account/listing not marked removed.
     */
    private suspend fun cleanupSite(
        id: String,
        removal: Removal,
    ) {
        // Ownership-scoped reads also work for self-removal; a global offer scan requires admin.
        val site = db.document(FirestorePaths.campsite(id)).read(CampsiteDocument) ?: return
        val ownedLotteries =
            db.collection("lotteries").whereEqualTo("ownerId", site.ownerId).read(LotteryDocument)
        for (lottery in ownedLotteries) {
            val offer = db.document(FirestorePaths.offer(lottery.id, id)).get(Source.SERVER).await()
            if (offer.exists()) cancelLottery(lottery.id, removal)
        }
        for (
        reservation in
        db
            .collection("reservations")
            .whereEqualTo("campsiteId", id)
            .read(ReservationDocument)
        ) {
            cancelFuture(reservation.id, removal)
        }
    }

    /**
     * Cancels a draft or not-yet-ended lottery through the already-authorized removal path.
     *
     * @param id Lottery document ID.
     * @param removal Removal metadata, or null for an account/listing not marked removed.
     */
    private suspend fun cancelLottery(
        id: String,
        removal: Removal,
    ) {
        val lottery = db.document(FirestorePaths.lottery(id)).read(LotteryDocument) ?: return
        if (
            lottery.cancellation == null &&
            (
                lottery.publishedAt == null ||
                    lottery.configuration.bookingClosesAt > clock.instant()
            )
        ) {
            lotteries.cancelForRemoval(id, removal.actorId, removal.reason)
        }
    }

    /**
     * Cancels/refunds an uncompleted stay in its recorded timezone while preserving
     * completed stays.
     *
     * @param id Reservation document ID.
     * @param removal Removal metadata, or null for an account/listing not marked removed.
     */
    private suspend fun cancelFuture(
        id: String,
        removal: Removal,
    ) {
        db.atomic { tx ->
            val reservation =
                tx.require(db.document(FirestorePaths.reservation(id)), ReservationDocument)
            val today =
                LocalDate.ofInstant(
                    clock.instant(),
                    java.time.ZoneId.of(reservation.bookedCampsite.timeZone),
                )
            if (reservation.checkOutDate > today && reservation.cancellation == null) {
                val site =
                    tx.require(
                        db.document(FirestorePaths.campsite(reservation.campsiteId)),
                        CampsiteDocument,
                    )
                val role =
                    when (removal.actorId) {
                        reservation.entrantId -> Role.ENTRANT
                        site.ownerId -> Role.OWNER
                        else -> Role.ADMIN
                    }
                cancelReservationInTransaction(
                    db,
                    tx,
                    reservation,
                    CancellationRequest(id, removal.actorId, role, removal.reason),
                    clock.instant(),
                )
            }
        }
    }
}

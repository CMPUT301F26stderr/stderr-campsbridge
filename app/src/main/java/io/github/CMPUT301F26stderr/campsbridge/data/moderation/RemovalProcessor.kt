package io.github.CMPUT301F26stderr.campsbridge.data.moderation

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.cancelReservationInTransaction
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.LocalDate

/** Processes recorded removal jobs; request authorization belongs to the repository.
 * @param db Authenticated client; Firestore rules authorize every resulting request.
 * @param images Attachment cleanup owner.
 * @param lotteries Existing lottery cancellation owner.
 * @param clock Shared time used to preserve completed history.
 */
internal class RemovalProcessor(
    private val db: FirebaseFirestore,
    private val images: ImageRepository,
    private val lotteries: FirestoreLotteryRepository,
    private val clock: Clock,
) {
    /**
     * Drains pending removal work, optionally limited to the original actor; deletes jobs
     * only after cleanup.
     *
     * @param actorId Account ID responsible for the requested operation.
     */
    suspend fun resumeFor(actorId: String?) {
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
     * @param removal Recorded removal attribution retained throughout cleanup.
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
                RemovalWrites.markSite(db, tx, current, removal)
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
     * @param removal Recorded removal attribution retained throughout cleanup.
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
     * @param removal Recorded removal attribution retained throughout cleanup.
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
     * @param removal Recorded removal attribution retained throughout cleanup.
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

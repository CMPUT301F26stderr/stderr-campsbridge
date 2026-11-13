/*
 * Owns draft, configuration, offer revision and publication transactions.
 */

package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Clock

/**
 * Owns draft, configuration, offer revision and publication transactions.
 * @param db Firestore client for this operation family.
 * @param clock Shared time source for validation and recorded facts.
 */
internal class LotteryWrites(
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

    /** Reads the required coordination counter; missing fields are malformed data.
     * @param snapshot Current lottery snapshot from the server or transaction.
     */
    private fun requiredRevision(snapshot: DocumentSnapshot): Long =
        Fields(
            snapshot.reference.path,
            snapshot.data
                ?: throw DocumentDecodingException(snapshot.reference.path, "Missing lottery"),
        ).long("_revision")

    /** Captures the revision before querying offers so a transaction can reject stale membership.
     * @param id Lottery whose complete offer set is needed.
     */
    private suspend fun allocationSnapshot(id: String): Pair<Long, List<LotteryOffer>> {
        val snapshot = ref(id).get(Source.SERVER).await()
        requireOperation(snapshot.exists(), NOT_FOUND, "Lottery missing")
        val revision = requiredRevision(snapshot)
        return revision to ref(id).collection("offers").read(LotteryOfferDocument)
    }

    /** Reports stale guarded writes as conflicts even when rules reject the old revision first.
     * @param id Parent whose revision coordinates the queried allocation set.
     * @param revision Revision captured before that query.
     * @param body Operation reads and writes, retried only by Firestore's transaction runner.
     */
    private suspend fun allocationTransaction(
        id: String,
        revision: Long,
        body: (Transaction) -> Unit,
    ) {
        try {
            db.atomic(body)
        } catch (failure: FirebaseFirestoreException) {
            if (failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                val current =
                    try {
                        ref(id).get(Source.SERVER).await()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        throw failure
                    }
                if (current.exists() && requiredRevision(current) != revision) {
                    throw PersistenceFailure(CONFLICT, "Allocations changed; retry the operation")
                }
            }
            throw failure
        }
    }

    /** Changes organizer-managed membership against the same guarded offer set used for publication.
     * @param id Editable lottery.
     * @param ownerId Requesting organizer.
     * @param participantOwnerIds Complete desired membership, including the organizer.
     */
    suspend fun updateParticipants(
        id: String,
        ownerId: String,
        participantOwnerIds: Set<String>,
    ) {
        val (revision, offers) = allocationSnapshot(id)
        allocationTransaction(id, revision) { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            requireOperation(lottery.ownerId == ownerId, NOT_ALLOWED, "Only the organizer can change participants")
            editable(lottery, ownerId)
            requireOperation(requiredRevision(snapshot) == revision, CONFLICT, "Allocations changed; retry membership")
            requireOperation(ownerId in participantOwnerIds, INVALID_STATE, "The organizer must remain a participant")
            for (added in participantOwnerIds - lottery.participantOwnerIds) tx.activeAccount(db, added, Role.OWNER)
            val sites =
                offers.map { offer ->
                    tx.require(db.document(FirestorePaths.campsite(offer.campsiteId)), CampsiteDocument)
                }
            requireOperation(
                sites.all { it.ownerId in participantOwnerIds },
                INVALID_STATE,
                "Remove the owner's offers first",
            )
            if (participantOwnerIds.size > 1 && lottery.participantOwnerIds.size == 1) {
                requireOperation(sites.all { it.isPublished }, INACTIVE, "Publish allocated campsites before sharing")
            }
            tx.update(
                ref(id),
                mapOf("participantOwnerIds" to participantOwnerIds.sorted(), "_revision" to revision + 1),
            )
        }
    }

    /** Reconciles one owner's allocation set; unchanged retries leave the revision intact.
     * @param id Editable shared lottery.
     * @param ownerId Acting participant whose allocations are replaced.
     * @param offers Desired owned allocations; empty clears only that owner's offers.
     */
    suspend fun replaceOwnerOffers(
        id: String,
        ownerId: String,
        offers: List<LotteryOffer>,
    ) {
        require(offers.map { it.campsiteId }.distinct().size == offers.size)
        val (revision, previous) = allocationSnapshot(id)
        allocationTransaction(id, revision) { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            editable(lottery, ownerId)
            requireOperation(requiredRevision(snapshot) == revision, CONFLICT, "Allocations changed; retry replacement")
            val sites =
                (previous + offers).map { it.campsiteId }.toSet().associateWith { campsiteId ->
                    tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
                }
            for (offer in offers) validateOffer(tx, lottery, ownerId, offer, sites.getValue(offer.campsiteId))
            val owned = previous.filter { sites.getValue(it.campsiteId).ownerId == ownerId }
            if (owned.toSet() == offers.toSet()) return@allocationTransaction
            val desired = offers.map { it.campsiteId }.toSet()
            val removed = owned.filter { it.campsiteId !in desired }
            // All validation reads precede the complete replacement's writes.
            for (offer in offers) {
                tx.set(
                    db.document(FirestorePaths.offer(id, offer.campsiteId)),
                    LotteryOfferDocument.encode(offer),
                )
            }
            for (offer in removed) tx.delete(db.document(FirestorePaths.offer(id, offer.campsiteId)))
            tx.update(ref(id), "_revision", revision + 1)
        }
    }

    /** Validates ownership, shared visibility and calendar facts before an allocation write.
     * @param tx Caller transaction; this helper performs reads only.
     * @param lottery Current parent determining participation and shared visibility.
     * @param ownerId Acting owner.
     * @param offer Requested campsite and nights.
     * @param site Authoritative campsite already read in the transaction.
     */
    private fun validateOffer(
        tx: Transaction,
        lottery: Lottery,
        ownerId: String,
        offer: LotteryOffer,
        site: Campsite,
    ) {
        requireOperation(site.ownerId == ownerId, NOT_ALLOWED, "Campsite is not owned by this participant")
        requireOperation(
            site.removal == null && (lottery.participantOwnerIds.size == 1 || site.isPublished),
            INACTIVE,
            "Campsite is unavailable for allocation",
        )
        val nights = offer.nights.map { tx.read(db.document(FirestorePaths.night(site.id, it)), CalendarNightDocument) }
        requireOperation(nights.all { it?.isOffered == true }, INVALID_STATE, "Calendar nights must be offered first")
    }

    /**
     * Creates one draft transactionally; retries preserve stored configuration and lifecycle.
     * Reads confirmed server values after commit; a read failure leaves creation safe to retry.
     * @param id Stable caller-allocated draft identity.
     * @param ownerId Active owner creating the draft.
     * @param title Initial display title.
     * @param description Initial description.
     * @param configuration Initial registration/booking windows and limits.
     */
    suspend fun createDraft(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ): Lottery {
        val reference = db.document(FirestorePaths.lottery(id))
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val existing = tx.read(reference, LotteryDocument)
            if (existing != null) {
                requireOperation(
                    existing.ownerId == ownerId,
                    CONFLICT,
                    "Draft ID belongs to another owner",
                )
                existing
            } else {
                val draft =
                    Lottery(id, ownerId, title, description, configuration, 0, clock.instant())
                tx.set(reference, LotteryDocument.encode(draft) + ("_revision" to 0L))
                draft
            }
        }
        return reference.read(LotteryDocument)
            ?: throw PersistenceFailure(PersistenceFailure.Reason.NOT_FOUND, reference.path)
    }

    /**
     * Updates descriptions and permitted configuration; published configuration freezes at
     * registration start.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param title Display title for the lottery or announcement.
     * @param description Listing or lottery description.
     * @param configuration Registration/booking windows and participation limits.
     */
    suspend fun update(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            requireOperation(
                lottery.ownerId == ownerId && lottery.cancellation == null,
                NOT_ALLOWED,
                "Lottery is not editable",
            )
            requireOperation(
                lottery.configuration == configuration ||
                    lottery.publishedAt == null ||
                    clock.instant() < lottery.configuration.registrationOpensAt,
                WINDOW_CLOSED,
                "Lottery configuration is frozen",
            )
            tx.update(
                ref(id),
                mapOf(
                    "title" to title,
                    "description" to description,
                    "registrationOpensAt" to configuration.registrationOpensAt.stored(),
                    "registrationClosesAt" to configuration.registrationClosesAt.stored(),
                    "bookingOpensAt" to configuration.bookingOpensAt.stored(),
                    "bookingClosesAt" to configuration.bookingClosesAt.stored(),
                    "winnerCount" to configuration.winnerCount,
                    "waitlistCapacity" to configuration.waitlistCapacity,
                    "_revision" to (requiredRevision(snapshot) + 1),
                ),
            )
        }
    }

    /**
     * Rejects a foreign, cancelled, or frozen lottery before changing its offered inventory.
     *
     * @param lottery Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    private fun editable(
        lottery: Lottery,
        ownerId: String,
    ) {
        requireOperation(
            ownerId in lottery.participantOwnerIds && lottery.cancellation == null,
            NOT_ALLOWED,
            "Lottery is not editable",
        )
        requireOperation(
            (
                lottery.publishedAt == null ||
                    clock.instant() < lottery.configuration.registrationOpensAt
            ) &&
                lottery.drawnAt == null,
            WINDOW_CLOSED,
            "Offers are frozen",
        )
    }

    /**
     * Validates owned offered nights and replaces an allocation while advancing the revision
     * guard.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param offer Campsite/night allocation to store.
     */
    suspend fun setOffer(
        id: String,
        ownerId: String,
        offer: LotteryOffer,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            editable(lottery, ownerId)
            val site =
                tx.require(db.document(FirestorePaths.campsite(offer.campsiteId)), CampsiteDocument)
            validateOffer(tx, lottery, ownerId, offer, site)
            tx.set(
                db.document(FirestorePaths.offer(id, site.id)),
                LotteryOfferDocument.encode(offer),
            )
            tx.update(ref(id), "_revision", requiredRevision(snapshot) + 1)
        }
    }

    /**
     * Removes an editable campsite allocation and advances the revision guard.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param campsiteId Campsite document ID.
     */
    suspend fun removeOffer(
        id: String,
        ownerId: String,
        campsiteId: String,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            editable(LotteryDocument.decode(snapshot), ownerId)
            val site = tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            requireOperation(site.ownerId == ownerId, NOT_ALLOWED, "Only the campsite owner can remove its allocation")
            tx.delete(db.document(FirestorePaths.offer(id, campsiteId)))
            tx.update(ref(id), "_revision", requiredRevision(snapshot) + 1)
        }
    }

    /**
     * Validates published campsites and offered nights before publication; a revision guard
     * rejects concurrent allocation edits and registration must still be in the future.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    suspend fun publish(
        id: String,
        ownerId: String,
    ) {
        // Query membership outside the transaction, then guard it with the parent revision.
        val (revision, offers) = allocationSnapshot(id)
        requireOperation(offers.isNotEmpty(), INVALID_STATE, "Lottery needs an offer")
        allocationTransaction(id, revision) { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val current = tx.get(ref(id))
            val lottery = LotteryDocument.decode(current)
            requireOperation(lottery.ownerId == ownerId, NOT_ALLOWED, "Only the organizer can publish")
            requireOperation(
                requiredRevision(current) == revision,
                CONFLICT,
                "Offers changed; retry publication",
            )
            editable(lottery, ownerId)
            requireOperation(
                clock.instant() < lottery.configuration.registrationOpensAt,
                WINDOW_CLOSED,
                "Move registration into the future before publishing",
            )
            for (offer in offers) {
                val site =
                    tx.require(
                        db.document(FirestorePaths.campsite(offer.campsiteId)),
                        CampsiteDocument,
                    )
                requireOperation(
                    site.isPublished && site.ownerId in lottery.participantOwnerIds,
                    INACTIVE,
                    "Offered campsite must be published",
                )
                tx.activeAccount(db, site.ownerId, Role.OWNER)
                for (date in offer.nights) {
                    requireOperation(
                        tx
                            .read(
                                db.document(FirestorePaths.night(site.id, date)),
                                CalendarNightDocument,
                            )?.isOffered == true,
                        INVALID_STATE,
                        "Offered date withdrawn",
                    )
                }
            }
            tx.update(
                ref(id),
                mapOf(
                    "publishedAt" to (lottery.publishedAt ?: clock.instant()).stored(),
                    "_revision" to (revision + 1),
                ),
            )
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
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
            val changed =
                lottery.copy(
                    title = title,
                    description = description,
                    configuration = configuration,
                )
            tx.set(
                ref(id),
                LotteryDocument.encode(changed) +
                    ("_revision" to ((snapshot.getLong("_revision") ?: 0) + 1)),
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
            lottery.ownerId == ownerId && lottery.cancellation == null,
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
            requireOperation(
                site.ownerId == ownerId && site.removal == null,
                NOT_ALLOWED,
                "Campsite is not owned by lottery owner",
            )
            val nights =
                offer.nights.map {
                    tx.read(db.document(FirestorePaths.night(site.id, it)), CalendarNightDocument)
                }
            requireOperation(
                nights.all { it?.isOffered == true },
                INVALID_STATE,
                "Calendar nights must be offered first",
            )
            tx.set(
                db.document(FirestorePaths.offer(id, site.id)),
                LotteryOfferDocument.encode(offer),
            )
            tx.update(ref(id), "_revision", (snapshot.getLong("_revision") ?: 0) + 1)
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
            tx.delete(db.document(FirestorePaths.offer(id, campsiteId)))
            tx.update(ref(id), "_revision", (snapshot.getLong("_revision") ?: 0) + 1)
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
        val snapshot = ref(id).get(Source.SERVER).await()
        requireOperation(snapshot.exists(), NOT_FOUND, "Lottery missing")
        val revision = snapshot.getLong("_revision") ?: 0
        val offers = ref(id).collection("offers").read(LotteryOfferDocument)
        requireOperation(offers.isNotEmpty(), INVALID_STATE, "Lottery needs an offer")
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val current = tx.get(ref(id))
            val lottery = LotteryDocument.decode(current)
            requireOperation(
                (current.getLong("_revision") ?: 0) == revision,
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
                    site.isPublished && site.ownerId == ownerId,
                    INACTIVE,
                    "Offered campsite must be published",
                )
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
            tx.update(ref(id), "publishedAt", (lottery.publishedAt ?: clock.instant()).stored())
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.data.campsite

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * Firebase-backed implementation for listing publication and owner-managed per-night inventory.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 */
class FirestoreCampsiteRepository(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) : CampsiteRepository {
    /**
     * Returns the stored campsite, or null when its document is absent.
     *
     * @param id Campsite document ID.
     */
    override suspend fun get(id: String) =
        db.document(FirestorePaths.campsite(id)).read(CampsiteDocument)

    /**
     * Observes one campsite, emitting null if its document is absent.
     *
     * @param id Campsite document ID.
     */
    override fun observe(id: String) =
        db.document(FirestorePaths.campsite(id)).observe(CampsiteDocument)

    /**
     * Observes published, nonremoved campsites suitable for discovery.
     */
    override fun observePublished() =
        db.collection("campsites").observe(CampsiteDocument).map { sites ->
            sites.filter { it.isPublished }
        }

    /**
     * Observes listings belonging to the owner, including drafts and retained records.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override fun observeForOwner(ownerId: String) =
        db.collection("campsites").whereEqualTo("ownerId", ownerId).observe(CampsiteDocument)

    /**
     * Observes all readable campsite records, including drafts and removal history.
     */
    override fun observeAll() =
        db.collection("campsites").observe(CampsiteDocument)

    /**
     * Creates an unpublished listing for an active owner and returns its generated ID.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param details Editable campsite facts to store.
     */
    override suspend fun create(
        ownerId: String,
        details: CampsiteDetails,
    ): String {
        val reference = db.collection("campsites").document()
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            tx.set(
                reference,
                CampsiteDocument.encode(Campsite(reference.id, ownerId, details, clock.instant())),
            )
        }
        return reference.id
    }

    /**
     * Authorizes the active owner and transforms a nonremoved listing transactionally.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param transform Pure record transformation; may run again if the transaction retries.
     */
    private suspend fun change(
        id: String,
        ownerId: String,
        transform: (Campsite) -> Campsite,
    ) {
        val ref = db.document(FirestorePaths.campsite(id))
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val site = tx.require(ref, CampsiteDocument)
            requireOperation(site.ownerId == ownerId, NOT_ALLOWED, "Different campsite owner")
            requireOperation(site.removal == null, INACTIVE, "Campsite removed")
            tx.set(ref, CampsiteDocument.encode(transform(site)))
        }
    }

    /**
     * Replaces editable listing details without changing ownership or publication history.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param details Editable campsite facts to store.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ) =
        change(id, ownerId) { it.copy(details = details) }

    /**
     * Sets the first publication time; an already published listing keeps its original time.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) =
        change(id, ownerId) { it.copy(publishedAt = it.publishedAt ?: clock.instant()) }

    /**
     * Observes stored nights in the half-open date range; missing nights are not offered.
     *
     * @param id Campsite document ID.
     * @param start Inclusive first local date in the range.
     * @param endExclusive Exclusive end of the observed calendar range.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ) =
        db
            .collection("${FirestorePaths.campsite(id)}/calendar")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), start.toString())
            .whereLessThan(FieldPath.documentId(), endExclusive.toString())
            .observe(CalendarNightDocument)

    /**
     * Changes each night in its own transaction; earlier nights may remain saved after
     * failure. Withdrawing an occupied night requires reservation cancellation first.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param dates Local calendar nights to update.
     * @param offered Whether to offer or withdraw the requested nights.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ) {
        // Each night is independent; bounded transactions allow arbitrarily long calendar edits.
        for (date in dates.sorted()) {
            db.atomic { tx ->
                tx.activeAccount(db, ownerId, Role.OWNER)
                val site = tx.require(db.document(FirestorePaths.campsite(id)), CampsiteDocument)
                requireOperation(
                    site.ownerId == ownerId && site.removal == null,
                    NOT_ALLOWED,
                    "Campsite is not editable",
                )
                val ref = db.document(FirestorePaths.night(id, date))
                val night = tx.read(ref, CalendarNightDocument) ?: CalendarNight(date, false)
                requireOperation(
                    offered || night.reservationId == null,
                    CONFLICT,
                    "Cancel the reservation before withdrawing a night",
                )
                tx.set(ref, CalendarNightDocument.encode(night.copy(isOffered = offered)))
            }
        }
    }
}

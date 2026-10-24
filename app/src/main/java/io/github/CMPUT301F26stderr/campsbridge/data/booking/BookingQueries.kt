package io.github.CMPUT301F26stderr.campsbridge.data.booking

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observeRelated
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Owns reservation read queries and the join through immutable campsite ownership.
 * @param db Firestore client used for scoped queries and document listeners.
 */
internal class BookingQueries(
    private val db: FirebaseFirestore,
) {
    /**
     * Returns the complete stored reservation, or null when absent.
     * @param id Reservation document identity.
     */
    suspend fun get(id: String) =
        db.document(FirestorePaths.reservation(id)).read(ReservationDocument)

    /**
     * Observes the reservation and its nested payment as one record.
     * @param id Reservation document identity.
     */
    fun observe(id: String) =
        db.document(FirestorePaths.reservation(id)).observe(ReservationDocument)

    /**
     * Observes an entrant's retained stays, newest first with an ID tie-break.
     * @param entrantId Entrant whose stays are requested.
     */
    fun forEntrant(entrantId: String) =
        db
            .collection("reservations")
            .whereEqualTo("entrantId", entrantId)
            .observe(ReservationDocument)
            .ordered()

    /**
     * Observes one campsite's retained stays in stable chronological order.
     * @param campsiteId Campsite whose stays are requested.
     */
    fun forCampsite(campsiteId: String) =
        db
            .collection("reservations")
            .whereEqualTo("campsiteId", campsiteId)
            .observe(ReservationDocument)
            .ordered()

    /**
     * Observes owned campsites including removed listings and owns their reservation subscriptions.
     * @param ownerId Account whose immutable campsite ownership determines membership.
     */
    fun forOwner(ownerId: String): Flow<List<Reservation>> {
        val campsiteIds =
            db
                .collection("campsites")
                .whereEqualTo("ownerId", ownerId)
                .observe(CampsiteDocument)
                .map { sites -> sites.map { it.id } }
        return observeRelated(campsiteIds, ::forCampsite).ordered()
    }
}

/** Applies stable newest-first ordering to complete records without hiding failures. */
private fun Flow<List<Reservation>>.ordered(): Flow<List<Reservation>> =
    map { records ->
        records
            .distinctBy {
                it.id
            }.sortedWith(compareByDescending<Reservation> { it.createdAt }.thenBy { it.id })
    }.distinctUntilChanged()

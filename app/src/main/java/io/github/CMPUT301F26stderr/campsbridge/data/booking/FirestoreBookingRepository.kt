package io.github.CMPUT301F26stderr.campsbridge.data.booking

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import java.time.Clock

/**
 * Reads complete reservations and delegates coordinated mutations to their transaction owner.
 * @param db Firestore client used for reservation queries and listeners.
 * @param clock Injectable operation time supplied to coordinated writes.
 */
class FirestoreBookingRepository(
    db: FirebaseFirestore,
    clock: Clock,
) : BookingRepository {
    private val writes = BookingWrites(db, clock)
    private val queries = BookingQueries(db)

    /**
     * Returns a complete reservation, including payment/refund, or null when absent.
     * @param id Reservation document ID.
     */
    override suspend fun get(id: String) =
        queries.get(id)

    /** Confirms the complete saved stay or its absence at the server; failures propagate.
     * @param lotteryId Saved lottery identity.
     * @param entrantId Opportunity owner.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? =
        queries.forOpportunity(lotteryId, entrantId)

    /**
     * Observes reservation and payment state through one document listener.
     * @param id Reservation document ID.
     */
    override fun observe(id: String) =
        queries.observe(id)

    /**
     * Observes the entrant's reservations, including past and cancelled stays.
     * @param entrantId Entrant whose stays are requested.
     */
    override fun observeForEntrant(entrantId: String) =
        queries.forEntrant(entrantId)

    /**
     * Observes all retained reservations at one campsite.
     * @param campsiteId Campsite whose stays are requested.
     */
    override fun observeForCampsite(campsiteId: String) =
        queries.forCampsite(campsiteId)

    /**
     * Atomically consumes a win, charges the simulated payment and claims every night.
     * @param request Selected stay and accepted quote/terms to revalidate.
     */
    override suspend fun reserve(request: BookingRequest): String =
        writes.reserve(request)

    /**
     * Authorizes and atomically refunds/releases a stay; retries preserve the first cancellation.
     * @param request Actor, role and cancellation reason.
     */
    override suspend fun cancel(request: CancellationRequest) =
        writes.cancel(request)

    /**
     * Observes all retained stays through the current owner's campsites, including removed sites.
     * @param ownerId Account whose campsite ownership determines the result.
     */
    override fun observeForOwner(ownerId: String) =
        queries.forOwner(ownerId)
}

package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for reservation creation/cancellation with coordinated inventory and
 * simulated payments.
 */
interface BookingRepository {
    /**
     * Returns a complete reservation with payment and cancellation history, or null when absent.
     *
     * @param id Reservation document ID.
     */
    suspend fun get(id: String): Reservation?

    /**
     * Observes a complete reservation including its payment, or null when absent.
     * @param id Reservation document identity.
     */
    fun observe(id: String): Flow<Reservation?>

    /**
     * Observes the entrant’s reservations, including past and cancelled stays.
     *
     * @param entrantId Account ID of the entrant using the winning opportunity.
     */
    fun observeForEntrant(entrantId: String): Flow<List<Reservation>>

    /**
     * Observes reservations for one campsite, including historical stays.
     *
     * @param campsiteId Campsite document ID.
     */
    fun observeForCampsite(campsiteId: String): Flow<List<Reservation>>

    /**
     * Validates an unused win, current quote/terms, and every night before atomically
     * creating the reservation/payment, claiming inventory, and queueing confirmation.
     * Returns the reservation ID.
     *
     * @param request Stay selection and expected price/terms to validate.
     */
    suspend fun reserve(request: BookingRequest): String

    /**
     * Authorizes the requested actor role and atomically cancels/refunds the stay, releases
     * inventory, and queues notification. Already cancelled stays are unchanged; the win
     * stays consumed.
     *
     * @param request Actor, role, and reason for cancellation.
     */
    suspend fun cancel(request: CancellationRequest)

    /**
     * Observes retained reservations across all campsites owned by the account.
     * @param ownerId Account whose campsite ownership determines the result.
     */
    fun observeForOwner(ownerId: String): Flow<List<Reservation>>
}

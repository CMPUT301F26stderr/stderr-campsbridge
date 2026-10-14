package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for reservation creation/cancellation with coordinated inventory and
 * simulated payments.
 */
interface BookingRepository {
    /**
     * Returns a reservation, including cancellation history, or null when absent.
     *
     * @param id Reservation document ID.
     */
    suspend fun get(id: String): Reservation?

    /**
     * Returns the reservation’s simulated payment/refund record, or null when absent.
     *
     * @param id Reservation document ID.
     */
    suspend fun getPayment(id: String): Payment?

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
}

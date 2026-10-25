package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Empty offline reader; simulated payment/inventory transactions require the Firebase host. */
class DemoBookingRepository : BookingRepository {
    /** Returns no saved offline reservation.
     * @param id Requested identity.
     */
    override suspend fun get(id: String): Reservation? =
        null

    /** Returns absence from the intentionally empty offline dataset, without a Firebase call.
     * @param lotteryId Requested lottery.
     * @param entrantId Requested entrant.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? =
        null

    /** Emits confirmed absence in the empty local demo.
     * @param id Requested identity.
     */
    override fun observe(id: String): Flow<Reservation?> =
        flowOf(null)

    /** Emits the demo's empty entrant history.
     * @param entrantId Requested account.
     */
    override fun observeForEntrant(entrantId: String): Flow<List<Reservation>> =
        flowOf(emptyList())

    /** Emits the demo's empty campsite history.
     * @param campsiteId Requested campsite.
     */
    override fun observeForCampsite(campsiteId: String): Flow<List<Reservation>> =
        flowOf(emptyList())

    /** Emits the demo's empty owner history.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Reservation>> =
        flowOf(emptyList())

    /** Rejects distributed booking rather than simulating a confirmed transaction.
     * @param request Requested stay and accepted quote.
     */
    override suspend fun reserve(request: BookingRequest): String =
        unavailable()

    /** Rejects distributed cancellation/refund.
     * @param request Requested cancellation.
     */
    override suspend fun cancel(request: CancellationRequest): Unit =
        unavailable()

    /** Keeps local demo limitations explicit at the operation boundary. */
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("Booking writes require the Firebase host")
}

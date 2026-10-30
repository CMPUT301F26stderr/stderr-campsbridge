package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Saved offline booking snapshots; coordinated transactions require the Firebase host.
 * @param records Supplied historical aggregates.
 * @param owners Authoritative fixture campsite-to-owner relationships.
 */
class DemoBookingRepository(
    private val records: List<Reservation> = emptyList(),
    private val owners: Map<String, String> = emptyMap(),
) : BookingRepository {
    /** Returns the matching supplied reservation, or null.
     * @param id Requested identity.
     */
    override suspend fun get(id: String): Reservation? =
        records.firstOrNull { it.id == id }

    /** Returns a supplied consuming reservation without a Firebase call.
     * @param lotteryId Requested lottery.
     * @param entrantId Requested entrant.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? =
        records.firstOrNull { it.lotteryId == lotteryId && it.entrantId == entrantId }

    /** Emits the supplied aggregate, or confirmed fixture absence.
     * @param id Requested identity.
     */
    override fun observe(id: String): Flow<Reservation?> =
        flowOf(records.firstOrNull { it.id == id })

    /** Emits only supplied reservations belonging to the requested entrant.
     * @param entrantId Requested account.
     */
    override fun observeForEntrant(entrantId: String): Flow<List<Reservation>> =
        flowOf(records.filter { it.entrantId == entrantId })

    /** Emits only supplied reservations associated with the requested campsite.
     * @param campsiteId Requested campsite.
     */
    override fun observeForCampsite(campsiteId: String): Flow<List<Reservation>> =
        flowOf(records.filter { it.campsiteId == campsiteId })

    /** Emits reservations associated with the requested fixture owner.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Reservation>> =
        flowOf(records.filter { owners[it.campsiteId] == ownerId })

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

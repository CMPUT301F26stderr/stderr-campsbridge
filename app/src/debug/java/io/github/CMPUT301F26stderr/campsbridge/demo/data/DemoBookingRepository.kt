package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** In-memory bookings for the offline demo; reset restores the supplied records.
 * @param initial Supplied historical aggregates.
 * @param campsites Fixture campsites that supply owners and booking snapshots.
 * @param clock Fixture time recorded on new bookings.
 */
class DemoBookingRepository(
    initial: List<Reservation> = emptyList(),
    private val campsites: List<Campsite> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
) : BookingRepository {
    private val records = MutableStateFlow(initial)

    /** Returns the matching reservation, or null.
     * @param id Requested identity.
     */
    override suspend fun get(id: String): Reservation? =
        records.value.firstOrNull { it.id == id }

    /** Returns a consuming reservation without a Firebase call.
     * @param lotteryId Requested lottery.
     * @param entrantId Requested entrant.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? =
        records.value.firstOrNull { it.lotteryId == lotteryId && it.entrantId == entrantId }

    /** Emits the current aggregate, or confirmed fixture absence.
     * @param id Requested identity.
     */
    override fun observe(id: String): Flow<Reservation?> =
        records.map { stays -> stays.firstOrNull { it.id == id } }

    /** Emits only reservations belonging to the requested entrant.
     * @param entrantId Requested account.
     */
    override fun observeForEntrant(entrantId: String): Flow<List<Reservation>> =
        records.map { stays -> stays.filter { it.entrantId == entrantId } }

    /** Emits only reservations associated with the requested campsite.
     * @param campsiteId Requested campsite.
     */
    override fun observeForCampsite(campsiteId: String): Flow<List<Reservation>> =
        records.map { stays -> stays.filter { it.campsiteId == campsiteId } }

    /** Emits reservations associated with the requested fixture owner.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Reservation>> {
        val owned = campsites.filter { it.ownerId == ownerId }.map { it.id }.toSet()
        return records.map { stays -> stays.filter { it.campsiteId in owned } }
    }

    /** Saves the stay in memory so it shows up in Reservations; calendar nights stay unclaimed.
     * @param request Requested stay and accepted quote.
     * @return New reservation identity.
     * @throws UnsupportedOperationException When this repository has no fixture for the campsite.
     */
    override suspend fun reserve(request: BookingRequest): String {
        val details = campsites.firstOrNull { it.id == request.campsiteId }?.details ?: unavailable()
        val now = clock.instant()
        val id = "demo-booking-${records.value.size}"
        records.value +=
            Reservation(
                id,
                request.lotteryId,
                request.entrantId,
                request.campsiteId,
                request.checkInDate,
                request.checkOutDate,
                request.guestCount,
                BookedCampsite(details.name, details.locationLabel, details.location, details.timeZone),
                AcceptedTerms(request.acceptedRules, request.policyVersion, now),
                now,
                Payment(request.expectedTotalCents, now),
            )
        return id
    }

    /** Rejects distributed cancellation/refund.
     * @param request Requested cancellation.
     */
    override suspend fun cancel(request: CancellationRequest): Unit =
        unavailable()

    /** Keeps local demo limitations explicit at the operation boundary. */
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("Booking writes require the Firebase host")
}

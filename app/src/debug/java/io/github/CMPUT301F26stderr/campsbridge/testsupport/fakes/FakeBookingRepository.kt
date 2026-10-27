package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/** Controlled reservation aggregate; unsupported operations fail rather than simulate success.
 * @param initial Saved reservations to expose to the requested entrant.
 */
class FakeBookingRepository(
    initial: List<Reservation> = emptyList(),
) : BookingRepository {
    val records = MutableStateFlow(initial)
    val getRequests = mutableListOf<String>()
    var getHandler: (suspend (String) -> Reservation?)? = null
    val entrantFailure = MutableStateFlow<Exception?>(null)
    val entrantRequests = mutableListOf<String>()
    val opportunityRequests = mutableListOf<Pair<String, String>>()
    var opportunityHandler: (suspend (String, String) -> Reservation?)? = null
    var observeHandler: ((String) -> Flow<Reservation?>)? = null
    val recordFailure = MutableStateFlow<Exception?>(null)
    val observedRecordIds = mutableListOf<String>()
    var activeRecordObservers = 0
        private set
    var activeEntrantObservers = 0
        private set

    /** Owns one controlled entrant aggregate and exposes actual cancellation cleanup.
     * @param entrantId Requested account.
     */
    override fun observeForEntrant(entrantId: String): Flow<List<Reservation>> =
        flow {
            entrantRequests.add(entrantId)
            activeEntrantObservers++
            try {
                combine(records, entrantFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.filter { it.entrantId == entrantId }
                }.collect { emit(it) }
            } finally {
                activeEntrantObservers--
            }
        }

    /** Reads controlled complete saved facts, including confirmed absence.
     * @param id Requested target.
     */
    override suspend fun get(id: String): Reservation? {
        getRequests.add(id)
        val handler = getHandler
        return if (handler != null) handler(id) else records.value.firstOrNull { it.id == id }
    }

    /** Reads exact controlled opportunity facts without deriving a persistence key in UI.
     * @param lotteryId Requested lottery.
     * @param entrantId Requested entrant.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? {
        opportunityRequests.add(lotteryId to entrantId)
        val handler = opportunityHandler
        return if (handler != null) {
            handler(lotteryId, entrantId)
        } else {
            records.value.firstOrNull { it.lotteryId == lotteryId && it.entrantId == entrantId }
        }
    }

    /** Observes one complete controlled aggregate, with explicit failure and cleanup.
     * @param id Requested target.
     */
    override fun observe(id: String): Flow<Reservation?> =
        flow {
            observedRecordIds.add(id)
            activeRecordObservers++
            try {
                val handler = observeHandler
                if (handler != null) {
                    handler(id).collect { emit(it) }
                    return@flow
                }
                combine(records, recordFailure) { rows, failure ->
                    failure?.let { throw it }
                    rows.firstOrNull { it.id == id }
                }.collect { emit(it) }
            } finally {
                activeRecordObservers--
            }
        }

    /** Rejects unconfigured campsite aggregates.
     * @param campsiteId Requested listing.
     */
    override fun observeForCampsite(campsiteId: String): Flow<List<Reservation>> =
        unsupported()

    /** Rejects unconfigured owner aggregates.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Reservation>> =
        unsupported()

    /** Rejects unconfigured booking writes.
     * @param request Exact reservation attempt.
     */
    override suspend fun reserve(request: BookingRequest): String =
        unsupported()

    /** Rejects unconfigured cancellation writes.
     * @param request Exact cancellation attempt.
     */
    override suspend fun cancel(request: CancellationRequest): Unit =
        unsupported()

    /** Requires a deliberate outcome for each newly consumed operation. */
    private fun unsupported(): Nothing =
        error("Configure this booking operation before using it")
}

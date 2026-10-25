package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Controlled read boundary; writes are deliberately unsupported until a test needs them.
 * @param initial Records emitted by the controlled source.
 */
class FakeCampsiteRepository(
    initial: List<Campsite> = emptyList(),
) : CampsiteRepository {
    val records = MutableStateFlow(initial)
    val streamFailure = MutableStateFlow<Exception?>(null)
    val observedOwners = mutableListOf<String>()
    val observedIds = mutableListOf<String>()
    var activeObservers = 0
    var allObservations = 0
    var readFailure: Exception? = null
    var readGate: CompletableDeferred<Unit>? = null
    val readIds = mutableListOf<String>()
    var writeCalls = 0
    var createHandler: (suspend (String, String, CampsiteDetails) -> Campsite)? = null
    var updateHandler: (suspend (String, String, CampsiteDetails) -> Unit)? = null
    val createRequests = mutableListOf<Triple<String, String, CampsiteDetails>>()
    val updateRequests = mutableListOf<Triple<String, String, CampsiteDetails>>()
    var offerHandler: (suspend (String, String, Set<LocalDate>, Boolean) -> Unit)? = null
    val offerRequests = mutableListOf<OfferRequest>()
    val calendarNights = MutableStateFlow<List<CalendarNight>>(emptyList())
    val calendarFailure = MutableStateFlow<Exception?>(null)
    val observedCalendars = mutableListOf<Triple<String, LocalDate, LocalDate>>()
    var activeCalendarObservers = 0

    /** Captures one explicit inventory operation without simulating its transaction.
     * @param id Actual saved campsite identity.
     * @param ownerId Requesting owner.
     * @param dates Requested local nights.
     * @param offered Desired offered value.
     */
    data class OfferRequest(
        val id: String,
        val ownerId: String,
        val dates: Set<LocalDate>,
        val offered: Boolean,
    )

    /** Returns current controlled server state.
     * @param id Requested listing identity.
     */
    override suspend fun get(id: String): Campsite? {
        readIds.add(id)
        readGate?.await()
        readFailure?.let { throw it }
        return records.value.find { it.id == id }
    }

    /** Observes one controlled record and tracks its lifetime.
     * @param id Requested listing identity.
     */
    override fun observe(id: String): Flow<Campsite?> =
        flow {
            observedIds.add(id)
            emitAll(controlled { rows -> rows.find { it.id == id } })
        }

    /** Supplies active published records. */
    override fun observePublished(): Flow<List<Campsite>> =
        controlled { rows -> rows.filter { it.isPublished } }

    /** Supplies the requested owner's records, including retained removal facts.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Campsite>> =
        flow {
            observedOwners.add(ownerId)
            emitAll(controlled { rows -> rows.filter { it.ownerId == ownerId } })
        }

    /** Counts accidental global reads so feature tests can reject them. */
    override fun observeAll(): Flow<List<Campsite>> =
        flow {
            allObservations++
            emitAll(controlled { it })
        }

    /** Emits the controlled range without inventing inventory writes.
     * @param id Requested listing.
     * @param start Inclusive first date.
     * @param endExclusive Exclusive last date.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<CalendarNight>> =
        flow {
            observedCalendars.add(Triple(id, start, endExclusive))
            activeCalendarObservers++
            try {
                emitAll(
                    combine(calendarNights, calendarFailure) { nights, failure ->
                        failure?.let { throw it }
                        nights.filter { it.date >= start && it.date < endExclusive }
                    },
                )
            } finally {
                activeCalendarObservers--
            }
        }

    /** Rejects unconfigured creation instead of simulating success.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     * @param details Requested values.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Campsite {
        writeCalls++
        createRequests.add(Triple(id, ownerId, details))
        return checkNotNull(createHandler) {
            "Configure a write outcome before testing draft creation"
        }(id, ownerId, details)
    }

    /** Rejects unconfigured edits.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     * @param details Requested values.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ) {
        writeCalls++
        updateRequests.add(Triple(id, ownerId, details))
        checkNotNull(updateHandler) { "Configure a write outcome before testing updates" }(
            id,
            ownerId,
            details,
        )
    }

    /** Rejects unconfigured publication.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) {
        writeCalls++
        error("Configure a write outcome before testing publication")
    }

    /** Rejects unconfigured inventory changes.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     * @param dates Requested dates.
     * @param offered Requested inventory value.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ) {
        writeCalls++
        offerRequests.add(OfferRequest(id, ownerId, dates, offered))
        checkNotNull(offerHandler) { "Configure a write outcome before testing inventory changes" }(
            id,
            ownerId,
            dates,
            offered,
        )
    }

    /** Emits controlled source data/failure and counts subscribed readers.
     * @param transform Projection needed by the requested read.
     */
    private fun <T> controlled(transform: (List<Campsite>) -> T): Flow<T> =
        flow {
            activeObservers++
            try {
                emitAll(
                    combine(records, streamFailure) { rows, failure ->
                        failure?.let { throw it }
                        transform(rows)
                    },
                )
            } finally {
                activeObservers--
            }
        }
}

package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    /** Returns current controlled server state.
     * @param id Requested listing identity.
     */
    override suspend fun get(id: String): Campsite? =
        records.value.find { it.id == id }

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

    /** Provides no inventory in this read-only fixture.
     * @param id Requested listing.
     * @param start Inclusive first date.
     * @param endExclusive Exclusive last date.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<CalendarNight>> =
        flowOf(emptyList())

    /** Rejects unconfigured creation instead of simulating success.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     * @param details Requested values.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Campsite =
        error("Configure a write outcome before testing draft creation")

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
        error("Configure a write outcome before testing updates")
    }

    /** Rejects unconfigured publication.
     * @param id Requested identity.
     * @param ownerId Requested owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) {
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
        error("Configure a write outcome before testing inventory changes")
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

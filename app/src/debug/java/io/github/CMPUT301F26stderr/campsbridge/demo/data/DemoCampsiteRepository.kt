package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Read-only campsite snapshots for the offline demo; no Firebase or write simulation.
 * @param records Scenario-owned campsite snapshots to expose to demo screens.
 * @param calendars Fixed inventory keyed by campsite identity; empty for isolated fixtures.
 */
class DemoCampsiteRepository(
    private val records: List<Campsite> = emptyList(),
    private val calendars: Map<String, List<CalendarNight>> = emptyMap(),
) : CampsiteRepository {
    /** Returns one supplied campsite snapshot, or null when its ID is absent.
     * @param id Requested campsite identity.
     */
    override suspend fun get(id: String): Campsite? =
        records.firstOrNull { it.id == id }

    /** Emits the supplied snapshot for one campsite without opening listeners.
     * @param id Requested campsite identity.
     */
    override fun observe(id: String): Flow<Campsite?> =
        flowOf(records.firstOrNull { it.id == id })

    /** Emits the published, nonremoved supplied campsite snapshots.
     */
    override fun observePublished(): Flow<List<Campsite>> =
        flowOf(records.filter(Campsite::isPublished))

    /** Emits supplied records owned by one account, including drafts and removal history.
     * @param ownerId Account identity whose campsite snapshots are requested.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Campsite>> =
        flowOf(records.filter { it.ownerId == ownerId })

    /** Emits every supplied campsite snapshot, including drafts and removal history.
     */
    override fun observeAll(): Flow<List<Campsite>> =
        flowOf(records)

    /** Rejects draft creation because this demo adapter has no campsite write behavior.
     * @param id Stable draft identity requested by the caller.
     * @param ownerId Account identity requesting the draft.
     * @param details Initial validated campsite details.
     * @throws PersistenceFailure Changes require the Firebase-enabled build.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Campsite =
        throw unavailableChange()

    /** Rejects campsite edits because this demo adapter has no campsite write behavior.
     * @param id Campsite identity to update.
     * @param ownerId Account identity requesting the update.
     * @param details Replacement validated campsite details.
     * @throws PersistenceFailure Changes require the Firebase-enabled build.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Unit =
        throw unavailableChange()

    /** Rejects publication because this demo adapter has no campsite write behavior.
     * @param id Campsite identity to publish.
     * @param ownerId Account identity requesting publication.
     * @throws PersistenceFailure Changes require the Firebase-enabled build.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ): Unit =
        throw unavailableChange()

    /** Emits only the supplied inventory in the requested half-open local date range.
     * @param id Campsite identity whose calendar was requested.
     * @param start Inclusive first local date in the requested range.
     * @param endExclusive Exclusive end of the requested range.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<CalendarNight>> =
        flowOf(calendars[id].orEmpty().filter { it.date >= start && it.date < endExclusive })

    /** Rejects inventory edits because this demo adapter has no campsite write behavior.
     * @param id Campsite identity whose inventory was requested.
     * @param ownerId Account identity requesting the inventory change.
     * @param dates Local dates to offer or withdraw.
     * @param offered Whether the requested dates should be offered.
     * @throws PersistenceFailure Changes require the Firebase-enabled build.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ): Unit =
        throw unavailableChange()

    /** Creates the explicit expected failure for unavailable offline-demo writes. */
    private fun unavailableChange() =
        PersistenceFailure(
            PersistenceFailure.Reason.NOT_ALLOWED,
            "Campsite changes require the Firebase-enabled build",
        )
}

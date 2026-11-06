package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.LocalDate
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Local listing and inventory operations shared by every demonstration area.
 * @param records Initial isolated helper fixtures.
 * @param calendars Initial isolated inventory.
 * @param store Shared store supplied by the main demo host.
 */
class DemoCampsiteRepository(
    records: List<Campsite> = emptyList(),
    calendars: Map<String, List<CalendarNight>> = emptyMap(),
    private val store: DemoStore =
        DemoStore(
            buildMap {
                records.forEach { put(Paths.campsite(it.id), CampsiteDocument.encode(it)) }
                calendars.forEach { (id, nights) ->
                    nights.forEach { put(Paths.night(id, it.date), CalendarNightDocument.encode(it)) }
                }
            },
        ),
) : CampsiteRepository {
    /** Reads the saved listing.
     * @param id Listing identity.
     */
    override suspend fun get(id: String) =
        store.read { get(Paths.campsite(id), CampsiteDocument) }

    /** Observes the saved listing.
     * @param id Listing identity.
     */
    override fun observe(id: String) =
        store.observe { get(Paths.campsite(id), CampsiteDocument) }

    /** Observes discoverable listings after committed changes. */
    override fun observePublished() =
        store.observe { list("campsites", CampsiteDocument).filter { it.isPublished } }

    /** Observes one owner's drafts and published records.
     * @param ownerId Listing owner.
     */
    override fun observeForOwner(ownerId: String) =
        store.observe {
            list("campsites", CampsiteDocument).filter {
                it.ownerId ==
                    ownerId
            }
        }

    /** Observes current records for administration. */
    override fun observeAll() =
        store.observe { list("campsites", CampsiteDocument) }

    /** Creates a stable draft; retries retain saved edits.
     * @param id Stable draft identity.
     * @param ownerId Active owner.
     * @param details Initial listing facts.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Campsite =
        store.change {
            active(ownerId, Role.OWNER)
            val old = get(Paths.campsite(id), CampsiteDocument)
            requireOperation(old == null || old.ownerId == ownerId, CONFLICT, "Draft belongs to another owner")
            old
                ?: Campsite(
                    id,
                    ownerId,
                    details,
                    store.clock.instant(),
                ).also { put(Paths.campsite(id), CampsiteDocument, it) }
        }

    /** Saves editable facts while retaining ownership and lifecycle.
     * @param id Listing identity.
     * @param ownerId Active owner.
     * @param details New listing facts.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ) {
        change(id, ownerId) { it.copy(details = details) }
    }

    /** Publishes once, retaining the original timestamp on retry.
     * @param id Listing identity.
     * @param ownerId Active owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) {
        change(id, ownerId) {
            it.copy(
                publishedAt =
                    it.publishedAt ?: store.clock.instant(),
            )
        }
    }

    /** Observes a half-open range, including existing claims.
     * @param id Listing identity.
     * @param start Inclusive date.
     * @param endExclusive Exclusive date.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ) =
        store.observe {
            list("${Paths.campsite(id)}/calendar", CalendarNightDocument)
                .filter {
                    it.date >= start &&
                        it.date < endExclusive
                }.sortedBy { it.date }
        }

    /** Offers free dates; cancellation must release occupied nights before withdrawal.
     * @param id Listing identity.
     * @param ownerId Active owner.
     * @param dates Dates to change.
     * @param offered New availability.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ) {
        store.change {
            owned(id, ownerId)
            dates.forEach { date ->
                val old = get(Paths.night(id, date), CalendarNightDocument) ?: CalendarNight(date, false)
                requireOperation(
                    old.reservationId == null || offered,
                    CONFLICT,
                    "Cancel the reservation before withdrawing its nights",
                )
                put(Paths.night(id, date), CalendarNightDocument, old.copy(isOffered = offered))
            }
        }
    }

    /** Checks ownership and active state.
     * @param id Listing identity.
     * @param ownerId Active owner.
     */
    private fun DemoDocuments.owned(
        id: String,
        ownerId: String,
    ): Campsite {
        active(ownerId, Role.OWNER)
        return require(Paths.campsite(id), CampsiteDocument).also {
            requireOperation(it.ownerId == ownerId, NOT_ALLOWED, "Different campsite owner")
            requireOperation(it.removal == null, INACTIVE, "Campsite removed")
        }
    }

    /** Commits one authorized edit.
     * @param id Listing identity.
     * @param ownerId Active owner.
     * @param transform Listing edit.
     */
    private suspend fun change(
        id: String,
        ownerId: String,
        transform: (Campsite) -> Campsite,
    ) {
        store.change { put(Paths.campsite(id), CampsiteDocument, transform(owned(id, ownerId))) }
    }
}

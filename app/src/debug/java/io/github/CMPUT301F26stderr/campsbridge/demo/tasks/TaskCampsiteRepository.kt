package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Local calendar store; sample edits never reach Firebase or perform reservation transactions. */
internal class TaskCampsiteRepository :
    CampsiteRepository by DemoCampsiteRepository(TaskExampleData.sites) {
    private val nights = MutableStateFlow(TaskExampleData.nights)

    /** Observes the requested sample range through the same repository contract as production.
     * @param id Fixture listing identity.
     * @param start Inclusive start date.
     * @param endExclusive Exclusive end date.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<CalendarNight>> =
        nights.map { rows ->
            if (id ==
                TaskExampleData.sites[0].id
            ) {
                rows.filter { it.date >= start && it.date < endExclusive }
            } else {
                emptyList()
            }
        }

    /** Applies an explicitly local Offer/Withdraw action while retaining occupied-night protection.
     * @param id Fixture listing identity.
     * @param ownerId Fixture owner.
     * @param dates Explicit desired local dates.
     * @param offered Desired inventory value.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ) {
        require(id == TaskExampleData.sites[0].id && ownerId == TaskExampleData.OWNER_ID)
        require(nights.value.none { it.date in dates && it.reservationId != null })
        val values = nights.value.associateBy { it.date }.toMutableMap()
        dates.forEach { values[it] = CalendarNight(it, offered) }
        nights.value = values.values.toList()
    }
}

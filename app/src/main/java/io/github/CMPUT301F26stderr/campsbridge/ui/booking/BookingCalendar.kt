package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState

/** Projects confirmed inventory and supplied eligibility without approving or changing a stay.
 * @param state Source-backed dates-page facts.
 */
internal fun bookingCalendarState(state: BookingUiState.Content): CalendarState {
    val start = state.draft.checkIn
    val end = state.draft.checkOut
    val selection =
        when {
            start == null -> CalendarSelection.None
            end != null && end > start -> CalendarSelection.Stay(start, end)
            else -> CalendarSelection.Dates(setOf(start))
        }
    val known = state.calendar.visible as? CalendarInventoryRead.Content
    val eligible = state.visibleEligibleNights
    if (known == null || eligible == null || state.eligibilityPending ||
        state.draft.step != BookingStep.DATES
    ) {
        return CalendarState(state.month, selection = selection)
    }
    val nights = known.nights.associateBy { it.date }
    val days =
        (1..state.month.lengthOfMonth()).associate { number ->
            val date = state.month.atDay(number)
            date to
                CalendarDayState(
                    status =
                        when {
                            nights[date]?.reservationId != null -> CalendarDayStatus.OCCUPIED
                            date in eligible -> CalendarDayStatus.AVAILABLE
                            else -> CalendarDayStatus.UNAVAILABLE
                        },
                    isSelectable = date >= state.today && date in eligible,
                )
        }
    return CalendarState(state.month, days, selection, selectionEnabled = true)
}

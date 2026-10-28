package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState

/** Maps confirmed F15/inventory to shared cells, separating checkout from occupied-night status.
 * A candidate endpoint never approves the intermediate nights; the parent validates its range.
 * @param state Current dates, displayed month and source-backed eligibility results.
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
    val selected =
        state.selectedEligibleNights
            .takeIf {
                state.calendar.selected is CalendarInventoryRead.Content
            }.orEmpty()
    val checkoutPredecessors = eligible + selected
    val choosingArrival = start == null || end != null || start < state.today
    val days =
        (1..state.month.lengthOfMonth()).associate { number ->
            val date = state.month.atDay(number)
            val selectable =
                date >= state.today &&
                    when {
                        choosingArrival -> date in eligible
                        date <= checkNotNull(start) -> date in eligible
                        else -> date.minusDays(1) in checkoutPredecessors
                    }
            date to
                CalendarDayState(
                    status =
                        when {
                            nights[date]?.reservationId != null -> CalendarDayStatus.OCCUPIED
                            date in eligible -> CalendarDayStatus.AVAILABLE
                            else -> CalendarDayStatus.UNAVAILABLE
                        },
                    isSelectable = selectable,
                )
        }
    return CalendarState(state.month, days, selection, selectionEnabled = true)
}

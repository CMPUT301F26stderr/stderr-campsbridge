package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState

/** Adapts known inventory to allocation-only date actions without enabling owner offer edits.
 * @param allocation Visible month, working selection and required inventory outcome.
 */
internal fun lotteryAllocationCalendarState(allocation: LotteryAllocationUiState): CalendarState =
    (allocation.inventory as? CampsiteInventoryUiState.Content)?.let { known ->
        val nights = known.nights.associateBy { it.date }
        CalendarState(
            allocation.month,
            (1..allocation.month.lengthOfMonth()).associate { number ->
                val date = allocation.month.atDay(number)
                val night = nights[date]
                date to
                    CalendarDayState(
                        when {
                            night?.reservationId != null -> CalendarDayStatus.OCCUPIED
                            night?.isOffered == true -> CalendarDayStatus.AVAILABLE
                            else -> CalendarDayStatus.UNAVAILABLE
                        },
                        isSelectable = date in allocation.selected || night?.isFree == true,
                    )
            },
            CalendarSelection.Dates(allocation.selected),
            selectionEnabled = true,
        )
    } ?: CalendarState(allocation.month)

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState
import java.time.LocalDate
import java.time.YearMonth

/** Maps only a successfully read month to editable cells; unknown dates never become all-free.
 * @param month Displayed month.
 * @param inventory Known range or pending/failure.
 * @param requested Local explicit desired values.
 * @param selected Working selection, distinct from requested edits in the standalone editor.
 */
internal fun offeredNightCalendarState(
    month: YearMonth,
    inventory: CampsiteInventoryUiState,
    requested: Map<LocalDate, Boolean>,
    selected: Set<LocalDate> = requested.keys,
): CalendarState {
    val known = inventory as? CampsiteInventoryUiState.Content
    val nights = known?.nights.orEmpty().associateBy { it.date }
    val days =
        (1..month.lengthOfMonth()).associate { day ->
            val date = month.atDay(day)
            val night = nights[date]
            date to
                CalendarDayState(
                    status =
                        when {
                            night?.reservationId != null -> {
                                CalendarDayStatus.OCCUPIED
                            }

                            requested[date] ?: (night?.isOffered == true) -> {
                                CalendarDayStatus.AVAILABLE
                            }

                            else -> {
                                CalendarDayStatus.UNAVAILABLE
                            }
                        },
                    isSelectable = known != null && night?.reservationId == null,
                )
        }
    return CalendarState(
        month,
        days,
        CalendarSelection.Dates(selected),
        selectionEnabled = known != null,
    )
}

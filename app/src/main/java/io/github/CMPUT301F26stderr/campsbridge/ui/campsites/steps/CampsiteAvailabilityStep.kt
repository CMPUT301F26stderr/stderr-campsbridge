package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import java.time.LocalDate
import java.time.YearMonth

/** Shows requested offer changes over a confirmed month without saving on selection.
 * @param month Visible local calendar month.
 * @param inventory Confirmed range or explicit unknown/failure.
 * @param requested Desired offered values retained across months.
 * @param onMonth Changes only the displayed month.
 * @param onDate Toggles a known unoccupied date.
 * @param onRetry Retries a failed range.
 */
@Composable
fun CampsiteAvailabilityStep(
    month: YearMonth,
    inventory: CampsiteInventoryUiState,
    requested: Map<LocalDate, Boolean>,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    Text(stringResource(R.string.campsite_calendar_hint))
    CampsiteCalendar(setupCalendarState(month, inventory, requested), onMonth, onDate)
    when (inventory) {
        CampsiteInventoryUiState.Loading -> {
            LoadingContent()
        }

        CampsiteInventoryUiState.Failure -> {
            FailureContent(
                stringResource(R.string.campsite_calendar_failure),
                onRetry,
            )
        }

        is CampsiteInventoryUiState.Content -> {
            Unit
        }
    }
    Text(
        stringResource(
            R.string.campsite_requested_nights,
            requested.values.count { it },
            requested.values.count { !it },
        ),
    )
}

/** Maps only a successfully read month to editable cells; unknown dates never become all-free.
 * @param month Displayed month.
 * @param inventory Known range or pending/failure.
 * @param requested Local explicit desired values.
 */
internal fun setupCalendarState(
    month: YearMonth,
    inventory: CampsiteInventoryUiState,
    requested: Map<LocalDate, Boolean>,
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
        CalendarSelection.Dates(requested.keys),
        selectionEnabled = known != null,
    )
}

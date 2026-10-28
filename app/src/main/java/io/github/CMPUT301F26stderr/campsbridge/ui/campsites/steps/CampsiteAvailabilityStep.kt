package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.offeredNightCalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
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
    inventory: CalendarInventoryRead,
    requested: Map<LocalDate, Boolean>,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    Text(stringResource(R.string.campsite_calendar_hint))
    CampsiteCalendar(offeredNightCalendarState(month, inventory, requested), onMonth, onDate)
    when (inventory) {
        CalendarInventoryRead.Loading -> {
            LoadingContent()
        }

        CalendarInventoryRead.Failure -> {
            FailureContent(
                stringResource(R.string.campsite_calendar_failure),
                onRetry,
            )
        }

        is CalendarInventoryRead.Content -> {
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

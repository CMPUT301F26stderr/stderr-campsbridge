// Shows staged offer changes over confirmed inventory without saving on selection.

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.offeredNightCalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarLabels
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.NightRangeCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** Stages explicit offer or withdraw actions after choosing one or more ranges.
 * @param month Visible local calendar month.
 * @param inventory Confirmed range or explicit unknown/failure.
 * @param requested Desired offered values retained across months.
 * @param onMonth Changes only the displayed month.
 * @param onOfferedChange Stages offered or withdrawn nights without saving them.
 * @param onRetry Retries a failed range.
 */
@Composable
fun CampsiteAvailabilityStep(
    month: YearMonth,
    inventory: CalendarInventoryRead,
    requested: Map<LocalDate, Boolean>,
    onMonth: (YearMonth) -> Unit,
    onOfferedChange: (Set<LocalDate>, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    var selectedDays by rememberSaveable(month.toString()) { mutableStateOf(longArrayOf()) }
    val selected = selectedDays.map(LocalDate::ofEpochDay).toSet()
    Text(stringResource(R.string.availability_hint))
    NightRangeCalendar(
        offeredNightCalendarState(month, inventory, requested, selected, labels = CalendarLabels.OFFERED_NIGHTS),
        onMonth,
        { selectedDays = it.map(LocalDate::toEpochDay).toLongArray() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        for ((offered, label) in listOf(true to R.string.availability_offer, false to R.string.availability_withdraw)) {
            AppActionButton(
                stringResource(label),
                {
                    onOfferedChange(selected, offered)
                    selectedDays = longArrayOf()
                },
                enabled = selected.isNotEmpty() && inventory is CalendarInventoryRead.Content,
                style = ActionStyle.SECONDARY,
            )
        }
    }
    when (inventory) {
        CalendarInventoryRead.Loading -> LoadingContent()
        CalendarInventoryRead.Failure -> FailureContent(stringResource(R.string.campsite_calendar_failure), onRetry)
        is CalendarInventoryRead.Content -> Unit
    }
    Text(
        stringResource(
            R.string.campsite_requested_nights,
            requested.values.count { it },
            requested.values.count { !it },
        ),
    )
}

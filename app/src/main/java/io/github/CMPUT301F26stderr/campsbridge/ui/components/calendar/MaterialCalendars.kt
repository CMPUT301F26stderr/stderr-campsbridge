package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.dateToPickerMillis
import io.github.CMPUT301F26stderr.campsbridge.ui.common.pickerMillisToDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.YearMonth

/** Selects inclusive ranges to add to caller-owned individual nights.
 * @param state Loaded month and selected nights.
 * @param onMonthChange Requests another inventory month.
 * @param onSelectionChange Replaces the selected set after an explicit add or removal.
 * @param modifier Caller layout.
 * @param enabled Whether an in-progress operation permits input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightRangeCalendar(
    state: CalendarState,
    onMonthChange: (YearMonth) -> Unit,
    onSelectionChange: (Set<LocalDate>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val picker =
        rememberDateRangePickerState(
            initialDisplayedMonthMillis = dateToPickerMillis(state.month.atDay(1)),
            selectableDates = rememberInventoryDates(state, enabled),
        )
    var pendingMonth by rememberSaveable { mutableLongStateOf(state.month.atDay(1).toEpochDay()) }
    LaunchedEffect(state.month) {
        val month = state.month.atDay(1).toEpochDay()
        if (pendingMonth != month) {
            picker.setSelection(null, null)
            pendingMonth = month
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val selected = (state.selection as? CalendarSelection.Dates)?.dates.orEmpty()
    val start = pickerMillisToDate(picker.selectedStartDateMillis)
    val end = pickerMillisToDate(picker.selectedEndDateMillis)
    val proposed = selectableNightRange(state, start, end)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(R.string.calendar_range_hint), style = MaterialTheme.typography.bodyMedium)
        MaterialRangeCalendar(picker, state.month, onMonthChange, enabled)
        TextButton(
            onClick = {
                if (enabled && proposed != null) {
                    onSelectionChange(selected + proposed)
                    picker.setSelection(null, null)
                }
            },
            enabled = enabled && proposed != null,
        ) { Text(stringResource(R.string.calendar_add_range)) }
        if (start != null && end != null && proposed == null) {
            Text(stringResource(R.string.calendar_range_unavailable), color = MaterialTheme.colorScheme.error)
        }
        for (range in nightRanges(selected)) {
            TextButton(
                onClick = { onSelectionChange(selected.filterNot { it >= range.start && it <= range.end }.toSet()) },
                enabled = enabled && state.selectionEnabled,
            ) {
                Text(stringResource(R.string.calendar_remove_range, formatNightRange(range, locale)))
            }
        }
        if (state.selectionEnabled) CalendarInventorySummary(state)
    }
}

/** Selects arrival and exclusive checkout in one Material calendar.
 * @param state Loaded month and date permissions.
 * @param start Selected arrival or null.
 * @param end Selected checkout or null.
 * @param onMonthChange Requests another inventory month.
 * @param onDatesChange Reports both current endpoints together.
 * @param modifier Caller layout.
 * @param enabled Whether the draft accepts input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StayDateRangeCalendar(
    state: CalendarState,
    start: LocalDate?,
    end: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDatesChange: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val picker =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.let(::dateToPickerMillis),
            initialSelectedEndDateMillis = end?.takeIf { start != null && it >= start }?.let(::dateToPickerMillis),
            initialDisplayedMonthMillis = dateToPickerMillis(state.month.atDay(1)),
            selectableDates = rememberInventoryDates(state, enabled),
        )
    val currentDates by rememberUpdatedState(start to end?.takeIf { start != null && it >= start })
    val currentCallback by rememberUpdatedState(onDatesChange)
    val inputEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(start, end) {
        val from = start?.let(::dateToPickerMillis)
        val until = end?.takeIf { start != null && it >= start }?.let(::dateToPickerMillis)
        if (picker.selectedStartDateMillis != from || picker.selectedEndDateMillis != until) {
            picker.setSelection(from, until)
        }
    }
    LaunchedEffect(picker) {
        snapshotFlow {
            pickerMillisToDate(picker.selectedStartDateMillis) to pickerMillisToDate(picker.selectedEndDateMillis)
        }.distinctUntilChanged().drop(1).collect { dates ->
            if (inputEnabled && dates != currentDates) currentCallback(dates.first, dates.second)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        MaterialRangeCalendar(picker, state.month, onMonthChange, enabled)
        if (state.selectionEnabled) CalendarInventorySummary(state)
    }
}

/** Rebuilds Material's cached date permissions whenever confirmed inventory changes.
 * @param state Current date permissions, including checkout-specific rules for stays.
 * @param enabled Whether the owning operation permits selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberInventoryDates(
    state: CalendarState,
    enabled: Boolean,
): SelectableDates =
    remember(state, enabled) {
        object : SelectableDates {
            /** Uses only caller-confirmed permissions.
             * @param utcTimeMillis Material's UTC date representation.
             */
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                enabled && state.selectionEnabled && state.days[pickerMillisToDate(utcTimeMillis)]?.isSelectable == true
        }
    }

/** Keeps stock range rendering and month navigation shared by both selection flows.
 * @param picker Saveable Material selection and scrolling state.
 * @param month Inventory month owned by the caller.
 * @param onMonthChange Loads the first visible inventory month after scrolling.
 * @param enabled Whether navigation and selection accept input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialRangeCalendar(
    picker: DateRangePickerState,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    enabled: Boolean,
) {
    val currentMonth by rememberUpdatedState(month)
    val currentCallback by rememberUpdatedState(onMonthChange)
    val inputEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(month) {
        val displayed = YearMonth.from(pickerMillisToDate(picker.displayedMonthMillis))
        if (displayed != month) picker.displayedMonthMillis = dateToPickerMillis(month.atDay(1))
    }
    LaunchedEffect(picker) {
        snapshotFlow { YearMonth.from(pickerMillisToDate(picker.displayedMonthMillis)) }
            .distinctUntilChanged()
            .collect { displayed ->
                if (inputEnabled && displayed != currentMonth) currentCallback(displayed)
            }
    }
    MaterialCalendarViewport {
        DateRangePicker(
            picker,
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .testTag("material-range-calendar")
                .calendarInput(enabled),
            title = null,
            headline = null,
            showModeToggle = false,
        )
    }
}

/** Blocks all picker input during a write, including month scrolling and accessibility actions.
 * @param enabled Whether the caller permits input.
 */
internal fun Modifier.calendarInput(enabled: Boolean): Modifier =
    if (enabled) {
        this
    } else {
        clearAndSetSemantics { disabled() }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

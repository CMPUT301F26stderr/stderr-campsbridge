// Displays inventory with Google's calendar and a separate text summary.

package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.dateToPickerMillis
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.pickerMillisToDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Inspects dates without editing or persisting inventory.
 * @param state Confirmed inventory for the displayed month.
 * @param onMonthChange Requests inventory for another month.
 * @param modifier Caller layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteCalendar(
    state: CalendarState,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates =
        remember(state.days) {
            object : SelectableDates {
                /** Allows inspecting only dates with loaded inventory.
                 * @param utcTimeMillis Material's UTC date representation.
                 */
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    pickerMillisToDate(utcTimeMillis) in state.days
            }
        }
    val picker =
        rememberDatePickerState(
            initialDisplayedMonthMillis = dateToPickerMillis(state.month.atDay(1)),
            selectableDates = dates,
        )
    val currentMonth by rememberUpdatedState(state.month)
    val currentCallback by rememberUpdatedState(onMonthChange)
    LaunchedEffect(state.month) {
        if (YearMonth.from(pickerMillisToDate(picker.displayedMonthMillis)) != state.month) {
            picker.displayedMonthMillis = dateToPickerMillis(state.month.atDay(1))
        }
    }
    LaunchedEffect(picker) {
        snapshotFlow { YearMonth.from(pickerMillisToDate(picker.displayedMonthMillis)) }
            .distinctUntilChanged()
            .collect { month -> if (month != currentMonth) currentCallback(month) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        MaterialCalendarViewport {
            DatePicker(picker, Modifier.fillMaxWidth(), title = null, headline = null, showModeToggle = false)
        }
        val inspected = pickerMillisToDate(picker.selectedDateMillis)
        val status = state.days[inspected]?.status
        if (inspected != null && status != null) {
            val locale = LocalConfiguration.current.locales[0]
            Text(
                stringResource(
                    R.string.calendar_date_status,
                    formatDate(inspected, locale),
                    calendarStatusLabel(state.labels, status),
                ),
            )
        }
        CalendarInventorySummary(state)
    }
}

/** Lists inventory outside the stock picker, where its meaning stays readable without colour.
 * @param state Confirmed inventory and label vocabulary.
 */
@Composable
internal fun CalendarInventorySummary(state: CalendarState) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(R.string.calendar_inventory_summary), style = MaterialTheme.typography.titleSmall)
        for (status in CalendarDayStatus.entries) {
            val ranges = nightRanges(state.days.filterValues { it.status == status }.keys)
            if (ranges.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.calendar_date_status,
                        calendarStatusLabel(state.labels, status),
                        ranges.joinToString { formatNightRange(it, locale) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Resolves owner offer intent separately from guest availability.
 * @param labels Vocabulary requested by the screen.
 * @param status Confirmed inventory status.
 */
@Composable
private fun calendarStatusLabel(
    labels: CalendarLabels,
    status: CalendarDayStatus,
): String =
    stringResource(
        when (status) {
            CalendarDayStatus.AVAILABLE -> {
                if (labels ==
                    CalendarLabels.OFFERED_NIGHTS
                ) {
                    R.string.calendar_offered
                } else {
                    R.string.calendar_available
                }
            }

            CalendarDayStatus.UNAVAILABLE -> {
                if (labels ==
                    CalendarLabels.OFFERED_NIGHTS
                ) {
                    R.string.calendar_not_offered
                } else {
                    R.string.calendar_unavailable
                }
            }

            CalendarDayStatus.OCCUPIED -> {
                R.string.calendar_occupied
            }
        },
    )

/** Formats inclusive nights compactly while retaining the year and month boundaries.
 * @param range Consecutive nights, including both endpoints.
 * @param locale Device date language.
 */
internal fun formatNightRange(
    range: NightRange,
    locale: Locale,
): String {
    val full = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    return when {
        range.start == range.end -> {
            range.start.format(full)
        }

        YearMonth.from(range.start) == YearMonth.from(range.end) -> {
            range.start.format(DateTimeFormatter.ofPattern("MMM d", locale)) + "–" +
                range.end.format(DateTimeFormatter.ofPattern("d, yyyy", locale))
        }

        else -> {
            range.start.format(full) + " – " + range.end.format(full)
        }
    }
}

/** Shows the stock Material range treatment beside selected nights and inventory. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
private fun CalendarPreview() {
    val month = YearMonth.of(2027, 5)
    CampsbridgeTheme {
        NightRangeCalendar(
            CalendarState(
                month,
                (1..month.lengthOfMonth()).associate { day ->
                    month.atDay(day) to
                        CalendarDayState(
                            if (day ==
                                16
                            ) {
                                CalendarDayStatus.OCCUPIED
                            } else {
                                CalendarDayStatus.AVAILABLE
                            },
                            day != 16,
                        )
                },
                CalendarSelection.Dates(setOf(month.atDay(14), month.atDay(15))),
                selectionEnabled = true,
            ),
            {},
            {},
        )
    }
}

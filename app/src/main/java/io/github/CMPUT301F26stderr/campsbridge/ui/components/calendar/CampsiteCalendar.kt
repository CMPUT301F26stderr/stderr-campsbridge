package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Displays inventory and supplied selection without performing persistence operations.
 * @param state Current displayed month, inventory and selection.
 * @param onMonthChange Requests a different displayed month.
 * @param onDateClick Delivers a selectable date; the caller updates selection.
 * @param modifier Layout customization from the parent.
 * @param enabled Whether month and date controls accept input during a caller-owned operation.
 */
@Composable
fun CampsiteCalendar(
    state: CalendarState,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val displayState = if (enabled) state else state.copy(selectionEnabled = false)
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val cells =
        remember(
            state.month,
            state.firstWeekday,
        ) { buildMonthCells(state.month, state.firstWeekday) }
    val minimumCell = 48.dp * LocalControlScale.current.coerceAtLeast(1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(state.month.minusMonths(1)) }, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.calendar_previous_month),
                )
            }
            Text(
                state.month.format(monthFormat),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onMonthChange(state.month.plusMonths(1)) }, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    stringResource(R.string.calendar_next_month),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellWidth = maxOf(maxWidth / 7, minimumCell)
            // Preserve touch targets when accessibility scaling exceeds the available width.
            Column(Modifier.horizontalScroll(rememberScrollState()).width(cellWidth * 7)) {
                Row {
                    repeat(7) { offset ->
                        Text(
                            state.firstWeekday
                                .plus(
                                    offset.toLong(),
                                ).getDisplayName(TextStyle.SHORT, locale),
                            Modifier.width(cellWidth),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.width(cellWidth))
                            } else {
                                CalendarDate(
                                    date,
                                    displayState,
                                    cellWidth,
                                    minimumCell,
                                    locale,
                                    onDateClick,
                                )
                            }
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            CalendarDayStatus.entries.forEach { status ->
                Text(
                    "${calendarMark(status)} ${calendarLabel(status)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Renders one date with independent inventory, selection and action semantics.
 * @param date Date represented by this cell.
 * @param state Caller-owned inventory and selection.
 * @param width Cell width shared by all weekdays.
 * @param minimumHeight Scaled minimum interactive height.
 * @param locale Locale for the spoken full date.
 * @param onDateClick Explicit selection action.
 */
@Composable
private fun CalendarDate(
    date: LocalDate,
    state: CalendarState,
    width: Dp,
    minimumHeight: Dp,
    locale: Locale,
    onDateClick: (LocalDate) -> Unit,
) {
    val day = state.days[date] ?: CalendarDayState(CalendarDayStatus.UNAVAILABLE)
    val isSelected = date in state.selection
    val label = calendarLabel(day.status)
    val background =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    TextButton(
        onClick = { onDateClick(date) },
        enabled = state.selectionEnabled && day.isSelectable,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(vertical = 4.dp),
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = background,
                disabledContainerColor = background,
            ),
        modifier =
            Modifier
                .width(width)
                .heightIn(min = minimumHeight)
                .testTag("calendar-date-$date")
                .semantics {
                    selected = isSelected
                    stateDescription = label
                    contentDescription =
                        date.format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale),
                        )
                },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            Text(
                calendarMark(day.status),
                Modifier.clearAndSetSemantics {
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Resolves the visible and spoken inventory label.
 * @param status Inventory presentation supplied by the caller.
 */
@Composable
private fun calendarLabel(status: CalendarDayStatus): String =
    stringResource(
        when (status) {
            CalendarDayStatus.AVAILABLE -> R.string.calendar_available
            CalendarDayStatus.UNAVAILABLE -> R.string.calendar_unavailable
            CalendarDayStatus.OCCUPIED -> R.string.calendar_occupied
        },
    )

/**
 * Resolves a visible non-color inventory mark with matching legend text.
 * @param status Inventory presentation supplied by the caller.
 */
@Composable
private fun calendarMark(status: CalendarDayStatus): String =
    stringResource(
        when (status) {
            CalendarDayStatus.AVAILABLE -> R.string.calendar_available_mark
            CalendarDayStatus.UNAVAILABLE -> R.string.calendar_unavailable_mark
            CalendarDayStatus.OCCUPIED -> R.string.calendar_occupied_mark
        },
    )

/**
 * Frames fixture inventory without repositories or simulated persistence.
 * @param editing Whether to show an owner's editable selection.
 */
@Composable
private fun CalendarPreviewContent(editing: Boolean) {
    val month = YearMonth.of(2027, 5)
    val days =
        (1..month.lengthOfMonth()).associate { number ->
            val date = month.atDay(number)
            val status =
                when {
                    number in 7..8 -> CalendarDayStatus.OCCUPIED
                    date.dayOfWeek.value in 5..6 -> CalendarDayStatus.AVAILABLE
                    else -> CalendarDayStatus.UNAVAILABLE
                }
            date to CalendarDayState(status, editing && status != CalendarDayStatus.OCCUPIED)
        }
    val selection =
        if (editing) {
            CalendarSelection.Dates(setOf(month.atDay(14), month.atDay(15)))
        } else {
            CalendarSelection.Stay(month.atDay(7), month.atDay(9))
        }
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.calendar_title)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CampsiteCalendar(
                    CalendarState(month, days, selection, selectionEnabled = editing),
                    {},
                    {},
                )
            }
        }
    }
}

/** Shows read-only inventory and the occupied nights of a saved stay. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ReadOnlyCalendarPreview() {
    CalendarPreviewContent(false)
}

/** Shows editable offered nights with occupied dates unavailable for editing. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun EditableCalendarPreview() {
    CalendarPreviewContent(true)
}

/** Shows the same content with enlarged system text. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun EnlargedCalendarPreview() {
    CalendarPreviewContent(true)
}

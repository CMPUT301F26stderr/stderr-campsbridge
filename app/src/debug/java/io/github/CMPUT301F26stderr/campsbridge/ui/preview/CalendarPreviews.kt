package io.github.CMPUT301F26stderr.campsbridge.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarDayStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarSelection
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CalendarState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.YearMonth

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

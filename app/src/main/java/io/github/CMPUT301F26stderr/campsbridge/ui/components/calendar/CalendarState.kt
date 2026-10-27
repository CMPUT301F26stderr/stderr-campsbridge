package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Inventory presentation independent of visual selection. */
enum class CalendarDayStatus { UNAVAILABLE, AVAILABLE, OCCUPIED }

/** Labels known owner offer intent separately from availability to a prospective guest. */
enum class CalendarLabels { AVAILABILITY, OFFERED_NIGHTS }

/**
 * Caller-supplied inventory label and action availability.
 * @param status Inventory presentation for the date.
 * @param isSelectable Whether this date can deliver a selection callback in the current flow.
 */
data class CalendarDayState(
    val status: CalendarDayStatus,
    val isSelectable: Boolean = false,
)

/**
 * Immutable calendar content; omitted dates are unavailable and unselectable.
 * @param month Displayed month, changed only by the owning caller.
 * @param days Known date presentations.
 * @param selection Highlighted dates, independent of inventory.
 * @param firstWeekday First column in each week.
 * @param selectionEnabled Whether date actions are enabled for the whole calendar.
 * @param labels Meaning of known inventory; unknown ranges retain availability labels.
 */
data class CalendarState(
    val month: YearMonth,
    val days: Map<LocalDate, CalendarDayState> = emptyMap(),
    val selection: CalendarSelection = CalendarSelection.None,
    val firstWeekday: DayOfWeek = DayOfWeek.MONDAY,
    val selectionEnabled: Boolean = false,
    val labels: CalendarLabels = CalendarLabels.AVAILABILITY,
)

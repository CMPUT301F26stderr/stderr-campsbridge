package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import java.time.LocalDate

/** Consecutive nights, including both endpoints.
 * @param start First night.
 * @param end Last night.
 */
data class NightRange(
    val start: LocalDate,
    val end: LocalDate,
)

/** Groups selected nights without filling gaps.
 * @param dates Selected nights from any displayed months.
 */
fun nightRanges(dates: Set<LocalDate>): List<NightRange> {
    val sorted = dates.sorted()
    if (sorted.isEmpty()) return emptyList()
    val ranges = mutableListOf<NightRange>()
    var start = sorted.first()
    var end = start
    for (date in sorted.drop(1)) {
        if (date.toEpochDay() != end.toEpochDay() + 1) {
            ranges += NightRange(start, end)
            start = date
        }
        end = date
    }
    return ranges + NightRange(start, end)
}

/** Returns a complete selectable range, or null when any date is unknown or blocked.
 * @param state Confirmed visible-month inventory and permissions.
 * @param start First proposed night.
 * @param end Last proposed night, included.
 */
fun selectableNightRange(
    state: CalendarState,
    start: LocalDate?,
    end: LocalDate?,
): Set<LocalDate>? {
    if (!state.selectionEnabled || start == null || end == null || end < start) return null
    val count = end.toEpochDay() - start.toEpochDay() + 1
    if (count > state.days.size) return null
    val dates = (0L until count).map { start.plusDays(it) }.toSet()
    return dates.takeIf { range -> range.all { state.days[it]?.isSelectable == true } }
}

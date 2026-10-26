package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Builds consecutive month dates with null padding to complete whole weeks.
 * @param month Month whose dates are represented.
 * @param firstWeekday Weekday occupying the first column.
 */
fun buildMonthCells(
    month: YearMonth,
    firstWeekday: DayOfWeek,
): List<LocalDate?> {
    val offset = (month.atDay(1).dayOfWeek.value - firstWeekday.value + 7) % 7
    val days = month.lengthOfMonth()
    val size = ((offset + days + 6) / 7) * 7
    return List(size) { index ->
        if (index < offset || index >= offset + days) null else month.atDay(index - offset + 1)
    }
}

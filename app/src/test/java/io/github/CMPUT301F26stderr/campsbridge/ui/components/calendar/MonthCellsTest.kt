package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

/** Gregorian month structure and configurable week-start contracts. */
class MonthCellsTest {
    /** Each date occurs once, in order, with only leading/trailing whole-week padding. */
    @Test fun calendarStructureAcrossWeekStartsAndLeapBoundaries() {
        val rows = mutableSetOf<Int>()
        listOf(1900, 2000, 2026, 2027, 2028).forEach { year ->
            (1..12).forEach { number ->
                val month = YearMonth.of(year, number)
                DayOfWeek.entries.forEach { firstWeekday ->
                    val cells = buildMonthCells(month, firstWeekday)
                    val offset = (month.atDay(1).dayOfWeek.value - firstWeekday.value + 7) % 7
                    assertEquals(
                        (1..month.lengthOfMonth()).map(month::atDay),
                        cells.filterNotNull(),
                    )
                    assertEquals(offset, cells.indexOf(month.atDay(1)))
                    assertEquals(0, cells.size % 7)
                    assertTrue(cells.take(offset).all { it == null })
                    assertTrue(cells.drop(offset + month.lengthOfMonth()).all { it == null })
                    assertTrue(cells.size in 28..42)
                    rows.add(cells.size / 7)
                }
            }
        }
        assertEquals(setOf(4, 5, 6), rows)
    }
}

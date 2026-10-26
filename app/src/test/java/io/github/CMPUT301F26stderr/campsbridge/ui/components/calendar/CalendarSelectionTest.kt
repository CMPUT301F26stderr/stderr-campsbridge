package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Selection is local presentation state; stays always exclude checkout. */
class CalendarSelectionTest {
    /** Individual selections and stay ranges remain distinct. */
    @Test fun membershipRespectsSelectionType() {
        val start = LocalDate.of(2027, 5, 1)
        assertFalse(start in CalendarSelection.None)
        assertTrue(start in CalendarSelection.Dates(setOf(start)))
        assertFalse(start.plusDays(1) in CalendarSelection.Dates(setOf(start)))
        val stay = CalendarSelection.Stay(start, start.plusDays(2))
        assertFalse(start.minusDays(1) in stay)
        assertTrue(start in stay)
        assertTrue(start.plusDays(1) in stay)
        assertFalse(start.plusDays(2) in stay)
        assertThrows(IllegalArgumentException::class.java) { CalendarSelection.Stay(start, start) }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarSelection.Stay(start, start.minusDays(1))
        }
    }
}

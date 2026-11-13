/*
 * Deterministic formatting and timezone input contracts.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.common.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/** Deterministic formatting and timezone input contracts. */
class DisplayFormatsTest {
    /** Currency retains exact cents, including the maximum Long value, without floating point. */
    @Test fun exactCadAmounts() {
        assertEquals("CAD 0.00", formatCad(0, Locale.CANADA))
        assertEquals("CAD 12.34", formatCad(1234, Locale.CANADA))
        assertEquals("CAD 92,233,720,368,547,758.07", formatCad(Long.MAX_VALUE, Locale.CANADA))
        assertEquals("CAD 12,34", formatCad(1234, Locale.FRANCE))
        assertThrows(IllegalArgumentException::class.java) { formatCad(-1, Locale.CANADA) }
    }

    /** Local dates stay local while timestamp display stays in Edmonton after device-zone changes. */
    @Test fun datesAndInstantsUseExplicitContext() {
        val date = LocalDate.of(2027, 5, 14)
        assertEquals("May 14, 2027", formatDate(date, Locale.US))
        val instant = Instant.parse("2027-05-15T01:00:00Z")
        val edmonton = formatDateTime(instant, Locale.US)
        assertTrue(edmonton.contains("May 14, 2027"))
        assertTrue(edmonton.contains("7:00"))
        assertTrue(edmonton.contains("MDT"))
        val french = formatDateTime(instant, Locale.FRANCE)
        assertTrue(french.contains("14 mai 2027"))
        assertTrue(french.contains("19:00"))
        val deviceZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(edmonton, formatDateTime(instant, Locale.US))
        } finally {
            TimeZone.setDefault(deviceZone)
        }
    }

    /** Recorded 2025 gaps and overlaps never silently shift a lottery deadline. */
    @Test fun daylightSavingBoundariesAreExplicit() {
        val zone = ZoneId.of("America/Edmonton")
        assertEquals(
            Instant.parse("2027-05-14T15:30:00Z"),
            resolveDateTime(LocalDate.of(2027, 5, 14), 9, 30, zone),
        )
        assertNull(resolveDateTime(LocalDate.of(2025, 3, 9), 2, 30, zone))
        assertNull(resolveDateTime(LocalDate.of(2025, 11, 2), 1, 30, zone))
        assertThrows(java.time.DateTimeException::class.java) {
            resolveDateTime(LocalDate.of(2027, 5, 14), 24, 0, zone)
        }
    }
}

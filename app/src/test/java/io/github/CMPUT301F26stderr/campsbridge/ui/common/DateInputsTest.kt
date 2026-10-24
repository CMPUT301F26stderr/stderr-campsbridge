package io.github.CMPUT301F26stderr.campsbridge.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/** Picker values encode local dates using UTC irrespective of device timezone. */
class DateInputsTest {
    /** Missing selection stays absent rather than becoming the epoch. */
    @Test fun absentSelectionIsNull() {
        assertNull(pickerMillisToDate(null))
    }

    /** Calendar dates survive negative epochs, leap days and daylight-saving boundaries. */
    @Test fun roundTripIgnoresDeviceTimezone() {
        val previous = TimeZone.getDefault()
        try {
            listOf("America/Edmonton", "Pacific/Kiritimati", "Pacific/Honolulu").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf("1960-01-01", "2000-02-29", "2026-03-08", "2026-11-01").forEach {
                    val date = LocalDate.parse(it)
                    val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    assertEquals(millis, dateToPickerMillis(date))
                    assertEquals(date, pickerMillisToDate(millis))
                }
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}

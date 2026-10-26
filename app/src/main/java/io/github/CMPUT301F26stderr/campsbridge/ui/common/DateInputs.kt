package io.github.CMPUT301F26stderr.campsbridge.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Decodes Material's UTC date representation without applying a device timezone.
 * @param millis Selected picker milliseconds, or null when nothing is selected.
 */
fun pickerMillisToDate(millis: Long?): LocalDate? =
    millis?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }

/**
 * Encodes a local calendar date for Material date pickers using UTC midnight.
 * @param date Local date without time-of-day or device timezone semantics.
 */
fun dateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

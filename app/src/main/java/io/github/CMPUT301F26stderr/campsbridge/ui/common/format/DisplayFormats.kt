package io.github.CMPUT301F26stderr.campsbridge.ui.common.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formats exact integer cents with an explicit CAD currency code.
 * @param cents Nonnegative amount in cents.
 * @param locale Number-format locale.
 */
fun formatCad(
    cents: Long,
    locale: Locale,
): String {
    require(cents >= 0)
    val numbers =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    return "CAD ${numbers.format(BigDecimal.valueOf(cents, 2))}"
}

/** Formats a campsite-local date without applying a device timezone.
 * @param date Calendar date to display.
 * @param locale Date-format locale.
 */
fun formatDate(
    date: LocalDate,
    locale: Locale,
): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/** Formats an instant with the explicitly selected timezone.
 * @param instant Recorded instant.
 * @param zone Timezone used for display.
 * @param locale Date/time-format locale.
 */
fun formatDateTime(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
): String {
    val dateTime =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(instant)
    val zoneLabel = DateTimeFormatter.ofPattern("z", locale).withZone(zone).format(instant)
    return "$dateTime $zoneLabel"
}

/** Resolves wall-clock input only when its timezone offset is unambiguous.
 * @param date Selected local date.
 * @param hour Selected hour in 24-hour time.
 * @param minute Selected minute.
 * @param zone Explicit lottery timezone.
 * @return Instant, or null for a daylight-saving gap or overlapping local time.
 */
fun resolveDateTime(
    date: LocalDate,
    hour: Int,
    minute: Int,
    zone: ZoneId,
): Instant? {
    val local = date.atTime(hour, minute)
    val offset = zone.rules.getValidOffsets(local).singleOrNull() ?: return null
    return local.toInstant(offset)
}

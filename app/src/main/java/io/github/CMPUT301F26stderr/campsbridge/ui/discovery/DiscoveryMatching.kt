package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import java.time.LocalDate
import java.util.Locale

/** Matches the human-readable location independently of the reserved general text predicate.
 * @param label Stored location label.
 * @param query Applied location query.
 */
internal fun matchesDiscoveryLocation(
    label: String,
    query: String,
): Boolean =
    label.lowercase(Locale.ROOT).contains(query.trim().lowercase(Locale.ROOT))

/** Requires every occupied night to be known offered/free; checkout is excluded.
 * @param nights Confirmed records from the requested range.
 * @param checkIn Inclusive first occupied night.
 * @param checkOut Exclusive checkout.
 */
internal fun hasAvailableStay(
    nights: List<CalendarNight>,
    checkIn: LocalDate,
    checkOut: LocalDate,
): Boolean {
    require(checkOut > checkIn)
    val byDate = nights.associateBy { it.date }
    var date = checkIn
    while (date < checkOut) {
        if (byDate[date]?.isFree != true) return false
        date = date.plusDays(1)
    }
    return true
}

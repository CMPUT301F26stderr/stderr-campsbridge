/*
 * Returns allocation dates whose supplied physical inventory is offered and unclaimed.
 */

package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import java.time.LocalDate

/** Returns allocation dates whose supplied physical inventory is offered and unclaimed.
 * @param offered Lottery allocation dates; no date outside this set may be returned.
 * @param calendar Confirmed physical nights with unique dates; absent dates are unavailable.
 * @return Matching free dates without modifying either input, reading time or doing IO.
 */
fun availableBookingNights(
    offered: Set<LocalDate>,
    calendar: List<CalendarNight>,
): Set<LocalDate> =
    calendar
        .filter { it.date in offered && it.isFree }
        .mapTo(mutableSetOf()) { it.date }

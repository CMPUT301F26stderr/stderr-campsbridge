package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import java.time.LocalDate

/** Ordered feedback for selected dates against confirmed eligible occupied nights. */
enum class StaySelectionInput {
    MISSING_CHECK_IN,
    MISSING_CHECK_OUT,
    INVALID_RANGE,
    PAST_CHECK_IN,
    UNAVAILABLE_NIGHTS,
    VALID,
}

/** Checks endpoints, positive duration, nonpast arrival and every occupied night in order.
 * @param checkIn Inclusive arrival, or null before selection.
 * @param checkOut Exclusive departure, or null before selection.
 * @param eligibleNights Confirmed eligible occupied dates; checkout need not be included.
 * @param today Current campsite-local date supplied by the caller.
 * @return First failing condition or VALID, without changing inputs or reading a clock.
 */
fun validateStaySelection(
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    eligibleNights: Set<LocalDate>,
    today: LocalDate,
): StaySelectionInput {
    if (checkIn == null) return StaySelectionInput.MISSING_CHECK_IN
    if (checkOut == null) return StaySelectionInput.MISSING_CHECK_OUT
    if (checkOut <= checkIn) return StaySelectionInput.INVALID_RANGE
    if (checkIn < today) return StaySelectionInput.PAST_CHECK_IN
    var night: LocalDate = checkIn
    while (night < checkOut) {
        if (night !in eligibleNights) return StaySelectionInput.UNAVAILABLE_NIGHTS
        night = night.plusDays(1)
    }
    return StaySelectionInput.VALID
}

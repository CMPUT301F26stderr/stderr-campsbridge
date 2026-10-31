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

/** Checks missing endpoints, positive duration and a nonpast campsite-local arrival in order.
 * @param checkIn Inclusive arrival, or null before selection.
 * @param checkOut Exclusive departure, or null before selection.
 * @param eligibleNights Confirmed eligible occupied dates; checkout need not be included.
 * @param today Current campsite-local date supplied by the caller.
 * @return First date error; occupied-night validation remains pending.
 */
@Suppress("UNUSED_PARAMETER")
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
    return StaySelectionInput.UNAVAILABLE_NIGHTS
}

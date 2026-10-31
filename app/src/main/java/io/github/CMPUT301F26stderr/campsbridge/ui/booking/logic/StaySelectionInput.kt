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

/** Validates selected local dates; BK1 behavior is pending the supplied contract tests.
 * @param checkIn Inclusive arrival, or null before selection.
 * @param checkOut Exclusive departure, or null before selection.
 * @param eligibleNights Confirmed eligible occupied dates; checkout need not be included.
 * @param today Current campsite-local date supplied by the caller.
 * @return Placeholder feedback until the ordered checks are implemented.
 */
@Suppress("UNUSED_PARAMETER")
fun validateStaySelection(
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    eligibleNights: Set<LocalDate>,
    today: LocalDate,
): StaySelectionInput =
    StaySelectionInput.MISSING_CHECK_IN

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic

import java.time.Instant

/** Ordered-window feedback supplied by the reserved F13 implementation. */
sealed interface LotteryWindowInput {
    /** Registration opening precedes closing, then booking opens and closes in order. */
    data object Valid : LotteryWindowInput

    /** Registration opening is at or after closing; this check has first precedence. */
    data object InvalidRegistration : LotteryWindowInput

    /** Booking opens before registration closes, after a valid registration window. */
    data object BookingBeforeRegistrationClose : LotteryWindowInput

    /** Booking opening is at or after closing, after the preceding checks passed. */
    data object InvalidBooking : LotteryWindowInput
}

/** F13 consumes registration open/close then booking open/close; null injection means pending. */
typealias LotteryWindowValidator = (Instant, Instant, Instant, Instant) -> LotteryWindowInput

/** Compiling F13 handoff stub; replace its placeholder feedback with the assigned ordered checks.
 * @param registrationOpen Inclusive registration opening.
 * @param registrationClose Exclusive registration closing.
 * @param bookingOpen Inclusive booking opening.
 * @param bookingClose Exclusive booking closing.
 * @return Placeholder failure until the reserved implementation passes its contracts.
 */
@Suppress("UNUSED_PARAMETER")
fun validateLotteryWindows(
    registrationOpen: Instant,
    registrationClose: Instant,
    bookingOpen: Instant,
    bookingClose: Instant,
): LotteryWindowInput =
    LotteryWindowInput.InvalidRegistration

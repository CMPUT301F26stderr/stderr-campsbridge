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

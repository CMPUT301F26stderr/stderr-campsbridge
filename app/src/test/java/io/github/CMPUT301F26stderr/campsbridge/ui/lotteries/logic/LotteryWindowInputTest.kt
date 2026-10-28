package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** F13 contracts for exact endpoints and ordered first-failure feedback without a clock. */
class LotteryWindowInputTest {
    private val origin = Instant.parse("2027-05-01T00:00:00Z")

    /** Separate registration and booking intervals are valid. */
    @Test fun separatedWindowsAreValid() {
        assertEquals(LotteryWindowInput.Valid, result(0, 10, 20, 30))
    }

    /** Booking may open exactly when registration closes. */
    @Test fun adjacentWindowsAreValid() {
        assertEquals(LotteryWindowInput.Valid, result(0, 10, 10, 20))
    }

    /** A registration interval must have positive duration. */
    @Test fun equalRegistrationEndpointsAreInvalid() {
        assertEquals(LotteryWindowInput.InvalidRegistration, result(10, 10, 20, 30))
    }

    /** Registration closing before its opening is rejected. */
    @Test fun reversedRegistrationIsInvalid() {
        assertEquals(LotteryWindowInput.InvalidRegistration, result(20, 10, 30, 40))
    }

    /** Booking cannot overlap an otherwise valid registration interval. */
    @Test fun bookingBeforeRegistrationClosesIsInvalid() {
        assertEquals(LotteryWindowInput.BookingBeforeRegistrationClose, result(0, 10, 9, 20))
    }

    /** A booking interval must also have positive duration. */
    @Test fun equalBookingEndpointsAreInvalid() {
        assertEquals(LotteryWindowInput.InvalidBooking, result(0, 10, 20, 20))
    }

    /** Booking closing before its opening is rejected after prior checks pass. */
    @Test fun reversedBookingIsInvalid() {
        assertEquals(LotteryWindowInput.InvalidBooking, result(0, 10, 30, 20))
    }

    /** Registration failure takes precedence even when both booking conditions also fail. */
    @Test fun invalidRegistrationHasFirstPrecedence() {
        assertEquals(LotteryWindowInput.InvalidRegistration, result(20, 10, 5, 0))
        assertEquals(LotteryWindowInput.InvalidRegistration, result(10, 10, 5, 5))
    }

    /** Early booking takes precedence over the reversed or empty booking interval. */
    @Test fun earlyBookingHasSecondPrecedence() {
        assertEquals(LotteryWindowInput.BookingBeforeRegistrationClose, result(0, 10, 5, 0))
        assertEquals(LotteryWindowInput.BookingBeforeRegistrationClose, result(0, 10, 5, 5))
    }

    /** All equal values fail the first registration check. */
    @Test fun allEqualEndpointsUseRegistrationFeedback() {
        assertEquals(LotteryWindowInput.InvalidRegistration, result(0, 0, 0, 0))
    }

    /** Nanosecond differences must not be rounded to seconds or dates. */
    @Test fun preservesNanosecondBoundaries() {
        val close = origin.plusNanos(2)
        assertEquals(
            LotteryWindowInput.Valid,
            validateLotteryWindows(origin, close, close, close.plusNanos(1)),
        )
        assertEquals(
            LotteryWindowInput.BookingBeforeRegistrationClose,
            validateLotteryWindows(origin, close, close.minusNanos(1), close.plusNanos(1)),
        )
        assertEquals(
            LotteryWindowInput.InvalidBooking,
            validateLotteryWindows(origin, close, close.plusNanos(1), close),
        )
    }

    /** Comparison supports the complete Instant range without epoch-millisecond conversion. */
    @Test fun supportsFullInstantRangeAndPastWindows() {
        assertEquals(
            LotteryWindowInput.Valid,
            validateLotteryWindows(
                Instant.MIN,
                Instant.MIN.plusNanos(1),
                Instant.MAX.minusNanos(1),
                Instant.MAX,
            ),
        )
        assertEquals(LotteryWindowInput.Valid, result(-40, -30, -20, -10))
    }

    /** Supplies exact second-offset fixtures without recreating the assigned ordering rule.
     * @param registrationOpen Registration opening offset.
     * @param registrationClose Registration closing offset.
     * @param bookingOpen Booking opening offset.
     * @param bookingClose Booking closing offset.
     */
    private fun result(
        registrationOpen: Long,
        registrationClose: Long,
        bookingOpen: Long,
        bookingClose: Long,
    ): LotteryWindowInput =
        validateLotteryWindows(
            origin.plusSeconds(registrationOpen),
            origin.plusSeconds(registrationClose),
            origin.plusSeconds(bookingOpen),
            origin.plusSeconds(bookingClose),
        )
}

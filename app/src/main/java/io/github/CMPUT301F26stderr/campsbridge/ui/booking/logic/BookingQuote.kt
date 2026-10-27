package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Exact integer-cents quote outcome, without creating payment or reservation facts. */
sealed interface BookingQuote {
    /** The valid rate/stay product cannot be represented as a nonnegative Long amount. */
    data object Overflow : BookingQuote

    /** Exact amount for the selected local nights at the supplied rate.
     * @param totalCents Derived total; zero is a valid free stay.
     */
    data class Value(
        val totalCents: Long,
    ) : BookingQuote
}

/** Derives local night count and checked total without floating point or elapsed-hour math.
 * @param checkIn Inclusive selected local arrival.
 * @param checkOut Exclusive local departure, strictly after arrival.
 * @param pricePerNightCents Nonnegative current campsite rate in integer CAD cents.
 * @return Exact Value, or Overflow without a wrapped/rounded amount.
 * @throws IllegalArgumentException For a nonpositive stay or negative source rate.
 */
fun bookingQuote(
    checkIn: LocalDate,
    checkOut: LocalDate,
    pricePerNightCents: Long,
): BookingQuote {
    require(checkOut > checkIn)
    require(pricePerNightCents >= 0)
    val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
    return try {
        BookingQuote.Value(Math.multiplyExact(pricePerNightCents, nights))
    } catch (_: ArithmeticException) {
        BookingQuote.Overflow
    }
}

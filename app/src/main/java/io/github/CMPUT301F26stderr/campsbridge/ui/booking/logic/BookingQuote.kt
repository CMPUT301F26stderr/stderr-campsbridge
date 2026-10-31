package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Exact integer CAD cents or overflow feedback, without creating a payment or reservation. */
sealed interface BookingQuote {
    /** The stay's total cannot be represented as a nonnegative Long amount. */
    data object Overflow : BookingQuote

    /** Exact total for the occupied nights at the supplied rate.
     * @param totalCents Derived CAD cents; zero is a valid free stay.
     */
    data class Value(
        val totalCents: Long,
    ) : BookingQuote
}

/** Counts local occupied nights and multiplies CAD cents with checked integer arithmetic.
 * @param checkIn Inclusive local arrival.
 * @param checkOut Exclusive local departure, strictly after arrival.
 * @param pricePerNightCents Nonnegative current rate in integer CAD cents.
 * @return Exact Value, including free stays, or Overflow without wrapping or rounding.
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

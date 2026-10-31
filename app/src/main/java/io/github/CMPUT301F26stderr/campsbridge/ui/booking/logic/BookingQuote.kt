package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import java.time.LocalDate

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

/** Declares the BK1 quote contract before implementing checked arithmetic.
 * @param checkIn Inclusive local arrival.
 * @param checkOut Exclusive local departure, strictly after arrival.
 * @param pricePerNightCents Nonnegative current rate in integer CAD cents.
 * @return Placeholder zero quote until the supplied contracts are implemented.
 */
@Suppress("UNUSED_PARAMETER")
fun bookingQuote(
    checkIn: LocalDate,
    checkOut: LocalDate,
    pricePerNightCents: Long,
): BookingQuote =
    BookingQuote.Value(0)

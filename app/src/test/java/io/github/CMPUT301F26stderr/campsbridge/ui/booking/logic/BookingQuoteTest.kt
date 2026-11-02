package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

/** Checked quote arithmetic preserves exact cents and counts campsite-local nights. */
class BookingQuoteTest {
    private val arrival = LocalDate.of(2027, 5, 14)

    /** Empty/reversed ranges are programming errors after the date-validation boundary. */
    @Test fun rejectsNonpositiveStay() {
        for (end in listOf(arrival, arrival.minusDays(1))) {
            assertThrows(IllegalArgumentException::class.java) { bookingQuote(arrival, end, 6500) }
        }
    }

    /** A malformed negative source rate cannot become a negative or apparently free quote. */
    @Test fun rejectsNegativeSourceRate() {
        assertThrows(IllegalArgumentException::class.java) {
            bookingQuote(arrival, arrival.plusDays(1), -1)
        }
    }

    /** A genuine zero rate remains a valid zero simulated charge. */
    @Test fun freeStayHasAnExactZeroQuote() {
        assertEquals(BookingQuote.Value(0), bookingQuote(arrival, arrival.plusDays(2), 0))
    }

    /** Ordinary and fractional-dollar rates are integer-cent multiplication. */
    @Test fun multipliesWholeCentsExactly() {
        assertEquals(BookingQuote.Value(13000), bookingQuote(arrival, arrival.plusDays(2), 6500))
        assertEquals(BookingQuote.Value(3702), bookingQuote(arrival, arrival.plusDays(3), 1234))
    }

    /** DST/leap/year boundaries still charge one rate for each local occupied date. */
    @Test fun localNightCountDoesNotUseElapsedHours() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Edmonton"))
            for (first in listOf(
                LocalDate.of(2027, 3, 13),
                LocalDate.of(2027, 11, 6),
                LocalDate.of(2028, 2, 28),
                LocalDate.of(2027, 12, 31),
            )) {
                assertEquals(
                    BookingQuote.Value(13000),
                    bookingQuote(first, first.plusDays(2), 6500),
                )
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    /** The maximum representable one-night amount is still exact, without Double rounding. */
    @Test fun maximumSingleNightAmountIsValid() {
        assertEquals(
            BookingQuote.Value(Long.MAX_VALUE),
            bookingQuote(arrival, arrival.plusDays(1), Long.MAX_VALUE),
        )
    }

    /** The last safe two-night total remains valid at the integer boundary. */
    @Test fun largestSafeTwoNightProductIsValid() {
        assertEquals(
            BookingQuote.Value(Long.MAX_VALUE - 1),
            bookingQuote(
                arrival,
                arrival.plusDays(2),
                Long.MAX_VALUE / 2,
            ),
        )
    }

    /** Overflow has an explicit outcome rather than wrapped negative or rounded cents. */
    @Test fun overflowingProductsAreNotQuotes() {
        assertEquals(
            BookingQuote.Overflow,
            bookingQuote(
                arrival,
                arrival.plusDays(2),
                Long.MAX_VALUE / 2 + 1,
            ),
        )
        assertEquals(
            BookingQuote.Overflow,
            bookingQuote(arrival, arrival.plusDays(3), Long.MAX_VALUE),
        )
    }

    /** The full date range uses a Long night count and keeps zero/excessive rates exact. */
    @Test fun fullLocalDateRangeUsesLongNightCountAndCheckedCents() {
        assertEquals(
            BookingQuote.Value(730_484_999_633L),
            bookingQuote(LocalDate.MIN, LocalDate.MAX, 1),
        )
        assertEquals(
            BookingQuote.Value(0),
            bookingQuote(LocalDate.MIN, LocalDate.MAX, 0),
        )
        assertEquals(
            BookingQuote.Overflow,
            bookingQuote(LocalDate.MIN, LocalDate.MAX, Long.MAX_VALUE),
        )
    }
}

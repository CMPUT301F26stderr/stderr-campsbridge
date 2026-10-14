package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/** Owns consecutive local stay dates and booking value boundaries. */
class StayTest {
    /** Occupied dates include arrival and exclude checkout across calendar boundaries. */
    @Test fun stayDatesAndNightCountAgreeAcrossCalendarBoundaries() {
        for (start in listOf("2027-03-13", "2027-11-06", "2028-02-28", "2027-12-31")) {
            val arrival = LocalDate.parse(start)
            val checkout = arrival.plusDays(2)
            assertEquals(listOf(arrival, arrival.plusDays(1)), stayNights(arrival, checkout))
            assertEquals(
                2L,
                CampingFixtures
                    .reservation()
                    .copy(
                        checkInDate = arrival,
                        checkOutDate = checkout,
                    ).nightCount,
            )
        }
        val arrival = CampingFixtures.arrival
        assertEquals(listOf(arrival), stayNights(arrival, arrival.plusDays(1)))
    }

    /** No reservation or booking request can describe an empty or reversed stay. */
    @Test fun rejectsEmptyAndReversedStays() {
        val arrival = CampingFixtures.arrival
        for (checkout in listOf(arrival, arrival.minusDays(1))) {
            assertThrows(IllegalArgumentException::class.java) { stayNights(arrival, checkout) }
            assertThrows(IllegalArgumentException::class.java) {
                CampingFixtures.reservation().copy(checkOutDate = checkout)
            }
            assertThrows(IllegalArgumentException::class.java) {
                CampingFixtures.bookingRequest().copy(checkOutDate = checkout)
            }
        }
    }

    /** Group sizes must be positive; simulated charges may be zero but never negative. */
    @Test fun validatesGuestsAndAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            CampingFixtures.reservation().copy(guestCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampingFixtures.bookingRequest().copy(guestCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CampingFixtures.bookingRequest().copy(expectedTotalCents = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Payment("reservation", -1, CampingFixtures.now)
        }
        assertEquals(0L, Payment("reservation", 0, CampingFixtures.now).amountCents)
    }
}

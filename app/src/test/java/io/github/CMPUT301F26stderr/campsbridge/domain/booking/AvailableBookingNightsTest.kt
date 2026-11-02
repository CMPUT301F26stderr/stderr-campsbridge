package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** F15 intersects actual lottery allocation and free inventory, without stay or time rules. */
class AvailableBookingNightsTest {
    private val arrival = LocalDate.of(2027, 5, 14)

    /** No allocation or inventory can produce an eligible night. */
    @Test fun emptyInputsHaveNoAvailableNights() {
        assertEquals(emptySet<LocalDate>(), availableBookingNights(emptySet(), emptyList()))
    }

    /** Offered dates alone do not establish physical availability. */
    @Test fun absentInventoryCannotBeAssumedFree() {
        assertEquals(emptySet<LocalDate>(), availableBookingNights(setOf(arrival), emptyList()))
    }

    /** Even physically free nights require actual allocation membership. */
    @Test fun freeInventoryWithoutAllocationIsExcluded() {
        assertEquals(
            emptySet<LocalDate>(),
            availableBookingNights(emptySet(), listOf(CalendarNight(arrival, true))),
        )
    }

    /** A single allocated offered unclaimed night is available. */
    @Test fun matchingFreeNightIsIncluded() {
        assertEquals(
            setOf(arrival),
            availableBookingNights(setOf(arrival), listOf(CalendarNight(arrival, true))),
        )
    }

    /** Withdrawing the physical offer excludes an allocated date without a booking claim. */
    @Test fun physicallyUnofferedNightIsExcluded() {
        assertEquals(
            emptySet<LocalDate>(),
            availableBookingNights(setOf(arrival), listOf(CalendarNight(arrival, false))),
        )
    }

    /** Every nonnull reservation claim prevents availability, including an empty-string claim. */
    @Test fun claimedNightsAreExcludedRegardlessOfClaimText() {
        val second = arrival.plusDays(1)
        assertEquals(
            emptySet<LocalDate>(),
            availableBookingNights(
                setOf(arrival, second),
                listOf(CalendarNight(arrival, true, "booking"), CalendarNight(second, true, "")),
            ),
        )
    }

    /** Missing interior or final inventory records are not generated from allocation dates. */
    @Test fun missingAllocatedInventoryIsExcludedIndividually() {
        assertEquals(
            setOf(arrival),
            availableBookingNights(
                setOf(arrival, arrival.plusDays(1), arrival.plusDays(2)),
                listOf(CalendarNight(arrival, true)),
            ),
        )
    }

    /** Mixed physical statuses are filtered using both authoritative inputs. */
    @Test fun mixedInventoryReturnsOnlyAllocatedFreeDates() {
        val withdrawn = arrival.plusDays(1)
        val booked = arrival.plusDays(2)
        val outside = arrival.plusDays(3)
        assertEquals(
            setOf(arrival),
            availableBookingNights(
                setOf(arrival, withdrawn, booked),
                listOf(
                    CalendarNight(outside, true),
                    CalendarNight(booked, true, "another"),
                    CalendarNight(withdrawn, false),
                    CalendarNight(arrival, true),
                ),
            ),
        )
    }

    /** Eligibility contains occupied nights only; the parent permits an excluded checkout. */
    @Test fun adjacentFreeCheckoutOutsideAllocationIsNotIncluded() {
        val second = arrival.plusDays(1)
        val checkout = arrival.plusDays(2)
        assertEquals(
            setOf(arrival, second),
            availableBookingNights(
                setOf(arrival, second),
                listOf(
                    CalendarNight(arrival, true),
                    CalendarNight(second, true),
                    CalendarNight(checkout, true),
                ),
            ),
        )
    }

    /** Past-date rejection belongs to the parent, so this deterministic helper has no clock. */
    @Test fun historicalFreeDatesAreStillReturned() {
        val historical = LocalDate.of(1970, 1, 1)
        assertEquals(
            setOf(historical),
            availableBookingNights(setOf(historical), listOf(CalendarNight(historical, true))),
        )
    }

    /** Set membership remains exact across month/year boundaries and unsorted input. */
    @Test fun inputOrderingAndYearBoundaryDoNotChangeTheSet() {
        val december = LocalDate.of(2027, 12, 31)
        val january = december.plusDays(1)
        assertEquals(
            setOf(december, january),
            availableBookingNights(
                linkedSetOf(january, december),
                listOf(
                    CalendarNight(december.minusDays(1), true),
                    CalendarNight(december, true),
                    CalendarNight(january, true),
                ),
            ),
        )
    }

    /** Neither collection nor its original values may be changed to produce the result. */
    @Test fun leavesCallerOwnedCollectionsUnchanged() {
        val outside = arrival.plusDays(1)
        val offered = linkedSetOf(arrival)
        val calendar = mutableListOf(CalendarNight(outside, true), CalendarNight(arrival, true))
        val originalOffered = offered.toSet()
        val originalCalendar = calendar.toList()
        val result = availableBookingNights(offered, calendar)
        assertEquals(originalOffered, offered)
        assertEquals(originalCalendar, calendar)
        assertEquals(setOf(arrival), result)
    }
}

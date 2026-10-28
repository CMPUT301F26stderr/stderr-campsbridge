package io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** User feedback precedence is distinct from repository/request invariant enforcement. */
class StaySelectionInputTest {
    private val today = LocalDate.of(2027, 5, 1)
    private val arrival = today.plusDays(1)

    /** Missing arrival precedes other invalid or missing endpoint/source facts. */
    @Test fun missingCheckInComesFirst() {
        assertEquals(
            StaySelectionInput.MISSING_CHECK_IN,
            validateStaySelection(null, null, emptySet(), today),
        )
        assertEquals(
            StaySelectionInput.MISSING_CHECK_IN,
            validateStaySelection(null, today.minusDays(1), emptySet(), today),
        )
    }

    /** Missing departure precedes an arrival that is already in the past. */
    @Test fun missingCheckOutPrecedesPastArrival() {
        assertEquals(
            StaySelectionInput.MISSING_CHECK_OUT,
            validateStaySelection(today.minusDays(1), null, emptySet(), today),
        )
    }

    /** An empty stay is invalid before a past-date or unavailable-night error is considered. */
    @Test fun emptyRangePrecedesOtherDateErrors() {
        val yesterday = today.minusDays(1)
        assertEquals(
            StaySelectionInput.INVALID_RANGE,
            validateStaySelection(yesterday, yesterday, emptySet(), today),
        )
    }

    /** Reversed dates have the same range feedback, without iterating an invalid interval. */
    @Test fun reversedRangePrecedesAvailabilityErrors() {
        assertEquals(
            StaySelectionInput.INVALID_RANGE,
            validateStaySelection(arrival, today, emptySet(), today),
        )
    }

    /** A past check-in takes precedence even when its nights are also unavailable. */
    @Test fun pastArrivalPrecedesUnavailableNights() {
        assertEquals(
            StaySelectionInput.PAST_CHECK_IN,
            validateStaySelection(today.minusDays(1), today.plusDays(1), emptySet(), today),
        )
    }

    /** Arrival exactly today is valid when that occupied night is eligible. */
    @Test fun currentLocalDayIsNotPast() {
        assertEquals(
            StaySelectionInput.VALID,
            validateStaySelection(today, today.plusDays(1), setOf(today), today),
        )
    }

    /** Checkout is excluded, so it need not itself be offered/free. */
    @Test fun oneNightDoesNotRequireEligibleCheckout() {
        assertEquals(
            StaySelectionInput.VALID,
            validateStaySelection(arrival, arrival.plusDays(1), setOf(arrival), today),
        )
    }

    /** Eligibility must include the first occupied night. */
    @Test fun missingCheckInNightIsUnavailable() {
        assertEquals(
            StaySelectionInput.UNAVAILABLE_NIGHTS,
            validateStaySelection(arrival, arrival.plusDays(2), setOf(arrival.plusDays(1)), today),
        )
    }

    /** Eligible endpoints cannot hide an unavailable interior occupied night. */
    @Test fun missingInteriorNightIsUnavailable() {
        assertEquals(
            StaySelectionInput.UNAVAILABLE_NIGHTS,
            validateStaySelection(
                arrival,
                arrival.plusDays(3),
                setOf(arrival, arrival.plusDays(2)),
                today,
            ),
        )
    }

    /** The night just before checkout is occupied and must be eligible. */
    @Test fun missingLastOccupiedNightIsUnavailable() {
        assertEquals(
            StaySelectionInput.UNAVAILABLE_NIGHTS,
            validateStaySelection(arrival, arrival.plusDays(2), setOf(arrival), today),
        )
    }

    /** Consecutive local dates cross leap/month/year boundaries without UTC-hour assumptions. */
    @Test fun consecutiveStaysCanCrossCalendarBoundaries() {
        for (first in listOf(
            LocalDate.of(2028, 2, 28),
            LocalDate.of(2027, 12, 31),
            LocalDate.of(2027, 3, 13),
        )) {
            assertEquals(
                StaySelectionInput.VALID,
                validateStaySelection(
                    first,
                    first.plusDays(2),
                    setOf(first, first.plusDays(1)),
                    first,
                ),
            )
        }
    }

    /** Extra eligible dates outside the chosen range cannot fill a missing occupied date. */
    @Test fun unrelatedDatesCannotSatisfyASelectionGap() {
        assertEquals(
            StaySelectionInput.UNAVAILABLE_NIGHTS,
            validateStaySelection(
                arrival,
                arrival.plusDays(2),
                setOf(arrival, arrival.minusDays(1), arrival.plusDays(2)),
                today,
            ),
        )
    }

    /** Validation is read-only and retains irrelevant input dates as well. */
    @Test fun preservesCallerOwnedEligibleSet() {
        val nights =
            mutableSetOf(arrival.minusDays(1), arrival, arrival.plusDays(1), arrival.plusDays(2))
        val original = nights.toSet()
        assertEquals(
            StaySelectionInput.VALID,
            validateStaySelection(arrival, arrival.plusDays(2), nights, today),
        )
        assertEquals(original, nights)
    }
}

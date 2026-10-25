package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Optional working criteria validate before replacing the applied filter. */
class DiscoveryFiltersTest {
    private val arrival = LocalDate.of(2027, 5, 14)

    /** Blank optional fields apply no stay or guest restriction. */
    @Test fun optionalFieldsStayAbsentAndLocationTrims() {
        assertEquals(
            FilterInput.Valid(DiscoveryFilters("Jasper")),
            validateDiscoveryFilters(FilterDraft(locationText = " Jasper ")),
        )
    }

    /** Both endpoints are required together; equal/reversed dates have separate feedback. */
    @Test fun endpointsArePairedAndCheckoutIsStrictlyLater() {
        for (draft in listOf(FilterDraft(checkIn = arrival), FilterDraft(checkOut = arrival))) {
            assertEquals(
                FilterInput.Invalid(FilterErrors(dates = StayFilterError.MISSING_ENDPOINT)),
                validateDiscoveryFilters(draft),
            )
        }
        for (checkout in listOf(arrival, arrival.minusDays(1))) {
            assertEquals(
                FilterInput.Invalid(FilterErrors(dates = StayFilterError.INVALID_ORDER)),
                validateDiscoveryFilters(FilterDraft(checkIn = arrival, checkOut = checkout)),
            )
        }
    }

    /** Reuses the positive-count contract and reports all invalid fields without applying them. */
    @Test fun invalidGuestAndStayFeedbackCanCoexist() {
        for (count in listOf("0", "-1", "x", "9223372036854775808")) {
            assertEquals(
                FilterInput.Invalid(FilterErrors(StayFilterError.MISSING_ENDPOINT, true)),
                validateDiscoveryFilters(FilterDraft(checkIn = arrival, guestText = count)),
            )
        }
    }

    /** A complete stay retains exact local endpoints and the full supported count range. */
    @Test fun validStayAndCountBecomeTypedAppliedValues() {
        assertEquals(
            FilterInput.Valid(
                DiscoveryFilters("Jasper", arrival, arrival.plusDays(2), Long.MAX_VALUE),
            ),
            validateDiscoveryFilters(
                FilterDraft(" Jasper ", arrival, arrival.plusDays(2), " 9223372036854775807 "),
            ),
        )
    }
}

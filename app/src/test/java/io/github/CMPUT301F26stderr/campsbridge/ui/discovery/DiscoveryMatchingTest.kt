package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Lead-owned location/date semantics; reserved general text and capacity algorithms are separate. */
class DiscoveryMatchingTest {
    private val arrival = LocalDate.of(2027, 5, 14)

    /** Location substring matching is trimmed and independent of the device locale. */
    @Test fun locationUsesHumanLabelAndRootCaseFolding() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(matchesDiscoveryLocation("PINE Jasper", " pine "))
            assertTrue(matchesDiscoveryLocation("Jasper", " "))
            assertFalse(matchesDiscoveryLocation("Banff", "Jasper"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** Missing/unoffered/claimed occupied nights exclude a stay; checkout is never occupied. */
    @Test fun everyOccupiedNightMustBeKnownFree() {
        val first = CalendarNight(arrival, true)
        val second = CalendarNight(arrival.plusDays(1), true)
        val checkout = arrival.plusDays(2)
        assertTrue(
            hasAvailableStay(
                listOf(first, second, CalendarNight(checkout, false, "other")),
                arrival,
                checkout,
            ),
        )
        assertFalse(hasAvailableStay(listOf(first), arrival, checkout))
        assertFalse(
            hasAvailableStay(listOf(first, second.copy(isOffered = false)), arrival, checkout),
        )
        assertFalse(
            hasAvailableStay(
                listOf(first, second.copy(reservationId = "booked")),
                arrival,
                checkout,
            ),
        )
    }
}

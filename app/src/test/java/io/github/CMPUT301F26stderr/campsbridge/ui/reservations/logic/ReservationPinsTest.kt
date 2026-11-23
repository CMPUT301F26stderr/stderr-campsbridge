/*
 * Checks how saved reservations become reservation-map pins.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pin derivation for WOW 1: one pin per booked campsite, active and past stays only. */
class ReservationPinsTest {
    private val base = CampingFixtures.reservation()
    private val lakeSite =
        base.bookedCampsite.copy(
            name = "Lake Camp",
            locationLabel = "Banff",
            location = Coordinates(51.1784, -115.5708),
        )

    /** Stays at one campsite share a pin; pins keep the order the list shows them in. */
    @Test fun staysAtTheSameCampsiteShareOnePinInListOrder() {
        val first = base.copy(id = "first")
        val lake = base.copy(id = "lake", campsiteId = "lake-site", bookedCampsite = lakeSite)
        val second = base.copy(id = "second")
        val pins =
            reservationPins(
                listOf(
                    ReservationListItem(first, ReservationCategory.UPCOMING),
                    ReservationListItem(lake, ReservationCategory.PAST),
                    ReservationListItem(second, ReservationCategory.PAST),
                ),
            )
        assertEquals(listOf("site", "lake-site"), pins.map { it.campsiteId })
        assertEquals(listOf("first", "second"), pins[0].reservationIds)
        assertEquals("River Camp", pins[0].name)
        assertEquals(base.bookedCampsite.location, pins[0].location)
        assertEquals(listOf("lake"), pins[1].reservationIds)
        assertEquals("Banff", pins[1].locationLabel)
    }

    /** Cancelled stays never become pins, and an empty list gives an empty map. */
    @Test fun cancelledStaysAreLeftOffTheMap() {
        val kept = base.copy(id = "kept")
        val cancelled = base.copy(id = "cancelled", campsiteId = "lake-site", bookedCampsite = lakeSite)
        val pins =
            reservationPins(
                listOf(
                    ReservationListItem(kept, ReservationCategory.IN_PROGRESS),
                    ReservationListItem(cancelled, ReservationCategory.CANCELLED),
                ),
            )
        assertEquals(listOf(listOf("kept")), pins.map { it.reservationIds })
        assertTrue(reservationPins(emptyList()).isEmpty())
    }
}

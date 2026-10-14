package io.github.CMPUT301F26stderr.campsbridge.domain.campsite

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DateTimeException

/** Owns listing validity and derived inventory visibility. */
class CampsiteTest {
    /** Listing details require a name, usable capacity, nonnegative price and valid timezone. */
    @Test fun validatesEditableDetails() {
        val details = CampingFixtures.campsite().details
        assertEquals(0L, details.copy(nightlyPriceCents = 0).nightlyPriceCents)
        assertThrows(IllegalArgumentException::class.java) { details.copy(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { details.copy(maxGuests = 0) }
        assertThrows(IllegalArgumentException::class.java) { details.copy(nightlyPriceCents = -1) }
        assertThrows(DateTimeException::class.java) { details.copy(timeZone = "Mars/Olympus") }
    }

    /** Discovery visibility requires both publication and an active listing. */
    @Test fun publicationAndRemovalDetermineVisibility() {
        val campsite = CampingFixtures.campsite()
        assertTrue(campsite.isPublished)
        assertFalse(campsite.copy(publishedAt = null).isPublished)
        assertFalse(campsite.copy(removal = Removal(CampingFixtures.now, "owner")).isPublished)
    }

    /** A night is free only when offered and unclaimed. */
    @Test fun availabilityCombinesOfferAndClaim() {
        for (offered in listOf(false, true)) {
            for (claim in listOf(null, "reservation")) {
                assertEquals(
                    offered && claim == null,
                    CalendarNight(CampingFixtures.arrival, offered, claim).isFree,
                )
            }
        }
    }
}

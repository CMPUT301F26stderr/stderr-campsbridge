package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10 contracts for optional counts, inclusive bounds and precondition precedence. */
class GuestCapacityMatchTest {
    /** An absent guest constraint matches every valid capacity. */
    @Test fun absentRequestMatches() {
        for (maximum in listOf(1L, 8L, Long.MAX_VALUE)) {
            assertTrue(matchesGuestCapacity(maximum, null))
        }
    }

    /** One guest is accepted at the smallest valid capacity and larger capacities. */
    @Test fun minimumRequestMatches() {
        assertTrue(matchesGuestCapacity(1, 1))
        assertTrue(matchesGuestCapacity(8, 1))
    }

    /** A request below capacity remains eligible. */
    @Test fun requestBelowCapacityMatches() {
        assertTrue(matchesGuestCapacity(8, 3))
    }

    /** Capacity is inclusive, so a group may fill the campsite. */
    @Test fun requestAtCapacityMatches() {
        assertTrue(matchesGuestCapacity(8, 8))
    }

    /** A group larger than capacity is excluded. */
    @Test fun requestAboveCapacityDoesNotMatch() {
        assertFalse(matchesGuestCapacity(1, 2))
        assertFalse(matchesGuestCapacity(8, 9))
    }

    /** Nonpositive requests are invalid filters rather than matches. */
    @Test fun nonpositiveRequestDoesNotMatch() {
        for (request in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertFalse(matchesGuestCapacity(8, request))
        }
    }

    /** The invalid maximum is rejected before any null or invalid request shortcut. */
    @Test fun invalidMaximumAlwaysThrowsFirst() {
        for (maximum in listOf(0L, -1L, Long.MIN_VALUE)) {
            for (request in listOf(null, -1L, 1L)) {
                assertThrows(IllegalArgumentException::class.java) {
                    matchesGuestCapacity(maximum, request)
                }
            }
        }
    }

    /** Long-sized counts keep their exact inclusive ordering without narrowing or overflow. */
    @Test fun supportsTheFullPositiveLongRange() {
        assertTrue(matchesGuestCapacity(Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(matchesGuestCapacity(Long.MAX_VALUE, Long.MAX_VALUE - 1))
        assertFalse(matchesGuestCapacity(Long.MAX_VALUE - 1, Long.MAX_VALUE))
    }
}

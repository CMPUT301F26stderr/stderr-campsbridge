package io.github.CMPUT301F26stderr.campsbridge.domain.lottery

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owns lottery configuration, temporal boundaries and offer validity. */
class LotteryTest {
    /** Registration and booking windows are ordered, with adjacent windows permitted. */
    @Test fun validatesWindowOrderAndLimits() {
        val config = CampingFixtures.lottery().configuration
        config.copy(waitlistCapacity = 1)
        for (invalid in listOf<() -> LotteryConfiguration>(
            { config.copy(registrationClosesAt = config.registrationOpensAt) },
            { config.copy(bookingOpensAt = config.registrationClosesAt.minusNanos(1)) },
            { config.copy(bookingClosesAt = config.bookingOpensAt) },
            { config.copy(winnerCount = 0) },
            { config.copy(waitlistCapacity = 0) },
            { config.copy(waitlistCapacity = -1) },
        )) {
            assertThrows(IllegalArgumentException::class.java) { invalid() }
        }
    }

    /** Registration includes its opening instant and excludes its deadline. */
    @Test fun registrationHasInclusiveStartAndExclusiveEnd() {
        val lottery = CampingFixtures.lottery()
        val config = lottery.configuration
        assertFalse(lottery.registrationIsOpen(config.registrationOpensAt.minusNanos(1)))
        assertTrue(lottery.registrationIsOpen(config.registrationOpensAt))
        assertTrue(lottery.registrationIsOpen(config.registrationClosesAt.minusNanos(1)))
        assertFalse(lottery.registrationIsOpen(config.registrationClosesAt))
    }

    /** Booking includes its opening instant and excludes its deadline. */
    @Test fun bookingHasInclusiveStartAndExclusiveEnd() {
        val lottery = CampingFixtures.lottery().copy(drawnAt = CampingFixtures.now)
        val config = lottery.configuration
        assertFalse(lottery.bookingIsOpen(config.bookingOpensAt.minusNanos(1)))
        assertTrue(lottery.bookingIsOpen(config.bookingOpensAt))
        assertTrue(lottery.bookingIsOpen(config.bookingClosesAt.minusNanos(1)))
        assertFalse(lottery.bookingIsOpen(config.bookingClosesAt))
    }

    /** Publication, cancellation and draw completion gate the temporal windows. */
    @Test fun lifecycleGatesParticipationAndBooking() {
        val lottery = CampingFixtures.lottery()
        val registrationTime = lottery.configuration.registrationOpensAt
        val bookingTime = lottery.configuration.bookingOpensAt
        assertFalse(lottery.copy(publishedAt = null).registrationIsOpen(registrationTime))
        assertFalse(
            lottery.copy(drawnAt = CampingFixtures.now).registrationIsOpen(registrationTime),
        )
        assertFalse(lottery.bookingIsOpen(bookingTime))
        val drawn = lottery.copy(drawnAt = CampingFixtures.now)
        assertFalse(drawn.copy(publishedAt = null).bookingIsOpen(bookingTime))
        val cancelled = lottery.copy(cancellation = Cancellation(CampingFixtures.now, "owner"))
        assertFalse(cancelled.registrationIsOpen(registrationTime))
        assertFalse(cancelled.copy(drawnAt = CampingFixtures.now).bookingIsOpen(bookingTime))
    }

    /** Offers allow gaps, but never empty, duplicated or unsorted dates. */
    @Test fun offersRequireOrderedUniqueNights() {
        val date = CampingFixtures.arrival
        assertEquals(
            listOf(date, date.plusDays(3)),
            LotteryOffer("site", listOf(date, date.plusDays(3))).nights,
        )
        for (nights in listOf(emptyList(), listOf(date, date), listOf(date.plusDays(1), date))) {
            assertThrows(IllegalArgumentException::class.java) { LotteryOffer("site", nights) }
        }
    }
}

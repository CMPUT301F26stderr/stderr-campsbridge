package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Exact saved-parent boundaries precede list/detail lifecycle presentation. */
class LotteryLifecycleTest {
    private val lottery = CampingFixtures.lottery()
    private val config = lottery.configuration

    /** An unpublished record remains a draft regardless of its configured deadlines. */
    @Test fun unpublishedTargetsRemainDrafts() {
        val draft = lottery.copy(publishedAt = null)
        assertEquals(LotteryLifecycle.DRAFT, lotteryLifecycle(draft, Instant.MIN))
        assertEquals(LotteryLifecycle.DRAFT, lotteryLifecycle(draft, Instant.MAX))
    }

    /** Cancellation takes precedence over publication, timing and completed draw facts. */
    @Test fun cancellationAlwaysTakesPrecedence() {
        val cancelled =
            lottery.copy(
                cancellation = Cancellation(CampingFixtures.now, "owner", null),
            )
        for (record in listOf(
            cancelled,
            cancelled.copy(publishedAt = null),
            cancelled.copy(drawnAt = config.registrationClosesAt),
        )) {
            assertEquals(LotteryLifecycle.CANCELLED, lotteryLifecycle(record, Instant.MIN))
            assertEquals(LotteryLifecycle.CANCELLED, lotteryLifecycle(record, Instant.MAX))
        }
    }

    /** Registration opens inclusively and closes exclusively, with no duration rounding. */
    @Test fun registrationBoundariesUseExactInstants() {
        assertEquals(
            LotteryLifecycle.UPCOMING,
            lotteryLifecycle(lottery, config.registrationOpensAt.minusNanos(1)),
        )
        assertEquals(
            LotteryLifecycle.REGISTRATION_OPEN,
            lotteryLifecycle(lottery, config.registrationOpensAt),
        )
        assertEquals(
            LotteryLifecycle.REGISTRATION_OPEN,
            lotteryLifecycle(lottery, config.registrationClosesAt.minusNanos(1)),
        )
        assertEquals(
            LotteryLifecycle.DRAW_PENDING,
            lotteryLifecycle(lottery, config.registrationClosesAt),
        )
    }

    /** Passing all booking deadlines cannot fabricate a completed draw or final outcomes. */
    @Test fun anUndrawnLotteryRemainsPendingAfterEveryDeadline() {
        assertEquals(
            LotteryLifecycle.DRAW_PENDING,
            lotteryLifecycle(lottery, config.bookingOpensAt),
        )
        assertEquals(
            LotteryLifecycle.DRAW_PENDING,
            lotteryLifecycle(lottery, config.bookingClosesAt),
        )
        assertEquals(LotteryLifecycle.DRAW_PENDING, lotteryLifecycle(lottery, Instant.MAX))
    }

    /** Completed draws can await a later booking opening; equality opens booking immediately. */
    @Test fun completedDrawWaitsForTheInclusiveBookingOpening() {
        val later =
            lottery.copy(
                configuration =
                    config.copy(bookingOpensAt = config.registrationClosesAt.plusSeconds(30)),
                drawnAt = config.registrationClosesAt,
            )
        assertEquals(
            LotteryLifecycle.AWAITING_BOOKING,
            lotteryLifecycle(later, config.registrationClosesAt),
        )
        assertEquals(
            LotteryLifecycle.AWAITING_BOOKING,
            lotteryLifecycle(later, later.configuration.bookingOpensAt.minusNanos(1)),
        )
        assertEquals(
            LotteryLifecycle.BOOKING_OPEN,
            lotteryLifecycle(later, later.configuration.bookingOpensAt),
        )
        assertEquals(
            LotteryLifecycle.BOOKING_OPEN,
            lotteryLifecycle(
                lottery.copy(drawnAt = config.registrationClosesAt),
                config.bookingOpensAt,
            ),
        )
    }

    /** Booking's exclusive closing boundary produces closed only after the draw is complete. */
    @Test fun completedDrawClosesAtTheBookingDeadline() {
        val drawn = lottery.copy(drawnAt = config.registrationClosesAt)
        assertEquals(
            LotteryLifecycle.BOOKING_OPEN,
            lotteryLifecycle(drawn, config.bookingClosesAt.minusNanos(1)),
        )
        assertEquals(LotteryLifecycle.CLOSED, lotteryLifecycle(drawn, config.bookingClosesAt))
        assertEquals(LotteryLifecycle.CLOSED, lotteryLifecycle(drawn, Instant.MAX))
    }

    /**
     * A clock rollback cannot make an already-completed draw accept another round of
     * registration.
     */
    @Test fun completedDrawFactsSurviveAnEarlierEvaluationClock() {
        val drawn = lottery.copy(drawnAt = config.registrationClosesAt)
        assertEquals(
            LotteryLifecycle.AWAITING_BOOKING,
            lotteryLifecycle(drawn, config.registrationOpensAt),
        )
    }
}

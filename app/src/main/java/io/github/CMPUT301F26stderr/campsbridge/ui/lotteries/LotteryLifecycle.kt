package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import java.time.Instant

/** Derived display phases; publication, draw completion and windows remain authoritative. */
enum class LotteryLifecycle {
    DRAFT,
    UPCOMING,
    REGISTRATION_OPEN,
    DRAW_PENDING,
    AWAITING_BOOKING,
    BOOKING_OPEN,
    CLOSED,
    CANCELLED,
}

/** Derives display state without guessing draw completion from individual entrant outcomes.
 * @param lottery Current saved parent.
 * @param now Explicit evaluation time; device time zones do not change instant boundaries.
 */
fun lotteryLifecycle(
    lottery: Lottery,
    now: Instant,
): LotteryLifecycle =
    when {
        lottery.cancellation != null -> {
            LotteryLifecycle.CANCELLED
        }

        lottery.publishedAt == null -> {
            LotteryLifecycle.DRAFT
        }

        lottery.drawnAt == null -> {
            when {
                now < lottery.configuration.registrationOpensAt -> LotteryLifecycle.UPCOMING
                lottery.registrationIsOpen(now) -> LotteryLifecycle.REGISTRATION_OPEN
                else -> LotteryLifecycle.DRAW_PENDING
            }
        }

        now < lottery.configuration.bookingOpensAt -> {
            LotteryLifecycle.AWAITING_BOOKING
        }

        lottery.bookingIsOpen(now) -> {
            LotteryLifecycle.BOOKING_OPEN
        }

        else -> {
            LotteryLifecycle.CLOSED
        }
    }

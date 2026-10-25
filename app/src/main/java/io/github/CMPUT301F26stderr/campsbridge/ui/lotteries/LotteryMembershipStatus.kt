package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import java.time.Instant

/** Derives labels shared by entrant detail and owner rows without exposing partial outcomes.
 * @param lottery Current confirmed parent whose completion gates winner/loser labels.
 * @param entry Current membership or confirmed absence.
 * @param now Shared injected display time.
 */
internal fun lotteryMembershipStatus(
    lottery: Lottery,
    entry: LotteryEntry?,
    now: Instant,
): LotteryMembershipStatus {
    val current = entry ?: return LotteryMembershipStatus.NOT_JOINED
    return when (current.state) {
        EntryState.WITHDRAWN -> {
            LotteryMembershipStatus.WITHDRAWN
        }

        EntryState.DECLINED -> {
            LotteryMembershipStatus.DECLINED
        }

        else -> {
            when {
                lottery.drawnAt == null -> {
                    if (current.state == EntryState.WAITING &&
                        now < lottery.configuration.registrationClosesAt
                    ) {
                        LotteryMembershipStatus.WAITING
                    } else {
                        LotteryMembershipStatus.RESULTS_PENDING
                    }
                }

                current.state == EntryState.OFFERED -> {
                    LotteryMembershipStatus.WON
                }

                current.state == EntryState.LOST -> {
                    LotteryMembershipStatus.LOST
                }

                else -> {
                    LotteryMembershipStatus.RESULTS_PENDING
                }
            }
        }
    }
}

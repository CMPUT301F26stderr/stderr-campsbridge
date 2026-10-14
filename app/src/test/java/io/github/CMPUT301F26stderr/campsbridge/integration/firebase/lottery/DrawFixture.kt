package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed

/**
 * Frozen membership after registration closes, before any outcome is applied.
 *
 * @param owner Active draw operator.
 * @param lottery Published lottery due for drawing.
 * @param entrants Authenticated waiting entrants.
 */
data class DrawFixture(
    val owner: EmulatorActor,
    val lottery: Lottery,
    val entrants: List<EmulatorActor>,
)

/**
 * Seeds due membership directly so draw tests do not depend on participation behavior.
 *
 * @param entrants Number of waiting entrants.
 * @param winners Maximum selected entrants.
 */
suspend fun FirebaseEmulatorRule.drawFixture(
    entrants: Int = 3,
    winners: Long = 2,
): DrawFixture {
    val registration = registrationFixture()
    val now = clock.instant()
    val lottery =
        registration.lottery.copy(
            configuration =
                LotteryConfiguration(
                    now.minusSeconds(7200),
                    now.minusSeconds(3600),
                    now.minusSeconds(3600),
                    now.plusSeconds(3600),
                    winners,
                    null,
                ),
            waitingCount = entrants.toLong(),
        )
    seed("lotteries/draw", LotteryDocument.encode(lottery) + ("_revision" to 0L))
    val accounts = List(entrants) { registeredActor(Role.ENTRANT) }
    for (actor in accounts) {
        seed(
            "lotteries/draw/entries/${actor.id}",
            LotteryEntryDocument.encode(
                LotteryEntry(
                    "draw",
                    actor.id,
                    EntryState.WAITING,
                    now.minusSeconds(7200),
                    now.minusSeconds(7200),
                ),
            ),
        )
    }
    return DrawFixture(registration.owner, lottery, accounts)
}

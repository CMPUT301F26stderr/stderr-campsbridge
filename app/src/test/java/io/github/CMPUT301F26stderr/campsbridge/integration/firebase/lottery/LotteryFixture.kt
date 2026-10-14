package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures

/**
 * Published registration state without membership; draws/booking are arranged separately.
 *
 * @param owner Active lottery owner.
 * @param lottery Published record in a server-valid registration window.
 */
data class LotteryFixture(
    val owner: EmulatorActor,
    val lottery: Lottery,
)

/**
 * Seeds an open registration window without performing any participation operations.
 *
 * @param capacity Waiting-list limit, or null for unlimited registration.
 */
suspend fun FirebaseEmulatorRule.registrationFixture(capacity: Long? = null): LotteryFixture {
    val owner = registeredActor(Role.OWNER)
    val now = clock.instant()
    val lottery =
        CampingFixtures.lottery(ownerId = owner.id).copy(
            configuration =
                LotteryConfiguration(
                    now.minusSeconds(3600),
                    now.plusSeconds(3600),
                    now.plusSeconds(3600),
                    now.plusSeconds(7200),
                    2,
                    capacity,
                ),
            createdAt = now.minusSeconds(7200),
            publishedAt = now.minusSeconds(7200),
        )
    seed("lotteries/draw", LotteryDocument.encode(lottery) + ("_revision" to 0L))
    return LotteryFixture(owner, lottery)
}

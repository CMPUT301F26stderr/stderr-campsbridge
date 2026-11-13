/*
 * Shares one lottery between two campsite owners using a single scenario clock.
 */

package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import java.time.LocalDate
import java.time.ZoneId

/** Server-valid windows used by the shared-owner contracts. */
enum class SharedLotteryPhase { DRAFT, UPCOMING, OPEN, DUE }

/** Related people, inventory and saved offers for one isolated scenario.
 * @param organizer A, who owns a1 and a2.
 * @param member B, who owns b1 and b2.
 * @param outsider C, an owner outside this lottery.
 * @param entrants Two distinct entrants.
 * @param admin Administrator inspecting saved outcomes.
 * @param lottery Shared lottery in the requested phase.
 * @param nights Four offered local dates anchored to the scenario clock.
 * @param offers Original A1, A2 and B1 allocations.
 */
data class SharedLotteryFixture(
    val organizer: EmulatorActor,
    val member: EmulatorActor,
    val outsider: EmulatorActor,
    val entrants: List<EmulatorActor>,
    val admin: EmulatorActor,
    val lottery: Lottery,
    val nights: List<LocalDate>,
    val offers: List<LotteryOffer>,
)

/** Seeds current-schema records without invoking the operations under test.
 * @param phase Requested registration/publication phase.
 */
suspend fun FirebaseEmulatorRule.sharedLotteryFixture(
    phase: SharedLotteryPhase = SharedLotteryPhase.DRAFT,
): SharedLotteryFixture {
    val organizer = registeredActor(Role.OWNER)
    val member = registeredActor(Role.OWNER)
    val outsider = registeredActor(Role.OWNER)
    val entrants = List(2) { registeredActor(Role.ENTRANT) }
    val admin = registeredActor(Role.ADMIN)
    val now = clock.instant()
    val arrival = LocalDate.ofInstant(now, ZoneId.of("America/Edmonton")).plusDays(14)
    val nights = (0L..3L).map(arrival::plusDays)
    val hasEntries = phase == SharedLotteryPhase.OPEN || phase == SharedLotteryPhase.DUE
    val opens = if (hasEntries) now.minusSeconds(3600) else now.plusSeconds(300)
    val closes = if (phase == SharedLotteryPhase.DUE) now.minusSeconds(60) else now.plusSeconds(900)
    val lottery =
        CampingFixtures.lottery("shared", organizer.id).copy(
            configuration = LotteryConfiguration(opens, closes, closes, now.plusSeconds(3600), 1, null),
            waitingCount = if (hasEntries) entrants.size.toLong() else 0L,
            createdAt = now.minusSeconds(10800),
            publishedAt = if (phase == SharedLotteryPhase.DRAFT) null else now.minusSeconds(7200),
            participantOwnerIds = setOf(organizer.id, member.id),
        )
    for ((id, owner) in listOf("a1" to organizer, "a2" to organizer, "b1" to member, "b2" to member)) {
        seed(
            "campsites/$id",
            CampsiteDocument.encode(
                CampingFixtures
                    .campsite(
                        id,
                        owner.id,
                    ).copy(createdAt = now.minusSeconds(10800), publishedAt = now.minusSeconds(7200)),
            ),
        )
        for (night in nights) {
            seed(
                "campsites/$id/calendar/$night",
                CalendarNightDocument.encode(CalendarNight(night, true)),
            )
        }
    }
    // This field is handwritten so the fixture does not depend on the new encoder working.
    seed(
        "lotteries/shared",
        LotteryDocument.encode(lottery) +
            mapOf(
                "participantOwnerIds" to listOf(organizer.id, member.id),
                "_revision" to 0L,
            ),
    )
    val offers =
        listOf(
            LotteryOffer("a1", listOf(nights[0])),
            LotteryOffer("a2", listOf(nights[1])),
            LotteryOffer("b1", listOf(nights[0])),
        )
    for (offer in offers) seed("lotteries/shared/offers/${offer.campsiteId}", LotteryOfferDocument.encode(offer))
    if (hasEntries) {
        for (entrant in entrants) {
            seed(
                "lotteries/shared/entries/${entrant.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "shared",
                        entrant.id,
                        EntryState.WAITING,
                        opens.plusSeconds(30),
                        opens.plusSeconds(30),
                    ),
                ),
            )
        }
    }
    return SharedLotteryFixture(organizer, member, outsider, entrants, admin, lottery, nights, offers)
}

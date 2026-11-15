/*
 * A winning entrant and three free offered nights; arranging this state never executes a draw.
 */

package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking

import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
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

/**
 * A winning entrant and three free offered nights; arranging this state never executes a draw.
 *
 * @param owner Actual listing owner and conversation participant.
 * @param entrant Entrant holding the unused winning opportunity.
 * @param campsite Published listing.
 * @param lottery Drawn lottery in its booking window.
 * @param request Matching two-night quote.
 * @param organizer Lottery organizer, who may differ from the booked campsite owner.
 */
data class BookingFixture(
    val owner: EmulatorActor,
    val entrant: EmulatorActor,
    val campsite: Campsite,
    val lottery: Lottery,
    val request: BookingRequest,
    val organizer: EmulatorActor = owner,
)

/** Seeds a booking-ready state; booking tests own the subsequent coordinated operation.
 * @param shared Whether a different participating owner organizes the lottery.
 */
suspend fun FirebaseEmulatorRule.bookingFixture(shared: Boolean = false): BookingFixture {
    val owner = registeredActor(Role.OWNER)
    val organizer = if (shared) registeredActor(Role.OWNER) else owner
    val entrant = registeredActor(Role.ENTRANT)
    val now = clock.instant()
    val site = CampingFixtures.campsite(ownerId = owner.id).copy(createdAt = now, publishedAt = now)
    val arrival = LocalDate.ofInstant(now, ZoneId.of(site.details.timeZone)).plusDays(14)
    val lottery =
        CampingFixtures.lottery(ownerId = organizer.id).copy(
            participantOwnerIds = setOf(owner.id, organizer.id),
            configuration =
                LotteryConfiguration(
                    now.minusSeconds(7200),
                    now.minusSeconds(3600),
                    now.minusSeconds(3600),
                    now.plusSeconds(3600),
                    2,
                    null,
                ),
            createdAt = now.minusSeconds(10800),
            publishedAt = now.minusSeconds(10800),
            drawnAt = now.minusSeconds(3600),
        )
    seed("campsites/site", CampsiteDocument.encode(site))
    seed(
        "lotteries/draw",
        LotteryDocument.encode(lottery) +
            mapOf(
                "participantOwnerIds" to lottery.participantOwnerIds.sorted(),
                "_revision" to 0L,
            ),
    )
    val nights = (0L..2L).map(arrival::plusDays)
    seed("lotteries/draw/offers/site", LotteryOfferDocument.encode(LotteryOffer("site", nights)))
    for (night in nights) {
        seed(
            "campsites/site/calendar/$night",
            CalendarNightDocument.encode(CalendarNight(night, true)),
        )
    }
    seed(
        "lotteries/draw/entries/${entrant.id}",
        LotteryEntryDocument.encode(
            LotteryEntry(
                "draw",
                entrant.id,
                EntryState.OFFERED,
                now.minusSeconds(7200),
                now.minusSeconds(3600),
            ),
        ),
    )
    val request =
        BookingRequest(
            "draw",
            entrant.id,
            "site",
            arrival,
            arrival.plusDays(2),
            4,
            13000,
            site.details.rules,
            BookingPolicy.VERSION,
        )
    return BookingFixture(owner, entrant, site, lottery, request, organizer)
}

/*
 * Suggests the next demo screen from shared saved records and the controlled scenario clock.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoDocuments
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppDestination
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import java.time.Instant

/** A contextual next action; it points at actual saved records and never fills a form.
 * @param label Localized action text.
 * @param area Person whose view should open.
 * @param route Existing application destination.
 * @param at Optional explicit demo-time advancement.
 * @param drawId Due lottery to draw through its repository.
 */
data class DemoJourneyAction(
    val label: Int,
    val area: DemoScenario,
    val route: String,
    val at: Instant? = null,
    val drawId: String? = null,
)

/** Chooses a useful next view from saved facts under the shared clock.
 * @param now Current demonstration time.
 */
fun DemoDocuments.journeyAction(now: Instant): DemoJourneyAction {
    val lottery =
        list("lotteries", LotteryDocument)
            .filter {
                it.publishedAt != null && it.cancellation == null &&
                    it.ownerId == "demo-host"
            }.lastOrNull { it.createdAt >= Instant.parse("2027-05-10T18:00:00Z") }
            ?: require(FirestorePaths.lottery("demo-open"), LotteryDocument)
    val site =
        list(
            "${FirestorePaths.lottery(lottery.id)}/offers",
            io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument,
        ).firstOrNull()?.campsiteId
    val entry = get(FirestorePaths.entry(lottery.id, "demo-account"), LotteryEntryDocument)
    val stay =
        list("reservations", ReservationDocument).firstOrNull {
            it.lotteryId == lottery.id &&
                it.entrantId == "demo-account"
        }
    return when {
        now < lottery.configuration.registrationOpensAt -> {
            DemoJourneyAction(
                R.string.demo_open_registration,
                DemoScenario.ENTRANT,
                AppDestination.lotteryDetail(lottery.id, LotteryContext.ENTRANT),
                lottery.configuration.registrationOpensAt,
            )
        }

        lottery.registrationIsOpen(now) && entry?.state != EntryState.WAITING -> {
            DemoJourneyAction(
                R.string.demo_explore_draw,
                DemoScenario.ENTRANT,
                if (site !=
                    null
                ) {
                    AppDestination.campsiteGraph(Role.ENTRANT, site, CampsiteDetailContext.VIEW)
                } else {
                    AppDestination.lotteryDetail(lottery.id, LotteryContext.ENTRANT)
                },
            )
        }

        lottery.drawnAt == null -> {
            DemoJourneyAction(
                R.string.demo_show_results,
                DemoScenario.ENTRANT,
                AppDestination.inbox(Role.ENTRANT),
                maxOf(now, lottery.configuration.registrationClosesAt, lottery.configuration.bookingOpensAt),
                lottery.id,
            )
        }

        stay == null -> {
            DemoJourneyAction(R.string.demo_open_results, DemoScenario.ENTRANT, AppDestination.inbox(Role.ENTRANT))
        }

        stay.cancellation == null -> {
            DemoJourneyAction(
                R.string.demo_view_as_host,
                DemoScenario.OWNER,
                AppDestination.reservationDetail(stay.id, ReservationContext.OWNER),
            )
        }

        else -> {
            DemoJourneyAction(R.string.demo_inspect_history, DemoScenario.ADMIN, AppDestination.ADMIN_LOGS)
        }
    }
}

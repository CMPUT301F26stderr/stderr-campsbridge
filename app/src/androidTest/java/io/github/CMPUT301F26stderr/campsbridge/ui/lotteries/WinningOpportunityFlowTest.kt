/*
 * Winning actions use real saved readers, registered booking and the shared account root.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.testsupport.onCalendarDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Winning actions use real saved readers, registered booking and the shared account root. */
class WinningOpportunityFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val arrival = LocalDate.of(2027, 5, 18)
    private val siteA =
        sampleCampsite("site/A ?1", "owner-A").let {
            it.copy(details = it.details.copy(name = "River Camp A"))
        }
    private val siteB =
        sampleCampsite("site/B ?2", "owner-B").let {
            it.copy(details = it.details.copy(name = "River Camp B"))
        }
    private val lottery =
        Lottery(
            "draw/A ?1",
            "owner-A",
            "Shared May camping",
            "Quiet riverside stays.",
            LotteryConfiguration(
                now.minusSeconds(7200),
                now.minusSeconds(3600),
                now.minusSeconds(1800),
                now.plusSeconds(7200),
                1,
                null,
            ),
            0,
            now.minusSeconds(10800),
            now.minusSeconds(9000),
            now.minusSeconds(3600),
            participantOwnerIds = setOf("owner-A", "owner-B"),
        )
    private val entry =
        LotteryEntry(lottery.id, "account-1", EntryState.OFFERED, now.minusSeconds(5400), now.minusSeconds(3600))
    private val offers =
        listOf(siteA, siteB).map {
            LotteryOffer(it.id, listOf(arrival, arrival.plusDays(1), arrival.plusDays(4)))
        }

    /** Releases actual graph listeners and controlled actions. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** WN-01: one saved offer enters booking directly without reserving. */
    @Test fun oneOfferEntersBookingDirectlyWithoutAWrite() {
        start(multiple = false)
        openLottery()
        compose.onNodeWithText("Book your stay").performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose.onNodeWithText("Choose campsite").assertDoesNotExist()
        assertBookingIdentity(siteA.id)
    }

    /** WN-01: choosing B carries both saved IDs into the real booking controller with no write. */
    @Test fun severalOffersChooseTheActualSiteBBeforeBooking() {
        start()
        openLottery()
        compose.onNodeWithText("Book your stay").performClick()
        compose.onNode(hasText("Choose campsite") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose River Camp B").performScrollTo().performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        assertBookingIdentity(siteB.id)
    }

    /** WN-03: Inbox entry, cancellation, failure and held Retry produce one observed decline. */
    @Test fun inboxWinCanCancelThenRetryADeclineWithoutBooking() {
        val gate = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<String, String>>()
        host.lotteries.declineHandler = { id, actor ->
            requests.add(id to actor)
            throw IOException("decline offline")
        }
        val event =
            Notification(
                "winner",
                null,
                NotificationType.LOTTERY_WON,
                NotificationSubject(SubjectType.LOTTERY, lottery.id),
                null,
                now.minusSeconds(3600),
            )
        host.notifications.records.value = listOf(event)
        host.notifications.receipts.value = mapOf("account-1" to listOf(InboxReceipt(event.id, null)))
        host.notifications.markReadHandler = { _, _ -> Unit }
        try {
            start()
            compose.onNodeWithText("Inbox").performClick()
            compose.onNodeWithTag("inbox-event-winner").performClick()
            compose.onNodeWithText("Decline opportunity").performClick()
            compose.onNodeWithText("Keep opportunity").performClick()
            compose.runOnIdle { assertTrue(requests.isEmpty()) }
            compose.onNodeWithText("Decline opportunity").performClick()
            compose.onNodeWithText("Decline", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Could not decline this opportunity.").assertIsDisplayed()
            compose.onNodeWithText("You were selected.").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(lottery.id to "account-1"), requests)
                host.lotteries.declineHandler = { id, actor ->
                    requests.add(id to actor)
                    gate.await()
                    host.lotteries.entries.value = listOf(entry.copy(state = EntryState.DECLINED, stateChangedAt = now))
                }
            }
            compose.onNodeWithText("Retry decline").performClick()
            compose.onNodeWithText("Declining opportunity").assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(List(2) { lottery.id to "account-1" }, requests)
                gate.complete(Unit)
            }
            compose.onNodeWithText("You declined this opportunity.").assertIsDisplayed()
            compose.onNodeWithText("Book your stay").assertDoesNotExist()
            compose.onNodeWithText("Decline opportunity").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(2, requests.size)
                assertTrue(host.bookings.reserveRequests.isEmpty())
                assertTrue(
                    host.bookings.records.value
                        .isEmpty(),
                )
            }
        } finally {
            gate.complete(Unit)
        }
    }

    /** WN-04: changed inventory retains the win for another campsite or explicit decline. */
    @Test fun unavailableNightsCanReturnToAnotherCampsiteAndToDecline() {
        start()
        openLottery()
        compose.onNodeWithText("Book your stay").performClick()
        compose.onNodeWithContentDescription("Choose River Camp B").performScrollTo().performClick()
        compose.onNodeWithTag("booking-calendar").performScrollTo()
        compose.onCalendarDate(arrival).performScrollTo().performClick()
        compose.onCalendarDate(arrival.plusDays(2)).performScrollTo().performClick()
        compose.runOnIdle {
            host.campsites.calendarNights.value =
                offers.first().nights.map { CalendarNight(it, true, "another-reservation") }
        }
        compose.onNodeWithText("Next").performClick()
        compose
            .onNodeWithText(
                "Some selected nights are unavailable. Choose another stay.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNode(hasText("Choose campsite") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose River Camp A").performScrollTo().performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("You were selected.").assertIsDisplayed()
        compose.onNodeWithText("Decline opportunity").performClick()
        compose.onNodeWithText("Keep opportunity").performClick()
        compose.runOnIdle {
            assertTrue(host.bookings.reserveRequests.isEmpty())
            assertTrue(host.lotteries.events.isEmpty())
            assertEquals(siteA.id, host.campsites.observedIds.last())
        }
    }

    /** Supplies related saved facts and starts the real root without any action.
     * @param multiple Whether A and B both have saved allocations.
     */
    private fun start(multiple: Boolean = true) {
        host.clock = Clock.fixed(now, ZoneOffset.UTC)
        host.campsites.records.value = listOf(siteA, siteB)
        host.campsites.calendarNights.value = offers.first().nights.map { CalendarNight(it, true) }
        host.lotteries.records.value = listOf(lottery)
        host.lotteries.liveOffers.value = mapOf(lottery.id to if (multiple) offers else offers.take(1))
        host.lotteries.entries.value = listOf(entry)
        compose.setContent { host.Content() }
    }

    /** Opens this saved winning entry through its actual entrant list. */
    private fun openLottery() {
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText("Action needed").performClick()
        compose.onNodeWithText(lottery.title).performClick()
    }

    /** Checks actual controller source IDs and the absence of writes from selection.
     * @param campsiteId Saved choice passed into booking.
     */
    private fun assertBookingIdentity(campsiteId: String) {
        compose.runOnIdle {
            assertEquals(campsiteId, host.campsites.observedIds.last())
            assertEquals(lottery.id to "account-1", host.bookings.opportunityRequests.last())
            assertTrue(host.bookings.reserveRequests.isEmpty())
            assertTrue(host.lotteries.events.isEmpty())
        }
    }
}

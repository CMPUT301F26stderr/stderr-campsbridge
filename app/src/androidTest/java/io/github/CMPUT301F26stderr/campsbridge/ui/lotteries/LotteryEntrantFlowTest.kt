package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Real entrant list/detail graphs retain their caller and independently recover read failures. */
class LotteryEntrantFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val site = sampleCampsite("site/A", "owner")
    private val parent =
        Lottery(
            "draw/A",
            "owner",
            "May riverside lottery",
            "Quiet riverside camping.",
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
            publishedAt = now.minusSeconds(9000),
        )

    /** Cancels the real retained graph and every source it created. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Partial winners stay pending until parent completion updates detail and list categories. */
    @Test fun pendingDrawBecomesConfirmedWinAndBackRetainsWaiting() {
        start(completed = false)
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("Results pending").assertIsDisplayed()
        compose.onNodeWithText("You were selected.").assertDoesNotExist()
        compose.onNodeWithText("Continue setup").assertDoesNotExist()
        compose.onNodeWithText("Create lottery").assertDoesNotExist()
        compose.runOnIdle { host.lotteries.records.value = listOf(parent.copy(drawnAt = now)) }
        compose.onNodeWithText("You were selected.").assertIsDisplayed()
        compose.onNodeWithText("No reservation has been made with this win.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Waiting").assertIsSelected()
        compose.onNodeWithText("No lotteries in Waiting.").assertIsDisplayed()
        compose.onNodeWithText("Action needed").performClick()
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertTrue(host.lotteries.events.isEmpty())
            assertEquals(listOf(parent.id to "account-1"), host.bookings.opportunityRequests)
        }
    }

    /** Consumed wins open their actual saved reader and retain one parent through restoration. */
    @Test fun savedReservationAndHistoryCallerSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        val saved = savedReservation()
        host.bookings.records.value = listOf(saved)
        start(restoration = restoration)
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("Reservation ${saved.id}").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Reservation ${saved.id}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My lotteries").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsSelected()
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertEquals(1, host.bookings.opportunityRequests.size)
            assertEquals(0, host.bookings.activeRecordObservers)
            assertEquals(0, host.lotteries.activeOfferObservers)
        }
    }

    /** An unknown used-win read preserves the confirmed win and offers a real held retry. */
    @Test fun opportunityFailureRetriesWithoutReplacingParentOrClaimingBookingSuccess() {
        host.bookings.opportunityHandler = { _, _ -> throw IOException("server offline") }
        start()
        compose.onNodeWithText("Action needed").performClick()
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("You were selected.").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Could not confirm whether this win has been used.",
            ).assertIsDisplayed()
        val gate = CompletableDeferred<Reservation?>()
        try {
            val attemptsBeforeRetry =
                compose.runOnIdle {
                    host.bookings.opportunityHandler = { _, _ -> gate.await() }
                    host.bookings.opportunityRequests.size
                }
            compose.onNodeWithText("Retry").performClick()
            compose.onNodeWithText("Checking your reservation").assertIsDisplayed()
            compose
                .onNodeWithText(
                    "No reservation has been made with this win.",
                ).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(attemptsBeforeRetry + 1, host.bookings.opportunityRequests.size)
                gate.complete(null)
            }
            compose
                .onNodeWithText(
                    "No reservation has been made with this win.",
                ).assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Action needed").assertIsSelected()
            compose.runOnIdle {
                assertEquals(attemptsBeforeRetry + 1, host.bookings.opportunityRequests.size)
                assertTrue(host.lotteries.events.isEmpty())
            }
        } finally {
            gate.complete(null)
        }
    }

    /** Membership observation errors leave saved facts visible and retry only that section. */
    @Test fun ownEntryFailureRetainsParentAndCanRetry() {
        start(completed = false)
        compose.onNodeWithText(parent.title).performClick()
        compose.runOnIdle { host.lotteries.entrantFailure.value = IOException("entry offline") }
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.onNodeWithText("Could not load your entry.").assertIsDisplayed()
        compose.runOnIdle { host.lotteries.entrantFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Results pending").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertTrue(host.bookings.opportunityRequests.isEmpty())
        }
    }

    /** A missing referenced parent remains visible across categories instead of becoming empty. */
    @Test fun unresolvedMembershipDoesNotDisappearWhenCategoryChanges() {
        start(records = emptyList())
        compose.onNodeWithText("Linked lottery is unavailable").assertIsDisplayed()
        compose.onNodeWithText(parent.id).assertIsDisplayed()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Linked lottery is unavailable").assertIsDisplayed()
        compose.onNodeWithText("No lotteries in History.").assertDoesNotExist()
        compose.onNodeWithText("Create lottery").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, host.lotteryDetailCreations) }
    }

    /** Losing membership retires its consuming-record lane while preserving the saved parent. */
    @Test fun removedOwnEntryRetiresReservationObservationInPlace() {
        val saved = savedReservation()
        host.bookings.records.value = listOf(saved)
        start()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("Reservation ${saved.id}").assertIsDisplayed()
        compose.runOnIdle { host.lotteries.entries.value = emptyList() }
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.onNodeWithText("You have not joined this lottery.").assertIsDisplayed()
        compose.onNodeWithText("Reservation ${saved.id}").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, host.bookings.activeRecordObservers) }
    }

    /** Starts the real entrant root with only this account's entry and explicit saved fixtures.
     * @param completed Whether the parent confirms the finished draw.
     * @param restoration Optional actual composition restoration host.
     * @param records Exact saved parents, including a deliberately unresolved reference.
     */
    private fun start(
        completed: Boolean = true,
        restoration: StateRestorationTester? = null,
        records: List<Lottery> = listOf(parent.copy(drawnAt = now)),
    ) {
        host.campsites.records.value = listOf(site)
        host.lotteries.records.value = if (completed) records else listOf(parent)
        host.lotteries.entries.value =
            listOf(
                LotteryEntry(
                    parent.id,
                    "account-1",
                    EntryState.OFFERED,
                    now.minusSeconds(5400),
                    now,
                ),
            )
        if (restoration == null) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText("Lotteries").performClick()
    }

    /** Supplies a complete consumed opportunity with historical payment and accepted terms. */
    private fun savedReservation(): Reservation =
        Reservation(
            "reservation/A",
            parent.id,
            "account-1",
            site.id,
            LocalDate.of(2027, 5, 14),
            LocalDate.of(2027, 5, 16),
            2,
            BookedCampsite(
                site.details.name,
                site.details.locationLabel,
                site.details.location,
                site.details.timeZone,
            ),
            AcceptedTerms(site.details.rules, BookingPolicy.VERSION, now),
            now,
            Payment(13000, now),
        )
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
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

/** Real Explore/campsite/choices/lottery navigation and shared entrant mutation callbacks. */
class CampsiteLotteryEntryFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("river/one", "owner")
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val first =
        Lottery(
            "draw/one",
            "owner",
            "River weekend",
            "Two-night stays",
            LotteryConfiguration(
                now.minusSeconds(60),
                now.plusSeconds(600),
                now.plusSeconds(600),
                now.plusSeconds(1_200),
                1,
                null,
            ),
            0,
            now.minusSeconds(120),
            now.minusSeconds(120),
        )
    private val second = first.copy(id = "draw-two", title = "Summer break")

    /** Releases all real graph-owned readers and commands. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /**
     * One choice joins directly and waits for acknowledgement plus observation across
     * restoration.
     */
    @Test fun soleChoiceJoinsFromTheSavedCampsite() {
        val gate = CompletableDeferred<Unit>()
        try {
            host.lotteries.joinHandler = { _, _ -> gate.await() }
            val restoration = StateRestorationTester(compose)
            start(listOf(first), restoration)
            compose.onNodeWithText("Join waitlist").performScrollTo().performClick()
            compose.onNodeWithText("Join waitlist").assertIsNotEnabled()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Join waitlist").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle { host.lotteries.entries.value = listOf(entry(EntryState.WAITING)) }
            compose.onNodeWithText("You are on the waitlist.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Join waitlist").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle { gate.complete(Unit) }
            compose.onNodeWithText("Leave waitlist").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("join:${first.id}:account-1"), host.lotteries.events)
                assertEquals(1, host.campsiteDetailCreations)
            }
        } finally {
            gate.complete(Unit)
        }
    }

    /** A failed own-entry source exposes retry after withdrawal without sending another command. */
    @Test fun campsiteWithdrawalCanRecoverItsMembershipSource() {
        host.lotteries.entries.value = listOf(entry(EntryState.WAITING))
        host.lotteries.withdrawHandler = { _, _ -> }
        start(listOf(first))
        compose.onNodeWithText("Leave waitlist").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(host.lotteries.events.isEmpty()) }
        compose.onNodeWithText("Leave waitlist").performScrollTo().performClick()
        compose.onNodeWithText("Leave").performClick()
        compose.runOnIdle { host.lotteries.entrantFailure.value = IOException("entry offline") }
        compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
        compose.onNodeWithText("Could not load your entry.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            host.lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
            host.lotteries.entrantFailure.value = null
        }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithText("You left this lottery.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Join waitlist").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("withdraw:${first.id}:account-1"), host.lotteries.events)
        }
    }

    /**
     * Multiple choices open exact saved IDs and return through the retained choices and
     * campsite.
     */
    @Test fun multipleChoicesOpenTheirActualLotteryAndReturnToTheSameParent() {
        val date = LocalDate.of(2027, 5, 15)
        host.lotteries.liveOffers.value =
            mapOf(
                first.id to listOf(LotteryOffer(site.id, listOf(date))),
                second.id to listOf(LotteryOffer(site.id, listOf(date.plusDays(1)))),
            )
        start(listOf(first, second))
        compose.onNodeWithText("Available lotteries").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, host.lotteries.activeOfferObservers) }
        compose.onNodeWithText("May 15, 2027").assertIsDisplayed()
        compose.onNodeWithText("May 16, 2027").assertIsDisplayed()
        compose.onNodeWithText(second.title).performClick()
        compose.onNodeWithText("Lottery").assertIsDisplayed()
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(second.id), host.lotteries.observedRecordIds) }
        compose.waitUntil(10_000) { host.lotteries.activeOfferObservers == 1 }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText(second.title) and hasClickAction()).assertIsDisplayed()
        compose.waitUntil(10_000) { host.lotteries.activeOfferObservers == 2 }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Available lotteries").performScrollTo().assertIsDisplayed()
        compose.waitUntil(10_000) { host.lotteries.activeOfferObservers == 0 }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Search campsites").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(1, host.lotteryDetailCreations)
            assertTrue(host.lotteries.events.isEmpty())
        }
    }

    /** Failed associations retry sectionally before a confirmed no-active state is displayed. */
    @Test fun associationFailureNeverClaimsThereIsNoActiveLottery() {
        host.lotteries.associationFailure.value = IOException("association offline")
        start(emptyList())
        compose
            .onNodeWithText(
                "Could not load campsite lotteries.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("No active lottery").assertDoesNotExist()
        compose.runOnIdle { host.lotteries.associationFailure.value = null }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithText("No active lottery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Join waitlist").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(1, host.campsites.observedIds.size)
        }
    }

    /** The actual row Retry preserves the other allocation and never reloads the saved campsite. */
    @Test fun choiceAllocationRetryTargetsOnlyTheFailedRow() {
        val date = LocalDate.of(2027, 5, 15)
        host.lotteries.liveOffers.value =
            mapOf(
                first.id to listOf(LotteryOffer(site.id, listOf(date))),
            )
        host.lotteries.offerFailures.value = mapOf(second.id to IOException("allocation offline"))
        start(listOf(first, second))
        compose.onNodeWithText("Available lotteries").performScrollTo().performClick()
        compose.onNodeWithText("Could not load this campsite’s offered nights.").assertIsDisplayed()
        compose.onNodeWithText("May 15, 2027").assertIsDisplayed()
        compose.runOnIdle { host.lotteries.offerFailures.value = emptyMap() }
        compose.onNodeWithText("Retry").performClick()
        compose
            .onNodeWithText(
                "This campsite has no offered nights in this lottery.",
            ).assertIsDisplayed()
        compose.onNodeWithText("May 15, 2027").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteries.observedOfferIds.count { it == first.id })
            assertEquals(2, host.lotteries.observedOfferIds.count { it == second.id })
            assertEquals(listOf(site.id), host.lotteries.associationRequests)
            assertEquals(1, host.campsiteDetailCreations)
            assertTrue(host.lotteries.events.isEmpty())
        }
    }

    /** A single choice also opens the existing entrant detail rather than a second join surface. */
    @Test fun soleChoiceCanOpenTheSavedLotteryAndReturn() {
        start(listOf(first))
        compose.onNodeWithText("View lottery").performScrollTo().performClick()
        compose.onNodeWithText("Lottery").assertIsDisplayed()
        compose.onNodeWithText(first.title).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(first.id), host.lotteries.observedRecordIds) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("View lottery").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsiteDetailCreations)
            assertEquals(1, host.lotteryDetailCreations)
            assertTrue(host.lotteries.events.isEmpty())
        }
    }

    /** Opens the existing production graph with fixed matching outcomes, not alternate algorithms.
     * @param rows Explicit association facts.
     * @param restoration Optional actual saved-composition harness.
     */
    private fun start(
        rows: List<Lottery>,
        restoration: StateRestorationTester? = null,
    ) {
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        host.campsites.records.value = listOf(site)
        host.lotteries.records.value = rows
        host.lotteries.associations.value = mapOf(site.id to rows)
        if (restoration == null) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText(site.details.name).performClick()
    }

    /** Supplies membership independently of repository acknowledgement.
     * @param state Exact persisted entry state.
     */
    private fun entry(state: EntryState): LotteryEntry =
        LotteryEntry(
            first.id,
            "account-1",
            state,
            now.minusSeconds(30),
            now,
        )
}

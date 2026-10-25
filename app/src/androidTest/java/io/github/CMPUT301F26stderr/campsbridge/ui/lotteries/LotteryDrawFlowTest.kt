package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Actual owner fallback reuses one saved parent and waits for observed completed outcomes. */
class LotteryDrawFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val parent =
        Lottery(
            "draw/A",
            "account-1",
            "Riverside May draw",
            "",
            LotteryConfiguration(now.minusSeconds(120), now, now, now.plusSeconds(3600), 1, null),
            1,
            now.minusSeconds(300),
            publishedAt = now.minusSeconds(240),
        )
    private val winner =
        LotteryEntry(
            parent.id,
            "entrant-A",
            EntryState.OFFERED,
            now.minusSeconds(90),
            now,
        )

    /** Closes the actual graph and every controlled job after the interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Pending progress survives child Back/recreation, while parent completion owns row results. */
    @Test fun manualCallAndObservedCompletionCanArriveInEitherOrder() {
        val gate = CompletableDeferred<DrawResult>()
        val restoration = StateRestorationTester(compose)
        try {
            host.lotteries.drawHandler = { gate.await() }
            start(restoration)
            compose.onNodeWithText("Run draw").performClick()
            compose.onNodeWithText("Run draw").assertIsNotEnabled()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Run draw").assertIsNotEnabled()
            compose.onNodeWithText("Participants").performClick()
            compose.onNodeWithText("entrant-A").assertIsDisplayed()
            compose.onNodeWithText("Selected").assertDoesNotExist()
            compose.runOnIdle { host.lotteries.records.value = listOf(parent.copy(drawnAt = now)) }
            compose.onNodeWithText("Selected").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Run draw").assertIsNotEnabled()
            compose.onNodeWithText("Booking open").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("draw:${parent.id}"), host.lotteries.events)
                assertEquals(1, host.lotteryDetailCreations)
                gate.complete(DrawResult.ALREADY_DRAWN)
            }
            compose.onNodeWithText("Run draw").assertDoesNotExist()
        } finally {
            gate.complete(DrawResult.ALREADY_DRAWN)
        }
    }

    /** Explicit retry retains context; acknowledgement alone leaves Results pending visible. */
    @Test fun failedCommandRetriesWithoutInventingCompletion() {
        host.lotteries.drawHandler = { throw IOException("draw offline") }
        start()
        compose.onNodeWithText("Run draw").performClick()
        compose.onNodeWithText("The draw could not be completed.").assertIsDisplayed()
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.runOnIdle { host.lotteries.drawHandler = { DrawResult.COMPLETED } }
        compose.onNodeWithText("Run draw").performClick()
        compose.onNodeWithText("The draw could not be completed.").assertDoesNotExist()
        compose.onNodeWithText("Results pending").assertIsDisplayed()
        compose.onNodeWithText("Run draw").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf("draw:${parent.id}", "draw:${parent.id}"), host.lotteries.events)
            host.lotteries.records.value = listOf(parent.copy(drawnAt = now))
        }
        compose.onNodeWithText("Run draw").assertDoesNotExist()
        compose.onNodeWithText("Booking open").assertIsDisplayed()
    }

    /** Opens the real owner primary list and its saved detail with a due undrawn fixture.
     * @param restoration Optional real composition restoration host.
     */
    private fun start(restoration: StateRestorationTester? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.lotteries.records.value = listOf(parent)
        host.lotteries.entries.value = listOf(winner)
        if (restoration ==
            null
        ) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(parent.title).performClick()
    }
}

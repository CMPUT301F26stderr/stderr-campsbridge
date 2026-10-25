package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Real membership routes use observed facts and shared submission/confirmation controls. */
class LotteryMembershipFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val parent =
        Lottery(
            "draw/A",
            "owner",
            "Riverside registration",
            "May long-weekend camping.",
            LotteryConfiguration(
                now.minusSeconds(60),
                now.plusSeconds(3600),
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                1,
                null,
            ),
            1,
            now.minusSeconds(300),
            publishedAt = now.minusSeconds(120),
        )

    /** Releases the actual graph and controlled observations after every interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Rejoin waits for acknowledgement, survives restoration and returns to the actual caller. */
    @Test fun rejoinRetainsItsCommandUntilAckAndMovesIntoWaiting() {
        val restoration = StateRestorationTester(compose)
        val gate = CompletableDeferred<Unit>()
        try {
            host.lotteries.joinHandler = { _, _ -> gate.await() }
            start(EntryState.WITHDRAWN, restoration)
            compose.onNodeWithText("Join waitlist").performClick()
            compose.onNodeWithText("Join waitlist").assertIsNotEnabled()
            compose.onNodeWithText("You left this lottery.").assertIsDisplayed()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Join waitlist").assertIsNotEnabled()
            compose.runOnIdle { host.lotteries.entries.value = listOf(entry(EntryState.WAITING)) }
            compose.onNodeWithText("You are on the waitlist.").assertIsDisplayed()
            compose.onNodeWithText("Leave waitlist").assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(listOf("join:${parent.id}:account-1"), host.lotteries.events)
                gate.complete(Unit)
            }
            compose.onNodeWithText("Join waitlist").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("History").assertIsSelected()
            compose.onNodeWithText("No lotteries in History.").assertIsDisplayed()
            compose.onNodeWithText("Waiting").performClick()
            compose.onNodeWithText(parent.title).assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, host.lotteryDetailCreations) }
        } finally {
            gate.complete(Unit)
        }
    }

    /** Leave Cancel and system Back dismiss without writes; Confirm blocks repeated submission. */
    @Test fun leaveRequiresConfirmationAndRetainsObservedFeedback() {
        val gate = CompletableDeferred<Unit>()
        try {
            host.lotteries.withdrawHandler = { _, _ -> gate.await() }
            start(EntryState.WAITING)
            compose.onNodeWithText("Leave waitlist").performClick()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
            compose.onNodeWithText("Leave waitlist").performClick()
            pressDialogBack()
            compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
            compose.onNodeWithText(parent.title).assertIsDisplayed()
            compose.runOnIdle { assertTrue(host.lotteries.events.isEmpty()) }
            compose.onNodeWithText("Leave waitlist").performClick()
            compose.onNodeWithText("Leave").performClick()
            compose.onNodeWithText("Leave").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            pressDialogBack()
            compose.onNodeWithText("Leave this lottery?").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("withdraw:${parent.id}:account-1"), host.lotteries.events)
                gate.complete(Unit)
            }
            compose.onNodeWithText("Leave this lottery?").assertIsDisplayed()
            compose.runOnIdle { host.lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN)) }
            compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
            compose.onNodeWithText("You left this lottery.").assertIsDisplayed()
            compose.onNodeWithText("Join waitlist").assertIsDisplayed()
        } finally {
            gate.complete(Unit)
        }
    }

    /** A failed operation preserves the saved detail and offers another explicit action. */
    @Test fun failedJoinKeepsMembershipAndRetriesOnlyOnAnotherClick() {
        host.lotteries.joinHandler = { _, _ -> throw IOException("offline") }
        start(EntryState.WITHDRAWN)
        compose.onNodeWithText("Join waitlist").performClick()
        compose.onNodeWithText("Could not update your entry.").assertIsDisplayed()
        compose.onNodeWithText("You left this lottery.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("join:${parent.id}:account-1"), host.lotteries.events)
            host.lotteries.joinHandler = { _, _ ->
                host.lotteries.entries.value = listOf(entry(EntryState.WAITING))
            }
        }
        compose.onNodeWithText("Join waitlist").performClick()
        compose.onNodeWithText("You are on the waitlist.").assertIsDisplayed()
        compose.onNodeWithText("Could not update your entry.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(List(2) { "join:${parent.id}:account-1" }, host.lotteries.events)
        }
    }

    /** A cancelled parent closes stale confirmation; a later Confirm cannot submit withdrawal. */
    @Test fun changedParentRetiresAnUnsubmittedConfirmation() {
        start(EntryState.WAITING)
        compose.onNodeWithText("Leave waitlist").performClick()
        compose.onNodeWithText("Leave this lottery?").assertIsDisplayed()
        compose.runOnIdle {
            host.lotteries.records.value =
                listOf(parent.copy(cancellation = Cancellation(now, "owner")))
        }
        compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
        compose.onNodeWithText("Leave waitlist").assertDoesNotExist()
        compose.onNodeWithText("Cancelled").assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.lotteries.events.isEmpty()) }
    }

    /** Failed-source Retry remains reachable after withdrawal without resubmitting the operation. */
    @Test fun withdrawalSourceFailureCanRecoverOutsideThePendingDialog() {
        host.lotteries.withdrawHandler = { _, _ -> }
        start(EntryState.WAITING)
        compose.onNodeWithText("Leave waitlist").performClick()
        compose.onNodeWithText("Leave").performClick()
        compose.onNodeWithText("Leave this lottery?").assertIsDisplayed()
        compose.runOnIdle { host.lotteries.entrantFailure.value = IOException("entry offline") }
        compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
        compose.onNodeWithText("Could not load your entry.").assertIsDisplayed()
        compose.onNodeWithText("Leave waitlist").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf("withdraw:${parent.id}:account-1"), host.lotteries.events)
            host.lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
            host.lotteries.entrantFailure.value = null
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("You left this lottery.").assertIsDisplayed()
        compose.onNodeWithText("Leave this lottery?").assertDoesNotExist()
        compose.onNodeWithText("Leave waitlist").assertDoesNotExist()
        compose.onNodeWithText("Join waitlist").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("withdraw:${parent.id}:account-1"), host.lotteries.events)
        }
    }

    /** Sends real Back only after Espresso has selected the focused modal window. */
    private fun pressDialogBack() {
        compose.onNodeWithText("Leave this lottery?").assertIsDisplayed()
        Espresso
            .onView(ViewMatchers.isRoot())
            .inRoot(RootMatchers.isDialog())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** Opens a real entrant detail from the membership's actual category.
     * @param state Controlled persisted own entry.
     * @param restoration Optional real composition restoration host.
     */
    private fun start(
        state: EntryState,
        restoration: StateRestorationTester? = null,
    ) {
        host.lotteries.records.value = listOf(parent)
        host.lotteries.entries.value = listOf(entry(state))
        if (restoration == null) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText("Lotteries").performClick()
        if (state == EntryState.WITHDRAWN) compose.onNodeWithText("History").performClick()
        compose.onNodeWithText(parent.title).performClick()
    }

    /** Supplies membership facts independently from command acknowledgement.
     * @param state Stored participation state.
     */
    private fun entry(state: EntryState): LotteryEntry =
        LotteryEntry(
            parent.id,
            "account-1",
            state,
            now.minusSeconds(30),
            now,
        )
}

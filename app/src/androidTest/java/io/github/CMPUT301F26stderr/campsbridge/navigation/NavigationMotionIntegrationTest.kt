package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.espresso.Espresso
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Exercises Back motion through production destinations and a persistent Material navigation bar. */
class NavigationMotionIntegrationTest {
    @get:Rule val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                },
        )
    private val host = TestAppHost()
    private val input = DirectNavigationEventInput()

    /** Releases the route-owned models and subscriptions after each journey. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Reservation and lottery Inbox links share drag feedback and rightward Back completion. */
    @Test fun inboxTargetsShareGestureAndButtonBackMotion() {
        val now = Instant.parse("2027-05-01T12:00:00Z")
        val lottery =
            Lottery(
                "motion-draw",
                "owner",
                "Riverside draw",
                "Two nights",
                LotteryConfiguration(
                    now.minusSeconds(7200),
                    now.minusSeconds(3600),
                    now.minusSeconds(1800),
                    now.plusSeconds(7200),
                    1,
                    null,
                ),
                2,
                now.minusSeconds(10800),
                publishedAt = now.minusSeconds(9000),
                drawnAt = now.minusSeconds(1800),
            )
        val reservation = previewReservation().copy(id = "motion-stay", entrantId = "account-1")
        val winner =
            Notification(
                "winner",
                null,
                NotificationType.LOTTERY_WON,
                NotificationSubject(SubjectType.LOTTERY, lottery.id),
                null,
                now,
            )
        val confirmed =
            winner.copy(
                id = "confirmed",
                type = NotificationType.RESERVATION_CONFIRMED,
                subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
            )
        host.lotteries.records.value = listOf(lottery)
        host.bookings.records.value = listOf(reservation)
        host.notifications.records.value = listOf(winner, confirmed)
        host.notifications.receipts.value =
            mapOf(
                "account-1" to listOf(InboxReceipt(winner.id, now), InboxReceipt(confirmed.id, now)),
            )
        start()
        compose.onNodeWithText("Inbox").performClick()
        for (event in listOf(confirmed, winner)) {
            openEvent(event.id)
            val origin = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
            for (edge in listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT)) {
                drag(edge)
                val dragged = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
                assertTrue(
                    "Every saved target should respond to the physical gesture edge",
                    if (edge == NavigationEvent.EDGE_LEFT) dragged > origin else dragged < origin,
                )
                assertTrue("Drag feedback should stay bounded", kotlin.math.abs((dragged - origin).value) < 32f)
                compose.runOnIdle { input.backCancelled() }
                compose.mainClock.advanceTimeBy(400)
                compose.waitForIdle()
            }
            drag(NavigationEvent.EDGE_RIGHT)
            compose.runOnIdle { input.backCompleted() }
            assertBackMovesRight(origin.value)
            settleInbox(event.id)

            openEvent(event.id)
            val toolbarOrigin = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
            compose.onNodeWithContentDescription("Back").performClick()
            assertBackMovesRight(toolbarOrigin.value)
            settleInbox(event.id)

            openEvent(event.id)
            val buttonOrigin = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
            Espresso.pressBack()
            assertBackMovesRight(buttonOrigin.value)
            settleInbox(event.id)
        }
    }

    /** Switching primary destinations preserves Material's in-progress selection animation. */
    @Test fun bottomNavigationAnimatesSelectionAcrossDestinations() {
        start()
        val bar =
            compose.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                    hasAnyDescendant(hasText("Inbox")) and hasAnyDescendant(hasText("Explore")),
            )
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Inbox").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val moving = bar.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val settled = bar.captureToImage().toPixelMap()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
        var changed = false
        for (x in 0 until moving.width) {
            for (y in 0 until moving.height) {
                if (moving[x, y] != settled[x, y]) changed = true
            }
        }
        assertTrue("The same Material bar should animate selection instead of starting already selected", changed)
    }

    /** Guidelines use the shared page host and return to the unchanged welcome screen. */
    @Test fun onboardingGuidelinesUsePageTransitions() {
        host.accounts.account.value = null
        start()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Guidelines").performClick()
        compose.mainClock.advanceTimeBy(120)
        val entering = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
        assertTrue("Guidelines should enter from the right", entering > 80.dp)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val origin = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot().left
        compose.onNodeWithContentDescription("Back").performClick()
        assertBackMovesRight(origin.value)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("Get started").assertIsDisplayed()
    }

    /** Mounts the real app with a synthetic source for deterministic gesture progress. */
    private fun start() {
        compose.setContent {
            val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            host.Content()
        }
        compose.waitForIdle()
    }

    /** Opens one real saved subject and freezes the clock after entry settles.
     * @param id Notification identity displayed by the Inbox.
     */
    private fun openEvent(id: String) {
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("inbox-event-$id").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
    }

    /** Delivers separate frames for gesture start and physical drag progress.
     * @param edge Physical screen edge used for Back.
     */
    private fun drag(edge: Int) {
        compose.runOnIdle { input.backStarted(NavigationEvent(swipeEdge = edge)) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { input.backProgressed(NavigationEvent(swipeEdge = edge, progress = 0.5f)) }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }

    /** Checks the old page's direction while its exit is still in progress.
     * @param origin Resting Back button position in density-independent pixels.
     */
    private fun assertBackMovesRight(origin: Float) {
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val leaving =
            compose
                .onNodeWithContentDescription("Back")
                .getUnclippedBoundsInRoot()
                .left.value
        assertTrue("Completed Back should move the page right", leaving > origin)
    }

    /** Lets the page finish returning and confirms the real caller remains selected.
     * @param id Notification that remains in history after returning.
     */
    private fun settleInbox(id: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithTag("inbox-event-$id").assertIsDisplayed()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
    }
}

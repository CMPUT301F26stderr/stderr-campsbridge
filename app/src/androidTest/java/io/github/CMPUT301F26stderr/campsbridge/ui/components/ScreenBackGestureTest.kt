package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Checks shared Back gesture behavior provided by [AppScreen]. */
class ScreenBackGestureTest {
    // Keep animation enabled so progress and cancellation can be observed deterministically.
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                },
        )

    /** Both edge gestures follow their physical direction, stay bounded, and cancel cleanly. */
    @Test
    fun edgeProgressFollowsFingerAndCancellationRestoresContent() {
        val input = DirectNavigationEventInput()
        var callbackCount = 0
        compose.setContent {
            WithNavigationInput(input) {
                AppScreen(title = "Page", onBack = { callbackCount++ }) {
                    ScreenContent()
                }
            }
        }
        compose.waitForIdle()
        val original = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()
        listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT).forEach { edge ->
            compose.mainClock.autoAdvance = false

            startBack(input, edge)
            progressBack(input, edge, 0.25f)
            val quarter = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()
            progressBack(input, edge, 0.75f)
            val threeQuarters = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()
            progressBack(input, edge, 0.25f)
            val quarterAgain = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()

            val quarterDelta = quarter.left - original.left
            val threeQuartersDelta = threeQuarters.left - original.left
            val expectedSign = if (edge == NavigationEvent.EDGE_LEFT) 1 else -1
            assertTrue(
                "The content should follow the gesture direction",
                quarterDelta.value * expectedSign > 0f,
            )
            assertTrue(
                "More progress should move the content farther",
                abs(threeQuartersDelta.value) > abs(quarterDelta.value),
            )
            assertTrue(
                "Less progress should move the content back",
                abs(quarterAgain.left.value - original.left.value) < abs(threeQuartersDelta.value),
            )
            assertTrue(
                "Gesture translation should stay within 24 dp",
                abs(threeQuartersDelta.value) <= 24f,
            )

            compose.runOnIdle { input.backCancelled() }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            assertEquals(original, compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot())
            compose.runOnIdle { assertEquals(0, callbackCount) }
            compose.mainClock.autoAdvance = true
        }
    }

    /** Completion invokes the callback once, and the toolbar uses the same callback. */
    @Test
    fun completedGestureAndToolbarBackInvokeTheSameCallback() {
        val input = DirectNavigationEventInput()
        val callbackCount = mutableIntStateOf(0)
        compose.setContent {
            WithNavigationInput(input) {
                AppScreen(title = "Page", onBack = { callbackCount.intValue++ }) {
                    ScreenContent()
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        startBack(input, NavigationEvent.EDGE_LEFT)
        progressBack(input, NavigationEvent.EDGE_LEFT, 0.8f)
        compose.runOnIdle { input.backCompleted() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, callbackCount.intValue) }

        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(2, callbackCount.intValue) }
    }

    /** Disabled Back does not move or call back, then responds after it is enabled. */
    @Test
    fun disabledBackDoesNotMoveAndCanBeEnabledLater() {
        val input = DirectNavigationEventInput()
        val callbackCount = mutableIntStateOf(0)
        lateinit var setBackEnabled: (Boolean) -> Unit
        compose.setContent {
            var backEnabled by remember { mutableStateOf(false) }
            setBackEnabled = { backEnabled = it }
            WithNavigationInput(input) {
                AppScreen(
                    title = "Page",
                    onBack = { callbackCount.intValue++ },
                    backEnabled = backEnabled,
                ) {
                    ScreenContent()
                }
            }
        }
        compose.waitForIdle()
        val original = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        startBack(input, NavigationEvent.EDGE_LEFT)
        progressBack(input, NavigationEvent.EDGE_LEFT, 0.8f)
        assertEquals(original, compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot())
        compose.runOnIdle { input.backCompleted() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, callbackCount.intValue) }

        compose.runOnIdle { setBackEnabled(true) }
        compose.mainClock.advanceTimeBy(16)
        startBack(input, NavigationEvent.EDGE_LEFT)
        progressBack(input, NavigationEvent.EDGE_LEFT, 0.8f)
        val enabledBounds = compose.onNodeWithTag(SCREEN_TAG).getUnclippedBoundsInRoot()
        assertTrue(enabledBounds.left > original.left)
        compose.runOnIdle { input.backCompleted() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, callbackCount.intValue) }
        compose.mainClock.autoAdvance = true
    }

    /** A screen without Back handling leaves the completed event to its parent handler. */
    @Test
    fun missingBackCallbackFallsThroughToParentHandler() {
        val input = DirectNavigationEventInput()
        val callbackCount = mutableIntStateOf(0)
        compose.setContent {
            WithNavigationInput(input) {
                val parentState = rememberNavigationEventState(NavigationEventInfo.None)
                NavigationBackHandler(
                    state = parentState,
                    onBackCompleted = { callbackCount.intValue++ },
                )
                AppScreen(title = "Page") { ScreenContent() }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { input.backCompleted() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, callbackCount.intValue) }
    }

    /**
     * Installs a direct navigation input for a test's composed screen.
     * @param input Synthetic source driven by the test.
     * @param content Screen and optional parent event handlers.
     */
    @Composable
    private fun WithNavigationInput(
        input: DirectNavigationEventInput,
        content: @Composable () -> Unit,
    ) {
        val dispatcher = remember { NavigationEventDispatcher() }
        val owner =
            remember(dispatcher) {
                object : NavigationEventDispatcherOwner {
                    override val navigationEventDispatcher = dispatcher
                }
            }
        DisposableEffect(dispatcher, input) {
            dispatcher.addInput(input)
            onDispose { dispatcher.dispose() }
        }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            content = content,
        )
    }

    /** Adds visible content whose bounds expose shared screen translation. */
    @Composable
    private fun ScreenContent() {
        Box(Modifier.fillMaxWidth().height(CONTENT_HEIGHT_DP.dp).testTag(SCREEN_TAG))
    }

    /**
     * Starts a synthetic Back gesture.
     * @param input Synthetic source attached to this screen.
     * @param edge Physical edge where Back begins.
     */
    private fun startBack(
        input: DirectNavigationEventInput,
        edge: Int,
    ) {
        compose.runOnIdle { input.backStarted(NavigationEvent(swipeEdge = edge)) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    /**
     * Updates a synthetic Back gesture.
     * @param input Synthetic source attached to this test's screen.
     * @param edge Physical edge where Back began.
     * @param progress Normalized gesture progress.
     */
    private fun progressBack(
        input: DirectNavigationEventInput,
        edge: Int,
        progress: Float,
    ) {
        compose.runOnIdle {
            input.backProgressed(NavigationEvent(swipeEdge = edge, progress = progress))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    /** Defines stable selectors and fixture dimensions for the tests. */
    private companion object {
        const val SCREEN_TAG = "screen-content"
        const val CONTENT_HEIGHT_DP = 80
    }
}

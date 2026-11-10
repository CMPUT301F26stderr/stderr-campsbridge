package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Checks gesture progress and cancellation through Navigation Compose's event handler. */
class PredictivePageMotionTest {
    // The shared emulator runner disables system animations for ordinary interaction tests.
    @get:Rule val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 1f
                },
        )

    /** Either edge slides full-size pages; cancellation keeps the entry and completion pops it. */
    @Test
    fun predictiveBackSlidesFromEitherEdgeAndCanCancel() {
        val input = DirectNavigationEventInput()
        lateinit var controller: NavHostController
        compose.setContent {
            val dispatcher = remember { NavigationEventDispatcher() }
            val owner =
                remember {
                    object : NavigationEventDispatcherOwner {
                        override val navigationEventDispatcher = dispatcher
                    }
                }
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.dispose() }
            }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                controller = rememberNavController()
                AppNavHost(controller, "home") {
                    composable("home") {
                        AppScreen("Home") {
                            Box(Modifier.fillMaxSize().background(Color.Red).testTag("home"))
                        }
                    }
                    composable("detail") {
                        AppScreen("Detail", onBack = { controller.popBackStack() }) {
                            Box(Modifier.fillMaxSize().background(Color.Blue).testTag("detail"))
                        }
                    }
                }
            }
        }
        compose.runOnIdle { controller.navigate("detail") }
        compose.waitForIdle()
        val original = compose.onNodeWithTag("detail").getUnclippedBoundsInRoot()
        val entry = compose.runOnIdle { requireNotNull(controller.currentBackStackEntry).id }
        compose.mainClock.autoAdvance = false

        progressBack(input, NavigationEvent.EDGE_LEFT)
        val dragged = compose.onNodeWithTag("detail").getUnclippedBoundsInRoot()
        assertTrue("Left-edge Back should slide the page right", dragged.left > original.left)
        assertTrue("The gesture should still be in progress", dragged.left < original.right)
        assertEquals((original.right - original.left).value, (dragged.right - dragged.left).value, 0.01f)
        assertEquals((original.bottom - original.top).value, (dragged.bottom - dragged.top).value, 0.01f)
        compose.runOnIdle { input.backCancelled() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertEquals(original, compose.onNodeWithTag("detail").getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(entry, controller.currentBackStackEntry?.id) }

        progressBack(input, NavigationEvent.EDGE_RIGHT)
        val rightDragged = compose.onNodeWithTag("detail").getUnclippedBoundsInRoot()
        assertTrue("Right-edge Back should slide the page left", rightDragged.left < original.left)
        assertEquals((original.right - original.left).value, (rightDragged.right - rightDragged.left).value, 0.01f)
        assertEquals((original.bottom - original.top).value, (rightDragged.bottom - rightDragged.top).value, 0.01f)
        compose.runOnIdle { input.backCompleted() }
        compose.mainClock.advanceTimeBy(80)
        val leaving = compose.onNodeWithTag("detail").getUnclippedBoundsInRoot()
        assertTrue("Completed Back should exit right from either gesture edge", leaving.left > original.left)
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag("detail").assertDoesNotExist()
        assertEquals(original, compose.onNodeWithTag("home").getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals("home", controller.currentDestination?.route) }
    }

    /** Delivers separate frames so the host can prepare and seek its predictive transition.
     * @param input Event source attached to this test's host.
     * @param edge Physical screen edge where Back begins.
     */
    private fun progressBack(
        input: DirectNavigationEventInput,
        edge: Int,
    ) {
        compose.runOnIdle { input.backStarted(NavigationEvent(swipeEdge = edge)) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            input.backProgressed(NavigationEvent(swipeEdge = edge, progress = 0.5f))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }
}

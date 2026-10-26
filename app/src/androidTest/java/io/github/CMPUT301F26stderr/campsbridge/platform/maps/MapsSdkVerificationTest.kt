package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapView
import io.github.CMPUT301F26stderr.campsbridge.BuildConfig
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the real SDK when explicitly requested; ordinary keyless CI verifies truthful fallback. */
class MapsSdkVerificationTest {
    @get:Rule val compose = createComposeRule()

    /** Real taps and marker drags require Apply, while a later Cancel preserves the parent pin. */
    @Test fun mapConfigurationAndExplicitPinOwnership() {
        val required =
            InstrumentationRegistry.getArguments().getString("requireConfiguredMaps") == "true"
        if (required) {
            assertTrue(
                "Configured Maps verification requires a build-time key",
                BuildConfig.MAPS_CONFIGURED,
            )
        }
        var applied: Coordinates? = null
        var cancellations = 0
        val generation = mutableIntStateOf(0)
        compose.setContent {
            CampsbridgeTheme {
                key(generation.intValue) {
                    LocationPicker(applied, { applied = it }, { cancellations++ })
                }
            }
        }
        compose.onNodeWithText("No location selected").assertIsDisplayed()
        compose.onNodeWithText("Use this location").assertIsNotEnabled()
        if (!required) {
            assertTrue("Ordinary UI verification must remain keyless", !BuildConfig.MAPS_CONFIGURED)
            compose
                .onNodeWithText(
                    "Maps is not configured in this build. You can return without changing this draft.",
                ).assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle {
                assertNull(applied)
                assertEquals(1, cancellations)
            }
            return
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            ConnectionResult.SUCCESS,
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context),
        )
        awaitLoadedMap()
        tapMap(0.5f, 0.5f)
        compose.waitUntil(10_000) {
            !compose
                .onNodeWithText("Use this location")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.Disabled)
        }
        compose.onNodeWithText("Use this location").assertIsEnabled().performClick()
        compose.runOnIdle { assertNotNull(applied) }
        val tapped = checkNotNull(applied)
        compose.runOnIdle { generation.intValue++ }
        awaitLoadedMap()
        onView(isAssignableFrom(MapView::class.java)).perform(DragWorkingMarker())
        awaitWorkingPinChange(tapped)
        compose.onNodeWithText("Use this location").performClick()
        compose.runOnIdle {
            assertNotEquals(
                "Dragging the SDK marker must change its working coordinate",
                tapped,
                applied,
            )
        }
        val dragged = checkNotNull(applied)
        compose.runOnIdle { generation.intValue++ }
        awaitLoadedMap()
        tapMap(0.7f, 0.65f)
        val previousLabel =
            context.getString(
                R.string.campsite_coordinates,
                dragged.latitude,
                dragged.longitude,
            )
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(previousLabel).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(dragged, applied)
            assertEquals(1, cancellations)
        }
    }

    /** Requires the SDK surface and its actual onMapLoaded callback, without replacing map content. */
    private fun awaitLoadedMap() {
        compose.waitUntil(30_000) {
            var mounted = false
            onView(isRoot()).check { view, _ -> mounted = containsMap(view) }
            mounted
        }
        onView(
            isAssignableFrom(MapView::class.java),
        ).check(
            androidx.test.espresso.assertion.ViewAssertions
                .matches(isDisplayed()),
        )
        compose.waitUntil(60_000) {
            compose
                .onAllNodesWithText(
                    "Loading map. If it does not load, check your connection and Maps configuration.",
                ).fetchSemanticsNodes()
                .isEmpty()
        }
    }

    /** Waits for the native drag callback to reach the same working-state presentation.
     * @param previous Applied pin before the native gesture.
     */
    private fun awaitWorkingPinChange(previous: Coordinates) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label =
            context.getString(
                R.string.campsite_coordinates,
                previous.latitude,
                previous.longitude,
            )
        compose.waitUntil(
            10_000,
        ) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty() }
    }

    /** Waits for asynchronous AndroidView mounting without substituting an SDK callback.
     * @param view Current Android hierarchy node.
     */
    private fun containsMap(view: View): Boolean =
        view is MapView || (
            view is ViewGroup &&
                (0 until view.childCount).any {
                    containsMap(view.getChildAt(it))
                }
        )

    /** Sends a native touch into the real MapView at a stable relative point.
     * @param x Horizontal fraction within the rendered map.
     * @param y Vertical fraction within the rendered map.
     */
    private fun tapMap(
        x: Float,
        y: Float,
    ) {
        onView(isAssignableFrom(MapView::class.java)).perform(
            GeneralClickAction(Tap.SINGLE, { view ->
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                floatArrayOf(location[0] + view.width * x, location[1] + view.height * y)
            }, Press.FINGER),
        )
    }

    /** Long-presses the centered SDK marker body and drags it through native motion events. */
    private class DragWorkingMarker : ViewAction {
        /** Restricts native gestures to a visible SDK surface. */
        override fun getConstraints(): Matcher<View> =
            allOf(isAssignableFrom(MapView::class.java), isDisplayed())

        /** Names the actual gesture in failed instrumentation diagnostics. */
        override fun getDescription(): String =
            "long press and drag the working Maps marker"

        /** Injects real touch events; the reopened map centers its camera on the existing pin.
         * @param uiController Espresso's native input and main-thread synchronization.
         * @param view Actual SDK MapView.
         */
        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val density = view.resources.displayMetrics.density
            val x = location[0] + view.width / 2f
            val y = location[1] + view.height / 2f - 20f * density
            val downTime = SystemClock.uptimeMillis()
            send(uiController, downTime, MotionEvent.ACTION_DOWN, x, y)
            uiController.loopMainThreadForAtLeast(1_000)
            for (step in 1..12) {
                send(uiController, downTime, MotionEvent.ACTION_MOVE, x + 6f * density * step, y)
                uiController.loopMainThreadForAtLeast(40)
            }
            send(uiController, downTime, MotionEvent.ACTION_UP, x + 72f * density, y)
            uiController.loopMainThreadUntilIdle()
        }

        /** Recycles each injected event after the native SDK receives it.
         * @param uiController Native input owner.
         * @param downTime Start time shared by the gesture.
         * @param action Native motion action.
         * @param x Screen coordinate.
         * @param y Screen coordinate.
         */
        private fun send(
            uiController: UiController,
            downTime: Long,
            action: Int,
            x: Float,
            y: Float,
        ) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue(uiController.injectMotionEvent(event))
            } finally {
                event.recycle()
            }
        }
    }
}

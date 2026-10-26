package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Exercises the actual picker host using deterministic SDK tap callbacks, never a fake save. */
class LocationPickerTest {
    @get:Rule val compose = createComposeRule()
    private val chosen = Coordinates(51.2, -115.6)

    /** A camera default is not a selection; Cancel leaves the parent unchanged after a tap. */
    @Test fun newPinRequiresExplicitApplyAndCancelDoesNotChangeParent() {
        var applied: Coordinates? = null
        var cancellations = 0
        compose.setContent {
            CampsbridgeTheme {
                LocationPicker(null, { applied = it }, { cancellations++ }, ::TestMap)
            }
        }
        compose.onNodeWithText("No location selected").assertIsDisplayed()
        compose.onNodeWithText("Use this location").assertIsNotEnabled()
        compose.onNodeWithText("Move pin").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(applied)
            assertEquals(1, cancellations)
        }
    }

    /** Recomposition from parent facts cannot overwrite the user's unconfirmed pin. */
    @Test fun parentRefreshDoesNotOverwriteWorkingPin() {
        val initial = mutableStateOf(Coordinates(53.0, -118.0))
        var applied: Coordinates? = null
        compose.setContent {
            CampsbridgeTheme {
                LocationPicker(initial.value, { applied = it }, {}, ::TestMap)
            }
        }
        compose.onNodeWithText("Move pin").performClick()
        compose.runOnIdle { initial.value = Coordinates(54.0, -119.0) }
        compose.onNodeWithText("Use this location").performClick()
        compose.runOnIdle { assertEquals(chosen, applied) }
    }

    /** Primitive state restores the working pin rather than reinitializing from the parent. */
    @Test fun recreationRetainsUnappliedPin() {
        val restoration = StateRestorationTester(compose)
        var applied: Coordinates? = null
        restoration.setContent {
            CampsbridgeTheme {
                LocationPicker(Coordinates(53.0, -118.0), { applied = it }, {}, ::TestMap)
            }
        }
        compose.onNodeWithText("Move pin").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Use this location").performClick()
        compose.runOnIdle { assertEquals(chosen, applied) }
    }

    /** Both platform Back and toolbar Back abandon working changes without an Apply callback. */
    @Test fun bothBackActionsCancelOnly() {
        var applied: Coordinates? = null
        var cancellations = 0
        compose.setContent {
            CampsbridgeTheme {
                LocationPicker(null, { applied = it }, { cancellations++ }, ::TestMap)
            }
        }
        compose.onNodeWithText("Move pin").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        pressBack()
        compose.runOnIdle {
            assertNull(applied)
            assertEquals(2, cancellations)
        }
    }

    /** Missing configuration never starts the SDK or invents a selectable success surface. */
    @Test fun unconfiguredMapKeepsTruthfulNoSelectionAndBack() {
        var applied: Coordinates? = null
        var cancellations = 0
        compose.setContent {
            CampsbridgeTheme {
                LocationPicker(
                    null,
                    { applied = it },
                    { cancellations++ },
                ) { pin, select, modifier ->
                    CampsiteMapSurface(pin, select, modifier, configured = false)
                }
            }
        }
        compose
            .onNodeWithText(
                "Maps is not configured in this build. You can return without changing this draft.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Use this location").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(applied)
            assertEquals(1, cancellations)
        }
    }

    /** Replaces only SDK interaction with one explicit coordinate callback.
     * @param pin Current working state supplied by the real host.
     * @param select The same action invoked by native tap/drag.
     * @param modifier Layout from the real screen.
     */
    @Composable
    private fun TestMap(
        pin: Coordinates?,
        select: (Coordinates) -> Unit,
        modifier: Modifier,
    ) {
        TextButton({ select(chosen) }, modifier) { Text("Move pin") }
    }
}

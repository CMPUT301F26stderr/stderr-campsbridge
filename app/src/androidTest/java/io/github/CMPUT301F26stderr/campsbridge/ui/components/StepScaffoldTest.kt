package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Contracts for short steps and reachable actions at large text sizes. */
class StepScaffoldTest {
    @get:Rule val compose = createComposeRule()

    /** The step supplies its caller's title/progress and explicit next action. */
    @Test fun shortStepShowsProgressAndDeliversNext() {
        var next = 0
        compose.setContent {
            CampsbridgeTheme {
                StepScaffold("Your details", "Step 1 of 3", {}, { next++ }) {
                    Text("Choose how you want to camp.")
                }
            }
        }
        compose.onNodeWithText("Your details").assertIsDisplayed()
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    /** Large text and long content retain a visible, working bottom action. */
    @Test fun largeTextKeepsNextReachable() {
        var next = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CampsbridgeTheme(settings = AccountSettings(fontScale = 1.25, controlScale = 1.5)) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        StepScaffold("Your camping preferences", "Step 2 of 3", {}, { next++ }) {
                            repeat(20) { Text("A long description of a camping preference.") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    /** The visible keyboard must not cover the focused step's explicit continuation action. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun keyboardKeepsNextReachable() {
        var keyboardVisible = false
        var next = 0
        compose.setContent {
            CampsbridgeTheme {
                val visible = WindowInsets.isImeVisible
                SideEffect { keyboardVisible = visible }
                val name = remember { mutableStateOf("") }
                StepScaffold("Your details", "Step 1 of 3", {}, { next++ }) {
                    AppTextField(name.value, { name.value = it }, "Name")
                }
            }
        }
        compose.onNodeWithText("Name").performClick()
        compose.waitUntil(5_000) { keyboardVisible }
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }
}

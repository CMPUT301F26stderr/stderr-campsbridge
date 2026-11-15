package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepTransition
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Keeps keyboard and modal Back handling ahead of page navigation. */
class ScreenBackPlatformTest {
    @get:Rule val compose = createComposeRule()

    /** Material dialogs consume Back before the underlying screen can leave. */
    @Test fun modalDismissesBeforePageBack() {
        var backs = 0
        compose.setContent {
            CampsbridgeTheme {
                var dialog by remember { mutableStateOf(false) }
                AppScreen("Page", onBack = { backs++ }) {
                    Button(onClick = { dialog = true }) { Text("Open dialog") }
                }
                if (dialog) {
                    AlertDialog(
                        onDismissRequest = { dialog = false },
                        confirmButton = { Button(onClick = { dialog = false }) { Text("Keep editing") } },
                        text = { Text("Unsaved input") },
                    )
                }
            }
        }
        compose.onNodeWithText("Open dialog").performClick()
        compose.onNodeWithText("Unsaved input").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Unsaved input").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backs) }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    /** The first Android Back hides the keyboard and the next invokes page Back. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun keyboardClosesBeforePageBack() {
        var backs = 0
        var keyboardVisible = false
        compose.setContent {
            CampsbridgeTheme {
                val imeVisible = WindowInsets.isImeVisible
                SideEffect { keyboardVisible = imeVisible }
                var text by remember { mutableStateOf("") }
                AppScreen("Editor", onBack = { backs++ }) {
                    OutlinedTextField(text, { text = it }, Modifier.testTag("editor-field"))
                }
            }
        }
        compose.onNodeWithTag("editor-field").performClick()
        compose.waitUntil(10_000) { keyboardVisible }
        Espresso.pressBack()
        compose.waitUntil(10_000) { !keyboardVisible }
        compose.runOnIdle { assertEquals(0, backs) }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    /** Only the current form step handles Back while old content remains in its exit animation. */
    @Test fun outgoingStepCannotHandleBack() {
        val handled = mutableListOf<Int>()
        compose.setContent {
            CampsbridgeTheme {
                var step by remember { mutableStateOf(0) }
                StepTransition(step, { it }) { visible ->
                    AppScreen("Step $visible", onBack = { handled += visible }) {
                        if (visible == 0) Button(onClick = { step = 1 }) { Text("Next step") }
                    }
                }
            }
        }
        compose.onNodeWithText("Next step").performClick()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(listOf(1), handled) }
    }
}

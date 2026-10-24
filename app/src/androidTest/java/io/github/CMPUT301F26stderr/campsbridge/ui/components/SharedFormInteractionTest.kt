package io.github.CMPUT301F26stderr.campsbridge.ui.components

import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Acceptance contracts for shared form feedback and confirmation actions. */
class SharedFormInteractionTest {
    @get:Rule val compose = createComposeRule()

    /** Editing reports the value and preserves the supplied error text. */
    @Test fun fieldReportsInputAndShowsError() {
        var received = ""
        compose.setContent {
            CampsbridgeTheme {
                val value = remember { mutableStateOf("") }
                AppTextField(
                    value.value,
                    {
                        value.value = it
                        received = it
                    },
                    "Name",
                    errorMessage = "A name is required",
                )
            }
        }
        compose.onNodeWithText("Name").performTextInput("Justin")
        compose.onNodeWithText("Name").assertTextContains("Justin")
        compose.onNodeWithText("A name is required").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Justin", received) }
    }

    /** Failure content invokes retry while retaining the caller's content. */
    @Test fun retryPreservesExistingContent() {
        var retries = 0
        compose.setContent {
            CampsbridgeTheme {
                AppScreen("Profile") {
                    Text("Existing profile")
                    FailureContent("Unable to save", { retries++ })
                }
            }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Existing profile").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    /** Confirmation and explicit cancellation reach distinct callbacks. */
    @Test fun confirmationAndCancellationAreDistinct() {
        var confirmed = 0
        var cancelled = 0
        compose.setContent {
            CampsbridgeTheme {
                ConfirmationDialog(
                    "Remove photo?",
                    "The current photo will be removed.",
                    { confirmed++ },
                    { cancelled++ },
                    {},
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, confirmed)
        }
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    /** A pending destructive operation cannot be confirmed twice. */
    @Test fun busyConfirmationDisablesActions() {
        compose.setContent {
            CampsbridgeTheme {
                ConfirmationDialog("Remove photo?", "Please wait.", {}, {}, {}, isSubmitting = true)
            }
        }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    /** Back dismissal is separate from cancel/confirm and is blocked while submitting. */
    @Test fun backDismissalRespectsSubmissionAndHasItsOwnCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val submitting = mutableStateOf(true)
        var confirmed = 0
        var cancelled = 0
        var dismissed = 0
        compose.setContent {
            CampsbridgeTheme {
                ConfirmationDialog(
                    "Remove photo?",
                    "The current photo will be removed.",
                    { confirmed++ },
                    { cancelled++ },
                    { dismissed++ },
                    isSubmitting = submitting.value,
                )
            }
        }
        compose.onNodeWithText("Remove photo?").assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.runOnIdle {
            assertEquals(0, dismissed)
            submitting.value = false
        }
        compose.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.runOnIdle { dismissed == 1 } }
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(0, confirmed)
            assertEquals(0, cancelled)
        }
    }
}

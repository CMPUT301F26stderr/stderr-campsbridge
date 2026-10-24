package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Contracts for action callbacks and submission feedback shared by all forms. */
class AppActionButtonTest {
    @get:Rule val compose = createComposeRule()

    /** An enabled action delivers one user tap to its owner. */
    @Test fun enabledActionDeliversTap() {
        var calls = 0
        compose.setContent {
            MaterialTheme { AppActionButton("Save", { calls++ }) }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    /** A disabled action does not deliver a physical tap. */
    @Test fun disabledActionDoesNotDeliverTap() {
        var calls = 0
        compose.setContent {
            MaterialTheme { AppActionButton("Save", { calls++ }, enabled = false) }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    /** Submission exposes progress and blocks a repeated user action. */
    @Test fun submissionBlocksTapAndExposesProgress() {
        var calls = 0
        compose.setContent {
            MaterialTheme {
                AppActionButton(
                    "Save",
                    { calls++ },
                    isSubmitting = true,
                    progressDescription = "Saving photo",
                )
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription("Saving photo").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}

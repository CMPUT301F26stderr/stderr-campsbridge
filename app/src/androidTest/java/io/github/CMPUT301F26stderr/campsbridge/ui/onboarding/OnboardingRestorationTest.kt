package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.CMPUT301F26stderr.campsbridge.demo.OnboardingPreviewActivity
import org.junit.Rule
import org.junit.Test

/** Verifies production Navigation/ViewModel ownership through actual activity recreation. */
class OnboardingRestorationTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** Recreation retains draft values; Back returns to the chosen intent. */
    @Test fun recreationPreservesTheSameFlowDraft() {
        val intent =
            Intent(
                ApplicationProvider.getApplicationContext(),
                OnboardingPreviewActivity::class.java,
            ).putExtra("interactive", true)
        ActivityScenario.launch<OnboardingPreviewActivity>(intent).use { scenario ->
            compose.onNodeWithText("Get started").performClick()
            compose.onNodeWithText("Manage campsites").performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Name").performTextInput("Unsubmitted draft")
            scenario.recreate()
            compose.onNodeWithText("Unsubmitted draft").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Manage campsites").assertIsSelected()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Unsubmitted draft").assertIsDisplayed()
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

/** Exercises the two B2 task entry points without onboarding, Firebase, or peer helpers. */
class CampsiteTaskExamplesFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** The actual editor protects the booked fixture and retains a withdrawal until H03 is supplied. */
    @Test fun availabilityExampleUsesKnownCalendarAndKeepsMissingHelperPending() {
        ActivityScenario.launch(TaskExamplesActivity::class.java).use {
            compose.onNodeWithTag("task-catalogue").performScrollToNode(hasTestTag("H03"))
            compose.onNodeWithTag("H03").performClick()
            compose.onNodeWithText("May 2027").assertIsDisplayed()
            compose.onNodeWithTag("calendar-date-2027-05-13").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("calendar-date-2027-05-11").performScrollTo().performClick()
            compose.onNodeWithText("Withdraw nights").performScrollTo().performClick()
            compose
                .onNodeWithText(
                    "Saving date changes is pending in this build. Your date choices are retained.",
                ).performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithText("Save changes").assertIsNotEnabled()
        }
    }

    /** The real owner inventory keeps its original records visible and returns to the catalogue. */
    @Test fun orderingExampleShowsActualRowsAndExplicitPendingOrdering() {
        ActivityScenario.launch(TaskExamplesActivity::class.java).use {
            compose.onNodeWithTag("task-catalogue").performScrollToNode(hasTestTag("H04"))
            compose.onNodeWithTag("H04").performClick()
            compose
                .onNodeWithText(
                    "Alphabetical ordering is pending in this build.",
                ).assertIsDisplayed()
            compose.onNodeWithText("River Camp").assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("Task examples").assertIsDisplayed()
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import io.github.CMPUT301F26stderr.campsbridge.demo.OfflineDemoActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises task navigation, reset, recreation, and pending states without Firebase. */
class TaskExamplesFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** The ordinary launcher exposes the task catalogue and an honest future-admin placeholder. */
    @Test fun menuOpensTasksAndFutureBaselinePlaceholder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<OfflineDemoActivity>(intent).use {
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("Task examples").performClick()
            awaitText("Task examples")
            compose.onNodeWithTag("task-catalogue").performScrollToNode(hasTestTag("H15"))
            compose.onNodeWithTag("H15").performClick()
            compose
                .onNodeWithText(
                    "Example pending baseline B9. " +
                        "Its feature or handoff contract is still in progress.",
                ).assertIsDisplayed()
            compose.onNodeWithText("All tasks").performClick()
            compose.onNodeWithTag("H01").assertIsDisplayed()
            assertTrue(FirebaseApp.getApps(context).isEmpty())
        }
    }

    /** Retains editor input on recreation and resets it independently of helper readiness. */
    @Test fun nameExampleRetainsInputAndResetRestoresFixtures() {
        ActivityScenario.launch(TaskExamplesActivity::class.java).use { scenario ->
            compose.onNodeWithTag("H01").performClick()
            compose.onNodeWithText("Name").performTextReplacement("changed")
            scenario.recreate()
            compose.onNodeWithText("changed").assertIsDisplayed()
            compose.onNodeWithText("Reset example").performClick()
            compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        }
    }

    /** Back from a real feature returns to the catalogue without completing onboarding. */
    @Test fun contactExampleOpensDirectlyAndBackReturnsToTasks() {
        ActivityScenario.launch(TaskExamplesActivity::class.java).use {
            compose.onNodeWithTag("task-catalogue").performScrollToNode(hasTestTag("H02"))
            compose.onNodeWithTag("H02").performClick()
            compose.onNodeWithText("Contact details").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("Task examples").assertIsDisplayed()
        }
    }

    /** Waits for an Android Activity transition before asserting its visible target.
     * @param text Unique visible destination label.
     */
    private fun awaitText(text: String) {
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithText(text).isDisplayed()
            } catch (
                notReady: IllegalStateException,
            ) {
                if (notReady.message?.startsWith("No compose hierarchies found") ==
                    true
                ) {
                    false
                } else {
                    throw notReady
                }
            }
        }
    }
}

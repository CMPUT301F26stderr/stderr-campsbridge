package io.github.CMPUT301F26stderr.campsbridge.demo

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the same default launcher Android Studio runs, with no external Firebase services. */
class OfflineDemoTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** A fresh launch has real content and the visible menu changes and resets scenarios. */
    @Test fun defaultLauncherIsOfflineAndOffersScenarios() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertTrue(intent.component!!.className.endsWith(".OfflineDemoActivity"))
        ActivityScenario.launch<OfflineDemoActivity>(intent).use { scenario ->
            compose.onNodeWithText("Offline demo").assertIsDisplayed()
            compose.onNodeWithText("Profile").performClick()
            compose.onNodeWithText("Alex Chen").assertIsDisplayed()
            assertTrue(FirebaseApp.getApps(context).isEmpty())
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("Existing owner").performClick()
            compose.onNodeWithText("My campsites").assertIsDisplayed()
            compose.onNodeWithText("River Camp").assertIsDisplayed()
            compose.onNodeWithText("Drafts").performClick()
            compose.onNodeWithText("Forest clearing").assertIsDisplayed()
            compose.onNodeWithText("Profile").performClick()
            compose.onNodeWithText("Jordan Lee").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText("Jordan Lee").assertIsDisplayed()
            compose.onNodeWithText("Campsites").performClick()
            compose.onNodeWithText("Drafts").assertIsSelected()
            compose.onNodeWithText("Forest clearing").assertIsDisplayed()
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("Reset scenario").performClick()
            compose.onNodeWithText("My campsites").assertIsDisplayed()
            compose.onNodeWithText("Profile").performClick()
            compose.onNodeWithText("Jordan Lee").assertIsDisplayed()
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("New account").performClick()
            compose.onNodeWithText("Get started").performClick()
            compose.onNodeWithText("Find a campsite").performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Name").performTextInput("Alex")
            assertTrue(FirebaseApp.getApps(context).isEmpty())
        }
    }

    /** Screen inspection is accessible from the UI without completing reserved behavior. */
    @Test fun previewsAreReachableFromTheMenu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<OfflineDemoActivity>(intent).use { scenario ->
            compose.onNodeWithText("Demo tools").performClick()
            compose.onNodeWithText("Onboarding previews").performClick()
            compose.onNodeWithText("Preferences").performClick()
            awaitPreview("What kind of camping do you enjoy?")
            compose.onNodeWithText("What kind of camping do you enjoy?").assertIsDisplayed()
            assertTrue(FirebaseApp.getApps(context).isEmpty())
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { scenario.state == Lifecycle.State.RESUMED }
            compose.onNodeWithText("Profile").performClick()
            compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        }
    }

    /** Waits for the target content across an asynchronous Android Activity/window handoff.
     * @param text Visible destination text still asserted by the caller after this bounded wait.
     */
    private fun awaitPreview(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            try {
                compose.onNodeWithText(text).isDisplayed()
            } catch (notReady: IllegalStateException) {
                if (notReady.message?.startsWith("No compose hierarchies found") == true) {
                    false
                } else {
                    throw notReady
                }
            }
        }
    }
}

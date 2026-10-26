package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Real root, routes and ViewModels, using explicit persistence/name-contract test doubles. */
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Cancels every model after the interaction finishes. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** An entrant saves one profile, optionally skips interests and cannot Back into setup. */
    @Test fun entrantCompletesAndSetupLeavesBackHistory() {
        host.accounts.account.value = null
        host.accounts.allowWrites = true
        compose.setContent { host.Content() }
        enterName(owner = false, name = "Alex")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("What kind of camping do you enjoy?").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                SetupProgress.IN_PROGRESS,
                host.preferences.progress.value.setup,
            )
        }
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Get started").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.accounts.createCalls)
            assertEquals(SetupProgress.COMPLETED, host.preferences.progress.value.setup)
        }
    }

    /** Owner activation and workspace completion are real operations, not a local role label. */
    @Test fun ownerCompletesWithConfirmedCapability() {
        host.accounts.account.value = null
        host.accounts.allowWrites = true
        compose.setContent { host.Content() }
        enterName(owner = true, name = "Host")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Ready to add your campsite?").assertIsDisplayed()
        compose.onNodeWithText("Finish setup").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Host").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(
                Role.OWNER in
                    host.accounts.account.value!!
                        .roles,
            )
            assertEquals(Role.OWNER, host.preferences.progress.value.workspace)
        }
    }

    /** Failed saves keep the name and retry reaches interests without a second identity. */
    @Test fun creationFailureRetainsInputAndRetries() {
        host.accounts.account.value = null
        host.accounts.allowWrites = true
        host.accounts.createFailure = IOException("offline")
        compose.setContent { host.Content() }
        enterName(owner = false, name = "Alex")
        compose.onNodeWithText("Continue").performClick()
        compose
            .onNodeWithText("Could not save your changes. Your input is still here. Try again.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Alex").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { host.accounts.createFailure = null }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("What kind of camping do you enjoy?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.identity.calls) }
    }

    /** Production's pending validation dependency stops the flow without profile writes. */
    @Test fun pendingParserDoesNotCreateAProfile() {
        host.accounts.account.value = null
        host.parseName = null
        compose.setContent { host.Content() }
        enterName(owner = false, name = "Alex")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, host.accounts.writes) }
    }

    /** Uses visible production actions to arrive at the name field.
     * @param owner Whether to select the owner starting intent.
     * @param name Fixed name supplied by the test-only name contract.
     */
    private fun enterName(
        owner: Boolean,
        name: String,
    ) {
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText(if (owner) "Manage campsites" else "Find a campsite").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Name").performTextInput(name)
    }
}

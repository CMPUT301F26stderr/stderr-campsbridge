package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Verifies production root branches and navigation lifetime through controlled dependencies. */
class AppNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases real ViewModel subscriptions after each interaction test. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** Identity loading does not expose a fabricated profile destination. */
    @Test fun pendingIdentityShowsStartup() {
        host.identity.pending = CompletableDeferred()
        compose.setContent { host.Content() }
        compose.onNodeWithText("Starting Campsbridge").assertIsDisplayed()
        compose.onNodeWithText("Alex Chen").assertDoesNotExist()
    }

    /** A missing profile enters welcome without inventing a completed profile. */
    @Test fun missingProfileShowsWelcome() {
        host.accounts.account.value = null
        compose.setContent { host.Content() }
        compose.onNodeWithText("Get started").assertIsDisplayed()
        compose.onNodeWithText("Alex Chen").assertDoesNotExist()
    }

    /** Startup retry reaches the existing profile using the real root model. */
    @Test fun startupFailureCanRetry() {
        host.identity.failure = IOException("offline")
        compose.setContent { host.Content() }
        compose.onNodeWithText("Could not start Campsbridge").assertIsDisplayed()
        compose.runOnIdle { host.identity.failure = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, host.identity.calls) }
    }

    /** Account updates keep one destination/model instead of pushing another profile. */
    @Test fun accountUpdatesKeepOneProfileDestination() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            host.accounts.account.value =
                sampleAccount().copy(
                    name = "Taylor Lee",
                )
        }
        compose.onAllNodesWithText("Taylor Lee").assertCountEquals(1)
        compose.onNodeWithText("Alex Chen").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.profileCreations)
            assertEquals(1, host.identity.calls)
        }
    }

    /** Workspace menu actions persist an actual capability without rebuilding navigation. */
    @Test fun workspaceSelectionUsesTheExistingSession() {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        compose.setContent { host.Content() }
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNode(hasText("Campsite owner") and hasClickAction()).performClick()
        compose
            .onNodeWithContentDescription(
                "Switch workspace",
            ).assertTextContains("Campsite owner")
        compose.runOnIdle {
            assertEquals(1, host.preferences.workspaceWrites)
            assertEquals(1, host.profileCreations)
            assertEquals(1, host.identity.calls)
        }
    }

    /** Removed accounts immediately lose the previous feature content. */
    @Test fun removedAccountHidesProfile() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            host.accounts.account.value =
                sampleAccount().copy(removal = Removal(Instant.EPOCH, "admin"))
        }
        compose.onNodeWithText("Account unavailable").assertIsDisplayed()
        compose.onNodeWithText("Alex Chen").assertDoesNotExist()
    }
}

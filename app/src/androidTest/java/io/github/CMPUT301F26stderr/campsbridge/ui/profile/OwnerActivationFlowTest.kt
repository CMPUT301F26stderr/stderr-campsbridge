package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Existing-profile activation through the real root, local workspace selection and owner graph. */
class OwnerActivationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val original = sampleAccount().copy(phone = "7805550123")

    /** Cancels every pending account/local write and graph-owned observation. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Opening and leaving the introduction performs no profile, role or workspace mutation. */
    @Test fun introductionCanBeCancelledWithoutWriting() {
        start()
        compose.onNodeWithText("Create a listing when you are ready.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(original.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original, host.accounts.account.value)
            assertEquals(0, host.accounts.writes)
            assertEquals(0, host.preferences.workspaceWrites)
        }
    }

    /** Confirmed activation opens actual campsite creation, and switching back does not revive the introduction. */
    @Test fun heldActivationEntersTheRealOwnerWorkspaceAndPreservesTheAccount() {
        val gate = CompletableDeferred<Unit>()
        host.accounts.ownerGate = gate
        start()
        activate()
        compose.onNodeWithContentDescription("Enabling hosting").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        pressBack()
        compose.runOnIdle {
            assertEquals(1, host.accounts.ownerWrites)
            assertEquals(0, host.preferences.workspaceWrites)
            gate.complete(Unit)
        }
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.onNodeWithText("Create campsite").performClick()
        compose.onNodeWithText("Campsite name").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNodeWithText("Camper").performClick()
        compose.onNodeWithText(original.name).assertIsDisplayed()
        compose.onNodeWithText("Create a listing when you are ready.").assertDoesNotExist()
        compose.onNodeWithText("Start hosting").assertDoesNotExist()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                original.copy(roles = original.roles + Role.OWNER),
                host.accounts.account.value,
            )
            assertEquals(listOf(original.id), host.accounts.ownerIds)
            assertEquals(0, host.accounts.createCalls)
            assertEquals(0, host.accounts.profileWrites)
        }
    }

    /** A capability failure remains in the introduction and retries that action explicitly. */
    @Test fun failedActivationRetainsTheIntroductionForRetry() {
        host.accounts.ownerFailure = IOException("offline")
        start()
        activate()
        compose
            .onNodeWithText(
                "Could not confirm hosting. Your profile is retained. Retry to check the same account.",
            ).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, host.preferences.workspaceWrites)
            assertEquals(original, host.accounts.account.value)
            host.accounts.ownerFailure = null
        }
        compose.onNodeWithText("Retry activation").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, host.accounts.ownerWrites) }
    }

    /** A failed local selection retries only selection, retaining the already granted capability. */
    @Test fun failedWorkspaceSelectionNeverRepeatsActivation() {
        host.preferences.writeFailure = IOException("local write failed")
        start()
        activate()
        compose.onNodeWithText("Retry workspace switch").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.accounts.ownerWrites)
            assertEquals(1, host.preferences.workspaceWrites)
            assertTrue(
                Role.OWNER in
                    host.accounts.account.value!!
                        .roles,
            )
            host.preferences.writeFailure = null
        }
        compose.onNodeWithText("Retry workspace switch").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.accounts.ownerWrites)
            assertEquals(2, host.preferences.workspaceWrites)
        }
    }

    /** Root recovery after a cancelled local selection keeps an explicit selection retry reachable. */
    @Test fun transientRootFailureDoesNotTrapTheConfirmedOwnerRequest() {
        host.preferences.writeGate = CompletableDeferred()
        start()
        activate()
        compose.onNodeWithContentDescription("Opening owner workspace").assertIsDisplayed()
        compose.runOnIdle { host.accounts.streamFailure.value = IOException("account offline") }
        compose.onNodeWithText("Could not start Campsbridge").assertIsDisplayed()
        compose.runOnIdle { host.accounts.streamFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Could not load profile").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNode(hasText("Start hosting") and hasClickAction()).assertIsEnabled()
        compose.runOnIdle { host.preferences.writeGate = null }
        activate()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.accounts.ownerWrites)
            assertEquals(2, host.preferences.workspaceWrites)
        }
    }

    /** Opens the actual profile child without restarting name/identity setup. */
    private fun start() {
        host.accounts.account.value = original
        host.accounts.allowWrites = true
        compose.setContent { host.Content() }
        compose.onNodeWithText("Start hosting").performScrollTo().performClick()
    }

    /** Activates only the introduction's primary button, distinct from its matching title. */
    private fun activate() {
        compose.onNode(hasText("Start hosting") and hasClickAction()).performClick()
    }
}

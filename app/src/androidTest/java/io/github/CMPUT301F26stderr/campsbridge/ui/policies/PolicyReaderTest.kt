package io.github.CMPUT301F26stderr.campsbridge.ui.policies

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production settings/readers work offline with real Back and no account writes. */
class PolicyReaderTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases graph-owned listeners after each reader interaction. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** Settings links readers and each Back returns to its actual menu. */
    @Test fun settingsReadersHaveCompleteCopyAndReturnToCaller() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Policies").performClick()
        compose.onNodeWithText("Guidelines").performClick()
        compose
            .onNodeWithText(
                "Respect campsites and follow the owner’s rules.",
            ).assertIsDisplayed()
        compose
            .onNodeWithText(
                "Join a lottery for a chance to book. Winners share available dates first-come.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Payments are simulated. Cancelling or rejecting a reservation returns the full simulated payment.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Reservation & refund policy").performClick()
        compose
            .onNodeWithText(
                "Payments are simulated. Cancelling or rejecting a reservation returns the full simulated payment.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Each win allows one reservation, even after cancellation.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, host.accounts.writes)
            assertEquals(1, host.profileCreations)
        }
    }

    /** Onboarding uses the same full reader without advancing or creating an account. */
    @Test fun welcomeReaderReturnsWithoutAdvancingSetup() {
        host.accounts.account.value = null
        compose.setContent { host.Content() }
        compose.onNodeWithText("Guidelines").performClick()
        compose.onNodeWithText("App guidelines").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Respect campsites and follow the owner’s rules.",
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Get started").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.accounts.writes) }
    }

    /** Unknown historical text has a recoverable reader rather than a current-policy substitute. */
    @Test fun unavailableVersionRetainsBackWithoutShowingCurrentText() {
        var backs = 0
        compose.setContent {
            CampsbridgeTheme {
                PolicyReaderScreen(
                    policyContent(PolicyId.REFUND, "unknown"),
                    { backs++ },
                )
            }
        }
        compose.onNodeWithText("Policy unavailable").assertIsDisplayed()
        compose
            .onNodeWithText(
                "This policy version is not included in this build.",
            ).assertIsDisplayed()
        compose
            .onNodeWithText(
                "Each win allows one reservation, even after cancellation.",
            ).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}

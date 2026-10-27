package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Production profile graph interactions with controlled persistence and fixed parser fixtures. */
class ProfileEditingTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Cancels real route/controller subscriptions after each interaction. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** Primary and editor share one model but each owns exactly one visible frame. */
    @Test fun menuAndEditorsHaveOneFrameAndActualBack() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onAllNodes(hasText("Profile") and !hasClickAction()).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        compose.onNodeWithText("Personal details").performClick()
        compose.onAllNodesWithText("Personal details").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Contact details").performClick()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Phone").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.profileCreations)
            assertEquals(0, host.accounts.writes)
        }
    }

    /** Failed save keeps typed text; explicit retry navigates once after confirmation. */
    @Test fun nameSaveFailureRetainsInputAndRetryReturnsToMenu() {
        host.accounts.allowWrites = true
        host.accounts.profileFailure = IOException("offline")
        host.parseName = { DisplayNameInput.Valid("Taylor") }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Name").performTextReplacement(" Taylor ")
        compose.onNodeWithText("Save name").performClick()
        compose
            .onNodeWithText(
                "Could not save your changes. Your input is still here. Try again.",
            ).assertIsDisplayed()
        compose.onNodeWithText(" Taylor ").assertIsDisplayed()
        compose.runOnIdle { host.accounts.profileFailure = null }
        compose.onNodeWithText("Save name").performClick()
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(2, host.accounts.profileWrites)
            assertEquals(1, host.profileCreations)
        }
    }

    /** A name save blocks photo actions and both toolbar/system exit while persistence waits. */
    @Test fun pendingFieldSaveDisablesConflictingControls() {
        host.accounts.allowWrites = true
        host.accounts.profileGate = CompletableDeferred()
        host.parseName = { DisplayNameInput.Valid("Taylor") }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Save name").performClick()
        compose.onNodeWithText("Save name").assertIsNotEnabled()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, host.accounts.profileWrites)
            host.accounts.profileGate!!.complete(Unit)
        }
        compose.onNodeWithText("Taylor").assertIsDisplayed()
    }

    /** Contact clearing uses real save wiring; Cancel discards a later unsaved edit. */
    @Test fun contactSaveAndCancelHaveSeparateEffects() {
        host.accounts.allowWrites = true
        host.parseEmail = { OptionalContactInput.Valid(null) }
        host.parsePhone = { OptionalContactInput.Valid("123") }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Contact details").performClick()
        compose.onNodeWithText("Email").performTextReplacement("")
        compose.onNodeWithText("Phone").performTextReplacement("123")
        compose.onNodeWithText("Save contacts").performClick()
        compose.onNodeWithText("Contact details").performClick()
        compose.onNodeWithText("Phone").performTextReplacement("unsaved")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            assertEquals(
                null,
                host.accounts.account.value
                    ?.email,
            )
            assertEquals(
                "123",
                host.accounts.account.value
                    ?.phone,
            )
            assertEquals(1, host.accounts.profileWrites)
        }
    }

    /** Missing reserved parsers are explained without blocking image inspection or field edits. */
    @Test fun reservedValidationIsVisibleAndDoesNotFakeSave() {
        host.parseName = null
        host.parseEmail = null
        host.parsePhone = null
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Pending")
        compose.onNodeWithText("Save name").assertIsNotEnabled()
        compose.onNodeWithText("Name validation is pending in this build.").assertIsDisplayed()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Contact details").performClick()
        compose.onNodeWithText("Save contacts").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, host.accounts.writes) }
    }

    /** Live account data refreshes the menu without rebuilding the parent graph model. */
    @Test fun dirtyEditorSurvivesAccountRefresh() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Unsaved")
        compose.runOnIdle {
            host.accounts.account.value =
                sampleAccount().copy(name = "Current saved")
        }
        compose.onNodeWithText("Unsaved").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Current saved").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.profileCreations) }
    }
}

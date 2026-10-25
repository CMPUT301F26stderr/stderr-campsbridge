package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.ControlledPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the real setup parent, step contents, native selection and reader return. */
class CampsiteSetupFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val draft = sampleCampsite("draft % 1", published = false)

    /** Local setup never reports a fake saved operation and releases its host. */
    @After fun close() {
        compose.runOnIdle {
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(0, host.images.replaceCalls)
            assertEquals(0, host.images.deleteCalls)
            host.close()
        }
    }

    /** New local input validates only current required fields, with real discard/dismiss
     * behavior.
     */
    @Test fun createValidatesAndDiscardReturnsToActualCaller() {
        start()
        compose.onNodeWithText("Create campsite").performClick()
        compose.onNodeWithText("Step 1 of 8").assertIsDisplayed()
        compose.onNodeWithText("Campsites").assertDoesNotExist()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Enter a campsite name.").assertIsDisplayed()
        compose.onNodeWithText("Campsite name").performTextReplacement("New Camp")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 2 of 8").assertIsDisplayed()
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Step 3 of 8").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose
            .onNodeWithText(
                "Choose the campsite location on the map.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard campsite changes?").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel")[1].performClick()
        compose.onNodeWithText("Step 3 of 8").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.readIds.size) }
    }

    /** Saved-ID setup has eight actual positions and returns from policy to the same draft
     * owner.
     */
    @Test fun savedDraftWalksEightStepsAndPolicyReturnsToRules() {
        start()
        resumeDraft()
        for (position in 1..5) {
            compose.onNodeWithText("Step $position of 8").assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
        }
        compose.onNodeWithText("Rules (optional)").performTextReplacement("Keep this new rule")
        compose.onNodeWithText("Read the refund policy").performScrollTo().performClick()
        compose.onNodeWithText("Simulated payments and refunds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Keep this new rule").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 7 of 8").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 8 of 8").assertIsDisplayed()
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.onNodeWithText("Save draft").assertIsEnabled()
        compose.onNodeWithText("Edit Introduce your campsite").performScrollTo().performClick()
        compose.onNodeWithText("Campsite name").performTextReplacement("Edited River")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 8 of 8").assertIsDisplayed()
        compose.onNodeWithText("Edited River").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.campsiteSetupCreations) }
    }

    /** Native Photo Picker changes only local intent; cancellation keeps the selected preview. */
    @Test fun pickerCancellationRetainsSelectionWithoutUploading() {
        val picker = ControlledPhotoPicker(fixturePhotoUri())
        start(picker)
        resumeDraft()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Photo selected").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { picker.result = null }
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.onNodeWithText("Photo selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Discard photo change").performScrollTo().performClick()
        compose.onNodeWithText("Photo selected").assertDoesNotExist()
    }

    /** Unchanged saved input can exit without a prompt and retains its actual management caller. */
    @Test fun unchangedDraftExitReturnsToManagementAndSelectedListTab() {
        start()
        resumeDraft()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard campsite changes?").assertDoesNotExist()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText("River Camp").assertIsDisplayed()
    }

    /** Step Back retains input and first-step Back asks before discarding meaningful edits. */
    @Test fun activeStepBackPreservesInputAndAsksBeforeExit() {
        start()
        compose.onNodeWithText("Create campsite").performClick()
        compose.onNodeWithText("Campsite name").performTextReplacement("Back test camp")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Step 1 of 8").assertIsDisplayed()
        compose.onNodeWithText("Back test camp").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard campsite changes?").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel")[1].performClick()
        compose.onNodeWithText("Back test camp").assertIsDisplayed()
    }

    /** Child/source failure remains retryable and doesn't fabricate another saved draft. */
    @Test fun savedTargetFailureRetainsActualBackAndRetry() {
        start()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("River Camp").performClick()
        compose.runOnIdle { host.campsites.readFailure = java.io.IOException("offline") }
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
        compose.onNodeWithText("Could not load this campsite.").assertIsDisplayed()
        compose.runOnIdle { host.campsites.readFailure = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Step 1 of 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(draft.id, draft.id), host.campsites.readIds) }
    }

    /** The actual location child returns to one draft owner without changing label or zone. */
    @Test fun locationChildReturnsToSameSavedDraft() {
        start()
        resumeDraft()
        repeat(2) { compose.onNodeWithText("Next").performClick() }
        compose
            .onNodeWithText(
                "Location label (optional)",
            ).performTextReplacement("Keep this label")
        compose.onNodeWithText("Choose location").performScrollTo().performClick()
        compose.onNodeWithText("Choose campsite location").assertIsDisplayed()
        compose.onNodeWithText("Use this location").performClick()
        compose.onNodeWithText("Step 3 of 8").assertIsDisplayed()
        compose.onNodeWithText("Keep this label").assertIsDisplayed()
        compose.onNodeWithText("America/Edmonton").assertIsDisplayed()
        compose.onNodeWithText("Choose location").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Step 3 of 8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.campsiteSetupCreations) }
    }

    /** Starts the real owner graph with controlled repositories.
     * @param picker Optional native picker result fixture.
     */
    private fun start(picker: ControlledPhotoPicker? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(draft)
        compose.setContent {
            if (picker == null) {
                host.Content()
            } else {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides picker,
                ) { host.Content() }
            }
        }
    }

    /** Opens encoded saved identity through its real list and management links. */
    private fun resumeDraft() {
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("River Camp").performClick()
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
    }
}

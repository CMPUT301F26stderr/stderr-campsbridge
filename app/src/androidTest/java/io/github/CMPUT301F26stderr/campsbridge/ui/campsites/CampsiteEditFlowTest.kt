package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Exercises the actual owner graph, section retention, explicit updates and native photo selection. */
class CampsiteEditFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("published % 146")

    @After fun close() {
        compose.runOnIdle {
            assertTrue(host.campsites.createRequests.isEmpty())
            assertTrue(host.campsites.publishRequests.isEmpty())
            assertTrue(host.campsites.offerRequests.isEmpty())
            host.close()
        }
    }

    @Test fun saveFailureRetainsInputThenConfirmedUpdateReopensWithSavedValues() {
        start()
        openEditor()
        openSection("Name and description")
        compose.onNodeWithText("Campsite name").performTextReplacement("Edited River")
        back()
        compose.runOnIdle { host.campsites.updateHandler = { _, _, _ -> throw IOException("offline") } }
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Could not save details. Your edits are kept for retry.").assertIsDisplayed()
        openSection("Name and description")
        compose.onNodeWithText("Edited River").assertIsDisplayed()
        back()
        compose.runOnIdle {
            host.campsites.updateHandler =
                { _, _, details -> host.campsites.records.value = listOf(site.copy(details = details)) }
        }
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Campsite details saved.").assertIsDisplayed()
        compose.onNodeWithText("Save details").assertIsNotEnabled()
        back()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("Edited River").assertIsDisplayed()
        compose.onNodeWithText("Edit campsite").performScrollTo().performClick()
        openSection("Name and description")
        compose.onNodeWithText("Edited River").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, host.campsites.updateRequests.size)
            assertEquals(
                site.publishedAt,
                host.campsites.records.value
                    .single()
                    .publishedAt,
            )
            assertEquals(0, host.images.replaceCalls)
        }
    }

    @Test fun sharedValidationAndDiscardKeepSavedValuesAlone() {
        start()
        openEditor()
        openSection("Capacity and price")
        compose.onNodeWithText("Nightly price").performTextReplacement("12.345")
        back()
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Check the highlighted fields before saving.").assertIsDisplayed()
        openSection("Capacity and price")
        compose.onNodeWithText("12.345").assertIsDisplayed()
        compose.onNodeWithText("Enter a nonnegative CAD amount with at most two decimal places.").assertIsDisplayed()
        back()
        back()
        compose.onNodeWithText("Discard campsite edits?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Save details").assertIsDisplayed()
        back()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(site), host.campsites.records.value)
            assertEquals(0, host.campsites.writeCalls)
        }
    }

    @Test fun photoSaveIsIndependentAndCancellationRetainsSelection() {
        val picker = ControlledPhotoPicker(fixturePhotoUri())
        start(picker)
        openEditor()
        openSection("Name and description")
        compose.onNodeWithText("Campsite name").performTextReplacement("Unsaved details")
        back()
        openSection("Campsite photo")
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Save photo").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { picker.result = null }
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.onNodeWithText("Save photo").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.images.replaceCalls) }
        compose.onNodeWithText("Save photo").performClick()
        compose.onNodeWithText("Remove photo").performScrollTo().assertIsDisplayed()
        back()
        back()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.images.replaceCalls)
            assertEquals(0, host.campsites.writeCalls)
            assertEquals(
                site.id,
                host.images.replacementTargets
                    .single()
                    .id,
            )
            assertEquals(
                site.details,
                host.campsites.records.value
                    .single()
                    .details,
            )
        }
    }

    @Test fun policyAndAvailabilityReturnToRetainedDetailsWithoutSavingThem() {
        start()
        openEditor()
        openSection("Rules")
        compose.onNodeWithText("Rules (optional)").performTextReplacement("Quiet at dusk")
        compose.onNodeWithText("Read the refund policy").performScrollTo().performClick()
        compose.onNodeWithText("Simulated payments and refunds").assertIsDisplayed()
        back()
        compose.onNodeWithText("Quiet at dusk").assertIsDisplayed()
        back()
        openSection("Availability")
        back()
        openSection("Rules")
        compose.onNodeWithText("Quiet at dusk").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
    }

    @Test fun failedInitialReadRetriesWithoutCreatingListing() {
        start()
        compose.runOnIdle { host.campsites.readFailure = IOException("offline") }
        openEditor()
        compose.onNodeWithText("Could not load this campsite.").assertIsDisplayed()
        compose.runOnIdle { host.campsites.readFailure = null }
        compose.onNodeWithText("Retry").performClick()
        openSection("Name and description")
        compose.onNodeWithText("River Camp").assertIsDisplayed()
    }

    private fun start(picker: ControlledPhotoPicker? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(site)
        compose.setContent {
            if (picker !=
                null
            ) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) { host.Content() }
            } else {
                host.Content()
            }
        }
    }

    private fun openEditor() {
        compose.onNodeWithText("River Camp").performClick()
        compose.onNodeWithText("Edit campsite").performScrollTo().performClick()
        compose.onNodeWithText("Step 1 of 8").assertDoesNotExist()
    }

    private fun openSection(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
    }

    private fun back() {
        compose.onNodeWithContentDescription("Back").performClick()
    }
}

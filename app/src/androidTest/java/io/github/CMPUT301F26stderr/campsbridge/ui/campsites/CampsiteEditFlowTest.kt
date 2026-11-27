/*
 * Saved editing journeys through the real management graph and shared child routes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
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

/** Saved editing journeys through the real management graph and shared child routes. */
class CampsiteEditFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite()

    /** Releases the real root and its feature controllers after each journey. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** CE-04: location Cancel/Apply and dirty Back preserve input until the confirmed update. */
    @Test fun editsPublishedDetailsAndReturnsToTheSameManager() {
        start()
        compose.onNodeWithText("Basics").performClick()
        compose.onNodeWithText("Campsite name").performTextReplacement("River Camp family pitches")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Location").performClick()
        compose.onNodeWithText("Location label (optional)").performTextReplacement("Near Jasper townsite")
        compose.onNodeWithText("Choose location").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Near Jasper townsite").assertIsDisplayed()
        compose.onNodeWithText("Choose location").performClick()
        compose.onNodeWithText("Use this location").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard campsite changes?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("River Camp family pitches").assertIsDisplayed()
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("River Camp family pitches").assertIsDisplayed()
        compose.runOnIdle {
            val request = host.campsites.updateRequests.single()
            assertEquals(site.id, request.first)
            assertEquals(site.ownerId, request.second)
            assertEquals("Near Jasper townsite", request.third.locationLabel)
            assertEquals(site.details.location, request.third.location)
            assertTrue(host.campsites.createRequests.isEmpty())
            assertTrue(host.campsites.publishRequests.isEmpty())
        }
    }

    /** CE-05: photo saves/removal are independent and details never submit a pending choice. */
    @Test fun photoChangesDoNotSaveOrDiscardRawDetails() {
        start(ControlledPhotoPicker(fixturePhotoUri()))
        compose.onNodeWithText("Basics").performClick()
        compose.onNodeWithText("Campsite name").performTextReplacement("River Camp family pitches")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Photo").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        awaitPhotoSelection()
        compose.onNodeWithText("Save photo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("River Camp family pitches").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(ImageTarget(ImageTargetType.CAMPSITE, site.id)), host.images.replacementTargets)
            assertTrue(host.campsites.updateRequests.isEmpty())
        }
        compose.onNodeWithText("Photo").performClick()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("River Camp family pitches").assertIsDisplayed()
        compose.onNodeWithText("Photo").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        awaitPhotoSelection()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Unsaved photo change").assertIsDisplayed()
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Unsaved photo change").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.campsites.updateRequests.size)
            assertEquals(
                "River Camp family pitches",
                host.campsites.updateRequests
                    .single()
                    .third.name,
            )
            assertEquals(1, host.images.replaceCalls)
            assertEquals(listOf(ImageTarget(ImageTargetType.CAMPSITE, site.id)), host.images.removalTargets)
        }
    }

    /** Waits for the native content read, which is outside the Compose idle scheduler. */
    private fun awaitPhotoSelection() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Save photo").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Opens actual saved management with controlled persistence and native picker results.
     * @param picker Optional successful native image selection.
     */
    private fun start(picker: ControlledPhotoPicker? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(site)
        host.campsites.updateHandler = { id, _, details ->
            host.campsites.records.value =
                host.campsites.records.value
                    .map { if (it.id == id) it.copy(details = details) else it }
        }
        compose.setContent {
            if (picker == null) {
                host.Content()
            } else {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) { host.Content() }
            }
        }
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Edit campsite").performScrollTo().performClick()
    }
}

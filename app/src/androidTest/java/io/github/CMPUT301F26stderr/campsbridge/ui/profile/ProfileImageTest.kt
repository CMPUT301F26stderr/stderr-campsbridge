package io.github.CMPUT301F26stderr.campsbridge.ui.profile

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
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.ControlledPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Real profile route, ViewModel, controller, and content reader with isolated persistence. */
class ProfileImageTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases model/controller work after each case. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** Waits for IO-backed selection/rendering through an observable UI condition.
     * @param text Localized text expected in the current screen.
     */
    private fun waitForText(text: String) {
        compose.waitUntil(
            5_000,
        ) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Picking creates only a preview, and cancelling another picker preserves that candidate. */
    @Test fun selectionAndPickerCancellationDoNotUpload() {
        val picker = ControlledPhotoPicker(fixturePhotoUri())
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides picker,
            ) { host.Content() }
        }
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        waitForText("Save photo")
        compose.onNodeWithContentDescription("Selected photo preview").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, host.images.replaceCalls)
            picker.result = null
        }
        compose.onNodeWithText("Choose photo").performClick()
        compose.onNodeWithText("Save photo").assertIsDisplayed()
        compose.onNodeWithText("Discard selection").performClick()
        waitForText("No photo yet")
        compose.runOnIdle {
            assertEquals(0, host.images.replaceCalls)
            assertEquals(0, host.images.deleteCalls)
        }
    }

    /** Saving is explicit, shows busy controls, and displays a confirmed image afterward. */
    @Test fun saveUsesTheRealControllerAndBlocksRepeatedActions() {
        val uri = fixturePhotoUri()
        val picker = ControlledPhotoPicker(uri)
        host.images.uploadGate = CompletableDeferred()
        host.images.urlResults["blob-1"] = uri.toString()
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides picker,
            ) { host.Content() }
        }
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        waitForText("Save photo")
        compose.onNodeWithText("Save photo").performClick()
        compose.onNodeWithText("Save photo").assertIsNotEnabled()
        compose.onNodeWithText("Choose photo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Saving photo").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.images.replaceCalls)
            host.images.uploadGate!!.complete(Unit)
        }
        waitForText("Remove photo")
        compose.onNodeWithContentDescription("Profile photo").assertIsDisplayed()
    }

    /** Failure leaves the local preview in place and an explicit retry can save it. */
    @Test fun failedSaveCanRetryWithoutRepicking() {
        val uri = fixturePhotoUri()
        val picker = ControlledPhotoPicker(uri)
        host.images.replaceFailure = IOException("offline")
        host.images.urlResults["blob-2"] = uri.toString()
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides picker,
            ) { host.Content() }
        }
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        waitForText("Save photo")
        compose.onNodeWithText("Save photo").performClick()
        waitForText("Could not save photo")
        compose.onNodeWithContentDescription("Selected photo preview").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.images.replaceCalls)
            host.images.replaceFailure =
                null
        }
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        waitForText("Remove photo")
        compose.runOnIdle {
            assertEquals(2, host.images.replaceCalls)
            assertEquals(1, picker.launches)
        }
    }

    /** Removal requires confirmation; cancellation has no persistence effect. */
    @Test fun removalConfirmationAndCancellationAreDistinct() {
        val uri = fixturePhotoUri()
        host.images.attachment.value =
            ImageAttachment(
                ImageTarget(ImageTargetType.ACCOUNT, "account-1"),
                "existing",
                Instant.EPOCH,
            )
        host.images.urlResults["existing"] = uri.toString()
        compose.setContent { host.Content() }
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, host.images.deleteCalls) }
        compose.onNodeWithText("Remove photo").performClick()
        compose.onNodeWithText("Remove").performClick()
        waitForText("No photo yet")
        compose.runOnIdle { assertEquals(1, host.images.deleteCalls) }
    }

    /** Unresolved image publication stays retryable without trapping independent profile navigation. */
    @Test fun ambiguousPhotoCanBeLeftWithoutLosingRecovery() {
        val picker = ControlledPhotoPicker(fixturePhotoUri())
        host.images.replaceFailure =
            ImagePublicationException(
                ImageTarget(ImageTargetType.ACCOUNT, "account-1"),
                "uploaded",
                IOException("ack lost"),
            )
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides picker,
            ) { host.Content() }
        }
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        waitForText("Save photo")
        compose.onNodeWithText("Save photo").performClick()
        waitForText("The photo change is unconfirmed. Check again before making another change.")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Personal details").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, host.images.replaceCalls) }
    }
}

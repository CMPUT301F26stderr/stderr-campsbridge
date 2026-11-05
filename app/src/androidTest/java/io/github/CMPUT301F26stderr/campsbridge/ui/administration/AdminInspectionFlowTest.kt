package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Real navigation and shared target-slot reader with controlled independent sources. */
class AdminInspectionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val profile =
        sampleAccount(
            setOf(Role.OWNER),
        ).copy(id = "owner/alpha ?", name = "Taylor Owner")
    private val parents = FakeAccountRepository(profile)
    private val target = ImageTarget(ImageTargetType.ACCOUNT, profile.id)

    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    private fun openImage() {
        host.accounts.account.value = sampleAccount(setOf(Role.ADMIN))
        host.adminAccounts = parents
        host.images.attachment.value = ImageAttachment(target, "blob-1", Instant.EPOCH)
        host.images.urlResults["blob-1"] = fixturePhotoUri().toString()
        compose.setContent { host.Content() }
        compose.onNodeWithText("Images").performClick()
        compose.onNodeWithText("Profile \u00b7 ${target.id}").performClick()
        compose.onNodeWithText("Image inspection").assertIsDisplayed()
    }

    @Test fun parentAndPhotoRetriesAreIndependentAndOnlyConfirmedFactsEnableNavigation() {
        parents.streamFailure.value = IOException("parent offline")
        host.images.streamFailure.value = IOException("image offline")
        openImage()
        compose.onNodeWithText("Open profile").performScrollTo().assertIsNotEnabled()
        var slotReads = 0
        compose.runOnIdle {
            slotReads = host.images.observedTargets.size
            parents.streamFailure.value = null
        }
        compose
            .onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("admin_image_parent")))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Open profile").performScrollTo().assertIsEnabled()
        var parentReads = 0
        compose.runOnIdle {
            assertEquals(slotReads, host.images.observedTargets.size)
            parentReads = parents.observations
            host.images.streamFailure.value = null
        }
        compose
            .onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("admin_image_photo")))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Open profile").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(parentReads, parents.observations)
            assertEquals(0, parents.writes + host.images.replaceCalls + host.images.deleteCalls)
        }
        compose.onNodeWithText("Open profile").performClick()
        compose.onNodeWithText("Profile inspection").assertIsDisplayed()
        compose.onNodeWithText(profile.id).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Image inspection").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.adminBrowseCreations) }
    }

    @Test fun missingParentCannotBeOpenedAndLivePresenceAndDisappearanceUpdateTheLink() {
        parents.account.value = null
        openImage()
        compose
            .onNodeWithText(
                "The parent record is missing or unavailable.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Open profile").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { parents.account.value = profile }
        compose.onNodeWithText("Open profile").performScrollTo().assertIsEnabled()
        compose.runOnIdle { parents.account.value = null }
        compose.onNodeWithText("Open profile").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, parents.observations) }
    }

    @Test fun attachmentReplacementAndRemovalStayInTheSameSlotAndDoNotRestartItsParent() {
        openImage()
        compose.runOnIdle {
            host.images.urlResults["blob-2"] = fixturePhotoUri().toString()
            host.images.attachment.value =
                ImageAttachment(target, "blob-2", Instant.EPOCH.plusSeconds(60))
        }
        compose.waitUntil(5_000) { "blob-2" in host.images.downloads }
        compose.runOnIdle {
            assertEquals(1, parents.observations)
            host.images.attachment.value = null
        }
        compose
            .onNodeWithText(
                "No image is attached to this target.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Open profile").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No matching records").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, parents.activeObservers)
            assertEquals(0, host.images.activeObservers)
        }
    }

    @Test fun confirmedCampsiteParentOpensReadOnlyInspectionBySavedIdentity() {
        val site = sampleCampsite("site/one ?", profile.id)
        val siteTarget = ImageTarget(ImageTargetType.CAMPSITE, site.id)
        host.accounts.account.value = sampleAccount(setOf(Role.ADMIN))
        host.campsites.records.value = listOf(site)
        host.images.attachment.value = ImageAttachment(siteTarget, "blob-site", Instant.EPOCH)
        host.images.urlResults["blob-site"] = fixturePhotoUri().toString()
        compose.setContent { host.Content() }
        compose.onNodeWithText("Images").performClick()
        compose.onNodeWithText("Campsite \u00b7 ${site.id}").performClick()
        compose
            .onNodeWithText(
                "Inspect campsite",
            ).performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.onNodeWithText("Campsite inspection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Image inspection").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(host.images.observedTargets.all { it == siteTarget })
            assertEquals(0, host.campsites.writeCalls)
        }
    }
}

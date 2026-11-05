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

}

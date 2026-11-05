package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Saved attachment metadata owns gallery membership, even when its display fails. */
class AdminBrowseTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    @Test fun failedThumbnailsKeepMetadataRowsAndReturningRetainsTheImageQuery() {
        val target = ImageTarget(ImageTargetType.ACCOUNT, "owner/alpha ?")
        val attachment = ImageAttachment(target, "broken-blob", Instant.EPOCH)
        val metadata = MutableStateFlow(listOf(attachment))
        host.accounts.account.value = sampleAccount(setOf(Role.ADMIN))
        host.images.attachment.value = attachment
        host.images.urlFailures[attachment.blobId] = IOException("image offline")
        host.adminImages =
            object : ImageRepository by host.images {
                override fun observeAll() =
                    metadata
            }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Images").performClick()
        compose.onNodeWithText("Search").performTextInput("owner/")
        compose.onNodeWithText("Profile \u00b7 ${target.id}").assertIsDisplayed().performClick()
        compose.onNodeWithText("Image inspection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("owner/").assertIsDisplayed()
        compose.onNodeWithText("Profile \u00b7 ${target.id}").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.adminBrowseCreations)
            assertEquals(0, host.images.replaceCalls + host.images.deleteCalls)
            metadata.value = emptyList()
        }
        compose.onNodeWithText("No matching records").assertIsDisplayed()
    }

}

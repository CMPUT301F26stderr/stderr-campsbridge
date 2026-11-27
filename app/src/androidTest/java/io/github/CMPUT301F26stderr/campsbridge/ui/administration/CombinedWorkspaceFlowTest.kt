/*
 * Checks granted workspaces through the real root without changing identity.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesGuestCapacity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Checks granted workspaces through the real root without changing identity. */
class CombinedWorkspaceFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases only this root's retained feature models and readers. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** ML-03: granted admin/owner/entrant workspaces retain one identity and the same saved campsite. */
    @Test fun combinedAdminRolesSwitchWorkspacesWithoutReplacingIdentityOrSharedRecords() {
        val admin = sampleAccount(setOf(Role.ADMIN, Role.OWNER, Role.ENTRANT))
        val site = sampleCampsite("shared-site", admin.id)
        host.accounts.account.value = admin
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.ADMIN)
        host.campsites.records.value = listOf(site)
        host.matchesText = ::matchesCampsiteText
        host.matchesCapacity = ::matchesGuestCapacity
        compose.setContent { host.Content() }
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Campsite inspection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNode(hasText("Campsite owner") and hasClickAction()).performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.onNodeWithText(site.details.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNode(hasText("Camper") and hasClickAction()).performClick()
        compose.onNodeWithText(site.details.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNode(hasText("Administrator") and hasClickAction()).performClick()
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithText(admin.name).performScrollTo().performClick()
        compose.onNodeWithText(admin.id).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(admin, host.accounts.account.value)
            assertEquals(listOf(site), host.campsites.records.value)
            assertEquals(1, host.identity.calls)
            assertEquals(3, host.preferences.workspaceWrites)
        }
    }
}

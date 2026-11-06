/*
 * Tests admin graph ownership and encoded identities across read-only related navigation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real graph ownership, encoded identities and read-only related navigation. */
class AdminProfileNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    @Test fun relatedInspectionAndBackRetainOneBrowseParentAndItsInputs() {
        val profile =
            sampleAccount(
                setOf(Role.OWNER),
            ).copy(id = "camper/alpha ?", name = "Taylor Owner")
        val inspected = FakeAccountRepository(profile)
        val site = sampleCampsite("site/one ?", profile.id)
        host.accounts.account.value = sampleAccount(setOf(Role.ADMIN))
        host.adminAccounts = inspected
        host.campsites.records.value = listOf(site)
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithText("Search").performTextInput("Taylor")
        compose.onNode(hasText("Campsite owner") and hasClickAction()).performClick()
        compose.onNodeWithText(profile.name).performScrollTo().performClick()
        compose.onNodeWithText("Profile inspection").assertIsDisplayed()
        compose.onNodeWithText(profile.id).assertIsDisplayed()
        compose.onNodeWithText(site.details.name).performScrollTo().performClick()
        compose.onNodeWithText("Campsite inspection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Profile inspection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        compose.onNode(hasText("Campsite owner") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, host.adminBrowseCreations)
            assertEquals(0, inspected.activeObservers)
            assertEquals(
                0,
                inspected.writes + host.campsites.writeCalls + host.images.replaceCalls +
                    host.images.deleteCalls,
            )
        }
    }
}

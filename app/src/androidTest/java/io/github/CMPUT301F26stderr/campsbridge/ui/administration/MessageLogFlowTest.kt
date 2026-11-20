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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesGuestCapacity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** ML-02 and ML-03 use actual Logs/role navigation over one unchanged signed-in identity. */
class MessageLogFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases only this root's retained feature models and readers. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** ML-02: Messages search/detail/back/recreation retain selection without participant writes. */
    @Test fun realMessageLogKeepsItsTabQueryAndReadOnlyInspectionThroughRestoration() {
        val admin = sampleAccount(setOf(Role.ADMIN))
        val site = sampleCampsite("site/B ?1", "owner-B")
        val stay = previewReservation().copy(id = "stay/B ?1", campsiteId = site.id, entrantId = "entrant-E")
        val message =
            Message(
                "message/B ?1",
                stay.id,
                site.ownerId,
                "Meet at the east gate.\nWe will show you your pitch.",
                Instant.EPOCH,
            )
        var sends = 0
        host.accounts.account.value = admin
        host.accounts.getHandler =
            { id ->
                when (id) {
                    admin.id -> admin
                    site.ownerId -> sampleAccount(setOf(Role.OWNER)).copy(id = id, name = "Jordan Host")
                    else -> null
                }
            }
        host.campsites.records.value = listOf(site)
        host.bookings.records.value = listOf(stay)
        host.messages.records.value = listOf(message)
        host.messages.sendHandler = { _, _, _, _ ->
            sends++
            error("Logs cannot send")
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { host.Content() }
        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("Messages").performClick()
        compose.onNodeWithText("Search").performTextInput("east gate")
        compose.onNodeWithTag("admin-message-${message.id}").performScrollTo().performClick()
        compose.onNodeWithText("Message details").assertIsDisplayed()
        compose.onNodeWithText(message.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Messages").assertIsSelected()
        compose.onNodeWithText("east gate").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Messages").assertIsSelected()
        compose.onNodeWithText("east gate").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Filter notification events").assertIsDisplayed()
        compose.onNodeWithText("Messages").performClick()
        compose.onNodeWithTag("admin-message-${message.id}").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, sends)
            assertTrue(host.messages.readRequests.isEmpty())
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }
}

/*
 * Checks the Messages list through production Inbox navigation and the existing conversation screen.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** An unread message shows on the Inbox Messages tab, opens from the list and clears once read. */
class ConversationListFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")

    /** Releases the host's retained models and readers. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** The tab badge counts the unread message, the row opens its conversation, and reading clears both. */
    @Test fun unreadConversationOpensFromMessagesAndClearsAfterReading() {
        val reservation = previewReservation().copy(id = "message-stay", entrantId = "account-1")
        host.bookings.records.value = listOf(reservation)
        host.campsites.records.value = listOf(sampleCampsite(reservation.campsiteId, "owner"))
        host.messages.records.value = listOf(Message("gate", reservation.id, "owner", "Use the east gate", now))
        host.messages.markReadHandler = { id, _ ->
            host.messages.records.value =
                host.messages.records.value
                    .map { if (it.id == id) it.copy(readAt = now) else it }
        }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Inbox").performClick()
        compose
            .onNodeWithTag(
                "inbox-messages-tab",
                useUnmergedTree = true,
            ).assertContentDescriptionEquals("Messages, 1 unread")
        compose.onNodeWithTag("inbox-messages-tab", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("conversation-row-message-stay").performClick()
        compose.onNodeWithText("Use the east gate").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("gate" to "account-1"), host.messages.readRequests) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("conversation-row-message-stay").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 unread").assertDoesNotExist()
        compose.onNodeWithTag("inbox-messages-tab", useUnmergedTree = true).assertContentDescriptionEquals("Messages")
    }
}

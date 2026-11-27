/*
 * NE-04 drives real root message targets without substituting an unregistered conversation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** NE-04 drives real root message targets without substituting an unregistered conversation. */
class MessageNotificationEntryFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-10T18:00:00Z")

    /** Releases the root, saved graph owners and their observations. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** NE-04: a second tap must open its own stay before consumption, including restoration. */
    @Test fun messageTapOpensTheCorrectConversationOnceAndKeepsEventReadsSeparate() {
        val site = sampleCampsite("site/B ?1", "owner-B")
        val old = previewReservation().copy(id = "old/stay ?1", campsiteId = site.id, entrantId = "account-1")
        val current = old.copy(id = "current/stay ?2")
        val first = Message("old/message ?1", old.id, site.ownerId, "Earlier conversation", now)
        val next = Message("new/message ?2", current.id, site.ownerId, "Use the riverside gate", now.plusSeconds(1))
        val firstEvent =
            Notification(
                "old/event ?1",
                site.ownerId,
                NotificationType.NEW_MESSAGE,
                NotificationSubject(SubjectType.MESSAGE, first.id),
                null,
                now,
            )
        val nextEvent =
            firstEvent.copy(
                id = "new/event ?2",
                subject = NotificationSubject(SubjectType.MESSAGE, next.id),
            )
        val firstEntry = NotificationEntry("account-1", firstEvent.id, firstEvent.subject)
        val nextEntry = NotificationEntry("account-1", nextEvent.id, nextEvent.subject)
        host.campsites.records.value = listOf(site)
        host.bookings.records.value = listOf(old, current)
        host.messages.records.value = listOf(first, next)
        host.notifications.records.value = listOf(firstEvent, nextEvent)
        host.notificationEntries.accept(firstEntry)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { host.Content() }
        compose.onNodeWithText(first.body).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { host.notificationEntries.accept(nextEntry) }
        compose.onNodeWithText(next.body).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(next.body).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(NotificationEntryUiState.None, host.notificationEntries.state.value)
            assertEquals(listOf(firstEvent.id, nextEvent.id), host.notifications.getRequests)
            assertEquals(listOf(first.id to "account-1", next.id to "account-1"), host.messages.readRequests)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(NotificationEntryUiState.None, host.notificationEntries.state.value)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }
}

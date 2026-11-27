/*
 * ST-03 opens both actual target kinds from one retained owner Sent tab without recipient writes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** ST-03 opens both actual target kinds from one retained owner Sent tab without recipient writes. */
class SentNotificationsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-10T18:00:00Z")

    /** Releases the actual account navigation/controllers. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** ST-03: Sent selection survives both saved targets and recreation with no receipt semantics. */
    @Test fun ownerSentHistoryOpensLotteryAndConversationAndRetainsItsTab() {
        val site = sampleCampsite("site/B ?1", "account-1")
        val stay = previewReservation().copy(id = "stay/B ?1", campsiteId = site.id, entrantId = "camper")
        val lottery =
            Lottery(
                "draw/B ?1",
                "account-1",
                "Riverside stays",
                "Quiet stays",
                LotteryConfiguration(
                    now.plusSeconds(3600),
                    now.plusSeconds(7200),
                    now.plusSeconds(10800),
                    now.plusSeconds(14400),
                    2,
                    null,
                ),
                1,
                now,
                now,
            )
        val announcement =
            Notification(
                "announcement",
                "account-1",
                NotificationType.OWNER_ANNOUNCEMENT,
                NotificationSubject(SubjectType.LOTTERY, lottery.id),
                Announcement("Arrival details", "Use the east gate"),
                now,
            )
        val message = Message("message/B ?1", stay.id, "account-1", "We will meet you there", now.plusSeconds(60))
        val event =
            Notification(
                "message-event",
                "account-1",
                NotificationType.NEW_MESSAGE,
                NotificationSubject(SubjectType.MESSAGE, message.id),
                null,
                message.createdAt,
            )
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER, Role.ENTRANT))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.lotteries.records.value = listOf(lottery)
        host.campsites.records.value = listOf(site)
        host.bookings.records.value = listOf(stay)
        host.messages.records.value = listOf(message)
        host.notifications.records.value = listOf(announcement, event)
        host.notifications.sentHandler =
            { actor -> host.notifications.records.map { rows -> rows.filter { it.senderId == actor } } }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { host.Content() }
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("Sent").performClick()
        compose.onNodeWithTag("sent-event-${announcement.id}").performScrollTo().performClick()
        compose.onNodeWithText(lottery.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sent").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Sent").assertIsSelected()
        compose.onNodeWithTag("sent-event-${event.id}").performScrollTo().performClick()
        compose.onNodeWithText(message.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sent").assertIsSelected()
        compose.runOnIdle {
            assertTrue(host.notifications.readRequests.isEmpty())
            assertTrue(host.messages.readRequests.isEmpty())
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** AN-04 opens the actual saved owner context and confirms event storage without claiming delivery. */
class AnnouncementComposeFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val lottery =
        Lottery(
            "draw/A ?1",
            "account-1",
            "May riverside stays",
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

    /** Releases actual route/controller dependencies. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** AN-04: discard cancellation preserves text; failed storage offers retained same-ID recovery. */
    @Test fun ownerComposesFromSavedManagementAndReturnsOnlyAfterConfirmedStorage() {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.lotteries.records.value = listOf(lottery)
        host.lotteries.entries.value = listOf(LotteryEntry(lottery.id, "entrant", EntryState.WAITING, now, now))
        val attempts = mutableListOf<String>()
        host.notifications.announceHandler = { id, _, _, _ ->
            attempts += id
            throw IOException("offline")
        }
        compose.setContent { host.Content() }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(lottery.title).performClick()
        compose.onNodeWithText("Notify entrants").performScrollTo().performClick()
        compose.onNodeWithTag("announcement-title").performTextReplacement("Arrival details")
        compose.onNodeWithTag("announcement-body").performTextReplacement("Please use the east gate.\nThanks!")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Keep draft").performClick()
        compose.onNodeWithTag("announcement-body").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNodeWithText(lottery.title).assertIsDisplayed()
        compose.runOnIdle { assertTrue(attempts.isEmpty()) }
        compose.onNodeWithText("Notify entrants").performScrollTo().performClick()
        compose.onNodeWithTag("announcement-title").performTextReplacement("Arrival details")
        compose.onNodeWithTag("announcement-body").performTextReplacement("Please use the east gate.\nThanks!")
        compose.onNodeWithText("Send announcement").performScrollTo().performClick()
        compose.onNodeWithText("Retry announcement").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            host.notifications.announceHandler = { id, draw, actor, value ->
                attempts += id
                host.notifications.records.value +=
                    Notification(
                        id,
                        actor,
                        NotificationType.OWNER_ANNOUNCEMENT,
                        NotificationSubject(SubjectType.LOTTERY, draw),
                        value,
                        now,
                    )
                id
            }
        }
        compose.onNodeWithText("Retry announcement").performScrollTo().performClick()
        compose.onNodeWithText(lottery.title).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, attempts.size)
            assertEquals(attempts.first(), attempts.last())
            val stored =
                host.notifications.records.value
                    .single()
            assertEquals(lottery.id, stored.subject.id)
            assertEquals("account-1", stored.senderId)
            assertEquals("Arrival details", stored.announcement?.title)
            assertEquals("Please use the east gate.\nThanks!", stored.announcement?.body)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }
}

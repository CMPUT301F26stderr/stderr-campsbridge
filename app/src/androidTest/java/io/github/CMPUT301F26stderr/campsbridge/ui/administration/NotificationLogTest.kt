package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Fixed real-navigation list/detail, retry, restoration and large-text contracts. */
class NotificationLogTest {
    @get:Rule val compose = createComposeRule()
    private val f = NotificationLogUiFixture()

    /** Releases the actual account and feature graph after each interaction. */
    @After fun close() {
        compose.runOnIdle { f.host.close() }
    }

    /** Exact encoded IDs and raw filters survive detail restoration and actual Back. */
    @Test fun filtersAndDetailRestoreUnderOneParent() {
        val restoration = StateRestorationTester(compose)
        f.start(compose, restoration)
        compose.onNodeWithTag("notification-log-query").performTextReplacement("Trail")
        compose.onNodeWithTag("notification-log-query").performImeAction()
        compose.onNodeWithText("Event type").performClick()
        compose.onNode(hasText("Lottery announcement") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithTag("notification-log-event:${f.event.id}").performScrollTo().performClick()
        compose.onNodeWithText(f.event.id).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(f.event.id).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("notification-log-query").assertTextContains("Trail")
        compose.onNodeWithText("Lottery announcement").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, f.host.notificationLogCreations)
            assertEquals(1, f.listRequests)
            assertEquals(listOf(f.event.id), f.host.notifications.getRequests)
            assertTrue(
                f.host.notifications.readRequests
                    .isEmpty(),
            )
        }
    }

    /** Optional context failure preserves saved announcement facts and retries only selected content. */
    @Test fun failedContextKeepsEventFactsAndRetriesInPlace() {
        f.host.lotteries.getHandler = { throw IOException("context offline") }
        f.start(compose)
        compose.onNodeWithTag("notification-log-event:${f.event.id}").performClick()
        compose.onNodeWithText("Bring drinking water.").assertIsDisplayed()
        compose.onNodeWithText("Could not load the linked record.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { f.host.lotteries.getHandler = { null } }
        compose.onNodeWithText("Retry event details").performScrollTo().performClick()
        compose.onNodeWithText("The linked record is unavailable.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, f.listRequests)
            assertEquals(2, f.host.notifications.getRequests.size)
            assertTrue(
                f.host.notifications.readRequests
                    .isEmpty(),
            )
        }
    }

    /** A failed all-event source is not empty; explicit Retry can establish confirmed emptiness. */
    @Test fun failedListRetriesToConfirmedEmpty() {
        f.failure.value = IOException("log offline")
        f.start(compose)
        compose.onNodeWithText("Could not load notification events.").assertIsDisplayed()
        compose.onNodeWithText("No notification events.").assertDoesNotExist()
        compose.runOnIdle {
            f.failure.value = null
            f.host.notifications.records.value = emptyList()
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No notification events.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, f.listRequests) }
    }

    /** Long owner text remains reachable at large system text sizes without losing its real ID. */
    @Test fun longAnnouncementRemainsReadableAtLargeText() {
        val longTitle = "Trail notice\nNorth meadow\nThe route beside the river"
        f.host.notifications.records.value =
            listOf(f.event.copy(announcement = Announcement(longTitle, "Bring drinking water.")))
        f.start(compose, fontScale = 2f)
        compose.onNodeWithText(longTitle).performScrollTo().performClick()
        compose.onNodeWithText(f.event.id).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recorded recipients").performScrollTo().assertIsDisplayed()
    }
}

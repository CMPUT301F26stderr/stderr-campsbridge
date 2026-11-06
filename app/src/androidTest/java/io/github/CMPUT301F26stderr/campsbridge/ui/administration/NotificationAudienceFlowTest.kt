package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Fixed real-navigation audience facts, independent recovery and child cleanup contracts. */
class NotificationAudienceFlowTest {
    @get:Rule val compose = createComposeRule()
    private val f = NotificationLogUiFixture()

    /** Releases all real feature and account models after each scenario. */
    @After fun close() {
        compose.runOnIdle { f.host.close() }
    }

    /** Recorded IDs and delivery/read distinctions survive restoration without receipt writes. */
    @Test fun recordedRecipientsRestoreAndReturnToTheSameEvent() {
        val restoration = StateRestorationTester(compose)
        f.host.notifications.getAudienceHandler = {
            NotificationAudience.Recorded(
                listOf(
                    NotificationAudienceMember("pending-account", NotificationAudienceDelivery.PENDING),
                    NotificationAudienceMember("unread-account", NotificationAudienceDelivery.DELIVERED),
                    NotificationAudienceMember(
                        "read-account",
                        NotificationAudienceDelivery.DELIVERED,
                        f.event.createdAt,
                    ),
                ),
            )
        }
        f.start(compose, restoration)
        openAudience()
        compose.onNodeWithText("pending-account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pending Inbox delivery").assertIsDisplayed()
        compose.onNodeWithText("Unread").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read at").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("read-account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(f.event.id).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, f.host.notificationLogCreations)
            assertEquals(listOf(f.event.id), f.host.notifications.getRequests)
            assertTrue(
                f.host.notifications.readRequests
                    .isEmpty(),
            )
        }
    }

    /** Failure, no recorded information, confirmed empty membership and unavailable data differ. */
    @Test fun audienceRetryDoesNotReplaceTheEventOrList() {
        f.host.notifications.getAudienceHandler = { throw IOException("audience offline") }
        f.start(compose)
        openAudience()
        compose.onNodeWithText("Could not load recipients.").assertIsDisplayed()
        compose.runOnIdle { f.host.notifications.getAudienceHandler = { NotificationAudience.NoRecordedInformation } }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No recipient information was recorded.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { f.host.notifications.getAudienceHandler = { NotificationAudience.Recorded(emptyList()) } }
        compose.onNodeWithText("Recorded recipients").performScrollTo().performClick()
        compose.onNodeWithText("No recorded recipients.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            f.host.notifications.getAudienceHandler =
                { NotificationAudience.Unavailable(NotificationAudienceUnavailable.EVENT_MISSING) }
        }
        compose.onNodeWithText("Recorded recipients").performScrollTo().performClick()
        compose.onNodeWithText("The original event is unavailable.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, f.listRequests)
            assertEquals(listOf(f.event.id), f.host.notifications.getRequests)
            assertTrue(
                f.host.notifications.readRequests
                    .isEmpty(),
            )
        }
    }

    /** Leaving a held audience read returns to the selected event and rejects its late result. */
    @Test fun leavingAudienceRetiresItsHeldRead() {
        val held = CompletableDeferred<Unit>()
        f.host.notifications.getAudienceHandler =
            {
                withContext(NonCancellable) { held.await() }
                NotificationAudience.NoRecordedInformation
            }
        try {
            f.start(compose)
            openAudience()
            compose.onNodeWithText("Loading recipients…").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText(f.event.id).assertIsDisplayed()
            compose.runOnIdle {
                f.host.notifications.getAudienceHandler = { NotificationAudience.Recorded(emptyList()) }
                held.complete(Unit)
            }
            compose.onNodeWithText("Recorded recipients").performScrollTo().performClick()
            compose.onNodeWithText("No recorded recipients.").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(f.event.id), f.host.notifications.getRequests)
                assertEquals(2, f.host.notifications.audienceRequests.size)
            }
        } finally {
            held.complete(Unit)
        }
    }

    /** Opens both actual ID-only child routes through their visible row/action. */
    private fun openAudience() {
        compose.onNodeWithTag("notification-log-event:${f.event.id}").performClick()
        compose.onNodeWithText("Recorded recipients").performScrollTo().performClick()
    }
}

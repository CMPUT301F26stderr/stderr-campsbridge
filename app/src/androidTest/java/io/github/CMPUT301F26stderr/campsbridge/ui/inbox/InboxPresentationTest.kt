/*
 * History retains event facts and exposes recovery at the row that owns the failed work.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** History retains event facts and exposes recovery at the row that owns the failed work. */
class InboxPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val createdAt = Instant.parse("2027-05-01T12:00:00Z")
    private val retriedContent = mutableListOf<String>()
    private val retriedRead = mutableListOf<String>()

    /** An unavailable subject cannot hide its retained announcement, time, or unread receipt. */
    @Test fun announcementFactsRemainVisibleWhenItsSubjectFails() {
        show(listOf(announcement("notice")), unread = 1)
        compose.onNodeWithText("1 unread").assertIsDisplayed()
        compose.onNodeWithText("Notice notice").assertIsDisplayed()
        compose.onNodeWithText("The check-in desk closes at six.").assertIsDisplayed()
        compose.onNodeWithText("Unread").assertIsDisplayed()
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        compose.onNodeWithText(formatDateTime(createdAt, locale)).assertIsDisplayed()
        compose.onNodeWithTag("inbox-event-notice").assertHasNoClickAction()
    }

    /** A held event keeps the original read state visible without offering a duplicate retry. */
    @Test fun unknownLoadingReceiptRetainsItsReadStatus() {
        compose.setContent {
            CampsbridgeTheme {
                InboxScreen(
                    InboxUiState(
                        source = InboxSourceState.READY,
                        unavailable =
                            listOf(
                                UnavailableInboxReceipt(InboxReceipt("held", createdAt), InboxEventStatus.LOADING),
                            ),
                        unreadCount = 0,
                        orderingPending = false,
                        unreadCountPending = false,
                    ),
                    onRetrySource = {},
                    onRetryItem = retriedContent::add,
                    onOpen = {},
                    onRetryRead = retriedRead::add,
                )
            }
        }
        compose.onNodeWithText("Loading notification").assertIsDisplayed()
        compose.onNodeWithText("Read").assertIsDisplayed()
        compose.onNodeWithText("Retry notification").assertDoesNotExist()
        compose.onNodeWithTag("inbox-receipt-held").assertHasNoClickAction()
    }

    /** Each recovery control forwards its own event identity even when multiple rows failed. */
    @Test fun contentAndReadRetriesBelongToTheirOwnRows() {
        show(listOf(announcement("A"), announcement("B")), unread = 2, failures = setOf("A", "B"))
        val row = hasAnyAncestor(hasTestTag("inbox-event-B"))
        compose.onNode(hasText("Retry read acknowledgement") and row).performClick()
        compose.onNode(hasText("Retry notification") and row).performClick()
        compose.runOnIdle {
            assertEquals(listOf("B"), retriedRead)
            assertEquals(listOf("B"), retriedContent)
        }
    }

    /** Mounts the passive production screen with explicit receipt facts and inert navigation.
     * @param items Actual event history.
     * @param unread Count over original receipts.
     * @param failures Receipt IDs with independently failed acknowledgements.
     */
    private fun show(
        items: List<InboxItem>,
        unread: Int,
        failures: Set<String> = emptySet(),
    ) {
        compose.setContent {
            CampsbridgeTheme {
                InboxScreen(
                    InboxUiState(
                        source = InboxSourceState.READY,
                        items = items,
                        unreadCount = unread,
                        orderingPending = false,
                        unreadCountPending = false,
                        readFailures = failures,
                    ),
                    onRetrySource = {},
                    onRetryItem = retriedContent::add,
                    onOpen = {},
                    onRetryRead = retriedRead::add,
                )
            }
        }
    }

    /** Builds a real announcement whose original facts survive failed linked-record loading.
     * @param id Immutable event identity.
     */
    private fun announcement(id: String): InboxItem =
        InboxItem(
            Notification(
                id,
                "owner",
                NotificationType.OWNER_ANNOUNCEMENT,
                NotificationSubject(SubjectType.LOTTERY, "draw"),
                Announcement("Notice $id", "The check-in desk closes at six."),
                createdAt,
            ),
            null,
            NotificationSubjectContent.Failure,
        )
}

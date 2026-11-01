package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.sortInboxItems
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class InboxOrderingTest {
    @Test
    fun returnsEmptyListForEmptyInput() {
        assertEquals(emptyList<InboxItem>(), sortInboxItems(emptyList()))
    }

    @Test
    fun sortsNewestEventTimeThenIdAscending() {
        val t1 = Instant.ofEpochSecond(100)
        val t2 = Instant.ofEpochSecond(200)

        val item1 = inboxItem("b-id", t1)
        val item2 = inboxItem("a-id", t2)
        val item3 = inboxItem("c-id", t2)

        val sorted = sortInboxItems(listOf(item1, item2, item3))
        assertEquals(listOf(item2, item3, item1), sorted)
    }

    private fun inboxItem(id: String, createdAt: Instant): InboxItem {
        val notification = Notification(
            id = id,
            senderId = null,
            type = NotificationType.LOTTERY_WON,
            subject = NotificationSubject(SubjectType.LOTTERY, "lottery-1"),
            announcement = null,
            createdAt = createdAt,
        )
        return InboxItem(
            notification = notification,
            readAt = null,
            subject = NotificationSubjectContent.Unsupported,
        )
    }
}

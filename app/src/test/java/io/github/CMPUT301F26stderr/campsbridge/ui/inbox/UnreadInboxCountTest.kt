package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic.unreadInboxCount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Unread receipt counting. */
class UnreadInboxCountTest {
    /** Only receipts without a read time count. */
    @Test
    fun countsReceiptsWithNullReadAt() {
        val receipts =
            listOf(
                InboxReceipt("n1", null),
                InboxReceipt("n2", Instant.EPOCH),
                InboxReceipt("n3", null),
            )
        assertEquals(2, unreadInboxCount(receipts))
    }

    /** Empty and fully read inboxes have nothing unread. */
    @Test
    fun returnsZeroForEmptyOrAllRead() {
        val receipts =
            listOf(
                InboxReceipt("n1", Instant.EPOCH),
                InboxReceipt("n2", Instant.EPOCH),
            )
        assertEquals(0, unreadInboxCount(emptyList()))
        assertEquals(0, unreadInboxCount(receipts))
    }
}

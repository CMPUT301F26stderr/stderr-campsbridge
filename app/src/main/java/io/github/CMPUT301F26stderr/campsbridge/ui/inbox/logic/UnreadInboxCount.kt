package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt

/**
 * Calculates unread inbox count from original receipts with null readAt, including unloaded content.
 */
fun unreadInboxCount(receipts: List<InboxReceipt>): Int {
    return receipts.count { it.readAt == null }
}

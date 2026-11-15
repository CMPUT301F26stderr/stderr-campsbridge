/*
 * Calculates unread inbox count from original receipts with null readAt, including unloaded content.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt

/** Counts unread receipts, including events whose content is not loaded.
 * @param receipts Saved account delivery receipts.
 */
fun unreadInboxCount(receipts: List<InboxReceipt>): Int =
    receipts.count { it.readAt == null }

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox.logic

import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxItem

/**
 * Orders inbox items by newest event time (createdAt descending), then by exact event ID (ascending).
 * Preserves input rows and does not mutate the original list.
 */
fun sortInboxItems(items: List<InboxItem>): List<InboxItem> {
    if (items.isEmpty()) return emptyList()
    return items.sortedWith(
        compareByDescending<InboxItem> { it.notification.createdAt }
            .thenBy { it.notification.id }
    )
}

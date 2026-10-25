package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject

/** Receipt-source state is independent of retained immutable rows and individual content reads. */
enum class InboxSourceState { LOADING, READY, FAILURE }

/** Account Inbox facts; missing helpers and unavailable event content remain explicit.
 * @param source Current receipt-source outcome; READY empty is a confirmed empty Inbox.
 * @param items Loaded real events; supplied ordering is applied only when its helper exists.
 * @param unavailable Receipts without known event time, ordered by exact ID after known events.
 * @param unreadCount Supplied count over every original receipt; null while its helper is pending.
 * @param orderingPending The reserved chronological ordering function is absent.
 * @param unreadCountPending The reserved unread-count function is absent.
 * @param readFailures Explicit acknowledgement failures, separate from context and navigation.
 */
data class InboxUiState(
    val source: InboxSourceState = InboxSourceState.LOADING,
    val items: List<InboxItem> = emptyList(),
    val unavailable: List<UnavailableInboxReceipt> = emptyList(),
    val unreadCount: Int? = null,
    val orderingPending: Boolean = true,
    val unreadCountPending: Boolean = true,
    val readFailures: Set<String> = emptySet(),
)

/** One explicit open request; consuming it has no effect on the independent receipt write.
 * @param notificationId Actual selected event/receipt identity.
 * @param subject Loaded supported destination reference.
 */
data class InboxOpenRequest(
    val notificationId: String,
    val subject: NotificationSubject,
)

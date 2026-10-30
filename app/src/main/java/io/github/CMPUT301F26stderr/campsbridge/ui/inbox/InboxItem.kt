package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import java.time.Instant

/** Loaded immutable event with this account's observed read state and independent context.
 * @param notification Actual event, retaining identity and creation time for ordering.
 * @param readAt Actual receipt's first-read timestamp; null remains unread.
 * @param subject Independent factual context, including missing/failed/unsupported subjects.
 * @param isLoading An explicit context retry is pending while this known event stays readable.
 */
data class InboxItem(
    val notification: Notification,
    val readAt: Instant?,
    val subject: NotificationSubjectContent,
    val isLoading: Boolean = false,
)

/**
 * Unknown event content has no fabricated identity/time model to insert into chronological rows.
 */
enum class InboxEventStatus { LOADING, UNAVAILABLE, FAILURE }

/** Receipt retained separately until its actual immutable event can be loaded.
 * @param receipt Actual account receipt, including read state for the reserved unread counter.
 * @param status Independent event-read outcome.
 */
data class UnavailableInboxReceipt(
    val receipt: InboxReceipt,
    val status: InboxEventStatus,
)

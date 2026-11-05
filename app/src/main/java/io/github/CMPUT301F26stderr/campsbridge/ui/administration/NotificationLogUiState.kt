package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent

/** The all-event observation never treats a failed read as an empty log. */
sealed interface NotificationLogEvents {
    /** No confirmed result has arrived. */
    data object Loading : NotificationLogEvents

    /** The all-event observation failed and can be retried. */
    data object Failure : NotificationLogEvents

    /** Locally filtered immutable events.
     * @param events Events ordered newest first, then by exact ID.
     */
    data class Content(
        val events: List<Notification>,
    ) : NotificationLogEvents
}

/** List inputs remain usable independently of the current source outcome.
 * @param query Raw retained search text.
 * @param type Exact category, or null for every category.
 * @param events Current all-event result after local filtering.
 */
data class NotificationLogUiState(
    val query: String = "",
    val type: NotificationType? = null,
    val events: NotificationLogEvents = NotificationLogEvents.Loading,
)

/** Selected event facts stay independent of list filtering and optional context reads.
 * @param id Actual route identity, or null outside a selected event.
 * @param event Confirmed immutable facts from the list or common reader.
 * @param content Common event/context outcome, or null before its read completes.
 * @param isLoading Whether the selected read is pending.
 */
data class NotificationLogDetailUiState(
    val id: String? = null,
    val event: Notification? = null,
    val content: NotificationContent? = null,
    val isLoading: Boolean = false,
)

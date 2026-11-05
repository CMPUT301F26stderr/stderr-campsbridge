package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
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

/** Recipient reads belong only to the currently displayed audience page. */
sealed interface NotificationLogAudienceState {
    /** No audience page is requesting data. */
    data object Idle : NotificationLogAudienceState

    /** One exact event's audience read is pending.
     * @param id Original event identity.
     */
    data class Loading(
        val id: String,
    ) : NotificationLogAudienceState

    /** A required audience read failed, independently of the event and list.
     * @param id Original event identity.
     */
    data class Failure(
        val id: String,
    ) : NotificationLogAudienceState

    /** Recorded facts, confirmed empty membership, absence of information, or unavailability.
     * @param id Original event identity.
     * @param audience Repository-supplied recipient facts, without OS delivery assumptions.
     */
    data class Content(
        val id: String,
        val audience: NotificationAudience,
    ) : NotificationLogAudienceState
}

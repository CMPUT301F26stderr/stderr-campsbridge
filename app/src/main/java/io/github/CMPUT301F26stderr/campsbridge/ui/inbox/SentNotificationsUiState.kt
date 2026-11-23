package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Real saved target of a sent event, with no recipient receipt state. */
sealed interface SentTarget {
    /** A saved lottery the sender may inspect.
     * @param id Original lottery identity.
     */
    data class Lottery(
        val id: String,
    ) : SentTarget

    /** The actual participant conversation resolved from a saved message.
     * @param id Saved reservation identity.
     * @param context Actor's actual relationship, independent of selected workspace.
     */
    data class Conversation(
        val id: String,
        val context: ReservationContext,
    ) : SentTarget
}

/** One observed immutable sender event and separately loaded linked context.
 * @param event Original event facts, retained through context failure/absence.
 * @param subject Linked read outcome, null until resolved.
 * @param loading An explicit event/context read is in progress.
 * @param target Optional saved destination authorized by the actual participant relationship.
 */
data class SentNotificationItem(
    val event: Notification,
    val subject: NotificationSubjectContent? = null,
    val loading: Boolean = true,
    val target: SentTarget? = null,
)

/** Sender history and local search, without unread badges or recipient receipts.
 * @param source Primary event observation outcome.
 * @param items Retained sender events in newest-first order, filtered by local query.
 * @param query Raw retained local search text.
 */
data class SentNotificationsUiState(
    val source: InboxSourceState = InboxSourceState.LOADING,
    val items: List<SentNotificationItem> = emptyList(),
    val query: String = "",
)

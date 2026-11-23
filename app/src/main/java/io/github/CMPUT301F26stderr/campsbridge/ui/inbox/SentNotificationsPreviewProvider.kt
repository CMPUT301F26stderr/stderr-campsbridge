package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Supplies saved event facts and independent context/source outcomes without recipient state. */
class SentNotificationsPreviewProvider : PreviewParameterProvider<SentNotificationsUiState> {
    override val values: Sequence<SentNotificationsUiState> =
        run {
            val stay = previewReservation()
            val message =
                Message(
                    "message",
                    stay.id,
                    "owner",
                    "The riverside trail is open again.\nPlease use the east gate when you arrive.",
                    stay.createdAt,
                )
            val messageEvent =
                Notification(
                    "message-event",
                    "owner",
                    NotificationType.NEW_MESSAGE,
                    NotificationSubject(SubjectType.MESSAGE, message.id),
                    null,
                    stay.createdAt,
                )
            val announcement =
                Notification(
                    "announcement",
                    "owner",
                    NotificationType.OWNER_ANNOUNCEMENT,
                    NotificationSubject(SubjectType.LOTTERY, "draw"),
                    Announcement(
                        "Trail access and quiet hours for the long weekend",
                        "Please keep food secured and respect quiet hours after 10 pm.\n" +
                            "Thanks for leaving the clearing clean.",
                    ),
                    stay.createdAt.minusSeconds(60),
                )
            val messageItem =
                SentNotificationItem(
                    messageEvent,
                    NotificationSubjectContent.MessageContent(
                        message,
                        stay.copy(
                            bookedCampsite =
                                stay.bookedCampsite.copy(
                                    name = "River Camp beside the northern meadow and lakeshore",
                                ),
                        ),
                    ),
                    false,
                    SentTarget.Conversation(stay.id, ReservationContext.OWNER),
                )
            val notice = SentNotificationItem(announcement, NotificationSubjectContent.Unavailable, false)
            val content = SentNotificationsUiState(InboxSourceState.READY, listOf(messageItem, notice))
            sequenceOf(
                content,
                content.copy(items = emptyList()),
                SentNotificationsUiState(),
                content.copy(source = InboxSourceState.FAILURE),
                content.copy(
                    items = listOf(messageItem.copy(subject = NotificationSubjectContent.Failure, target = null)),
                ),
                content.copy(items = listOf(messageItem.copy(subject = null, loading = true, target = null))),
                content.copy(items = emptyList(), query = "No matching trail"),
            )
        }
}

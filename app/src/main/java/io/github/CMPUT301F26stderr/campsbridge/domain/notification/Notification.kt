package io.github.CMPUT301F26stderr.campsbridge.domain.notification

import java.time.Instant

/**
 * Event categories rendered using application templates or owner announcement content.
 */
enum class NotificationType {
    LOTTERY_WON,
    LOTTERY_LOST,
    RESERVATION_CONFIRMED,
    RESERVATION_CANCELLED,
    OWNER_ANNOUNCEMENT,
    NEW_MESSAGE,
}

/**
 * Entity categories referenced by notifications for navigation and history.
 */
enum class SubjectType {
    LOTTERY,
    RESERVATION,
    MESSAGE,
}

/**
 * Typed reference to a lottery, reservation, or message without copying its content.
 *
 * @param type Category of the referenced application entity.
 * @param id ID of the entity identified by the subject type.
 */
data class NotificationSubject(
    val type: SubjectType,
    val id: String,
)

/**
 * Nonblank title and message supplied by a lottery owner.
 *
 * @param title Display title for the lottery or announcement.
 * @param body Nonblank message or reply text.
 */
data class Announcement(
    val title: String,
    val body: String,
) {
    init {
        require(title.isNotBlank())
        require(body.isNotBlank())
    }
}

/**
 * Shared immutable event; per-recipient read state lives in inbox receipts.
 *
 * @param id Notification event document ID.
 * @param senderId Sending account ID, or null for system events.
 * @param type Event category controlling content and subject interpretation.
 * @param subject Typed reference to the notification’s lottery, reservation, or message.
 * @param announcement Required content for an owner announcement; null for system events.
 * @param createdAt Original record creation time.
 */
data class Notification(
    val id: String,
    val senderId: String?,
    val type: NotificationType,
    val subject: NotificationSubject,
    val announcement: Announcement?,
    val createdAt: Instant,
) {
    init {
        require((type == NotificationType.OWNER_ANNOUNCEMENT) == (announcement != null))
        val expected =
            when (type) {
                NotificationType.LOTTERY_WON,
                NotificationType.LOTTERY_LOST,
                NotificationType.OWNER_ANNOUNCEMENT,
                -> SubjectType.LOTTERY

                NotificationType.RESERVATION_CONFIRMED,
                NotificationType.RESERVATION_CANCELLED,
                -> SubjectType.RESERVATION

                NotificationType.NEW_MESSAGE -> SubjectType.MESSAGE
            }
        require(subject.type == expected)
    }
}

/**
 * Membership of an event in an account inbox with optional read acknowledgement.
 *
 * @param notificationId Shared notification event ID.
 * @param readAt First-read acknowledgement time, or null if unread.
 */
data class InboxReceipt(
    val notificationId: String,
    val readAt: Instant?,
)

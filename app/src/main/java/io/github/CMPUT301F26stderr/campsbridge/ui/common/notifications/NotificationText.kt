package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import android.content.Context
import androidx.annotation.StringRes
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationText

/** Supplies the shared Inbox/OS event title without doing reads or replacing owner-supplied text.
 * @param type Original immutable event category.
 */
@StringRes
fun notificationTitleResource(type: NotificationType): Int =
    when (type) {
        NotificationType.LOTTERY_WON -> R.string.inbox_winner
        NotificationType.LOTTERY_LOST -> R.string.inbox_loser
        NotificationType.RESERVATION_CONFIRMED -> R.string.inbox_reservation_confirmed
        NotificationType.RESERVATION_CANCELLED -> R.string.inbox_reservation_cancelled
        NotificationType.OWNER_ANNOUNCEMENT -> R.string.inbox_announcement
        NotificationType.NEW_MESSAGE -> R.string.inbox_message
    }

/** Resolves factual localized alert copy before the platform publisher receives it.
 * @param context Resources used for current language, never a repository owner.
 * @param content Already loaded original event and independent context outcome.
 */
fun notificationText(
    context: Context,
    content: NotificationContent.Loaded,
): NotificationText {
    val event = content.notification
    event.announcement?.let { return NotificationText(it.title, it.body) }
    val body =
        when (val subject = content.subject) {
            is NotificationSubjectContent.LotteryContent -> {
                subject.lottery.title
            }

            is NotificationSubjectContent.ReservationContent -> {
                subject.reservation.bookedCampsite.name
            }

            NotificationSubjectContent.Unavailable -> {
                context.getString(
                    R.string.inbox_context_unavailable,
                )
            }

            NotificationSubjectContent.Failure -> {
                context.getString(R.string.inbox_context_failed)
            }

            NotificationSubjectContent.Unsupported -> {
                context.getString(
                    R.string.inbox_context_unsupported,
                )
            }
        }
    return NotificationText(context.getString(notificationTitleResource(event.type)), body)
}

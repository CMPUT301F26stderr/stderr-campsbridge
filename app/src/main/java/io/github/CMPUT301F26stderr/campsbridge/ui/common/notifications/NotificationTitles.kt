package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import androidx.annotation.StringRes
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType

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

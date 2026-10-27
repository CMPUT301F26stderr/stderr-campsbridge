package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the existing localized title mapping for each immutable event category. */
class NotificationTitlesTest {
    /** The existing six-case Inbox title mapping is unchanged by extraction. */
    @Test fun originalTitlesStayMappedToTheirExistingResources() {
        val expected =
            mapOf(
                NotificationType.LOTTERY_WON to R.string.inbox_winner,
                NotificationType.LOTTERY_LOST to R.string.inbox_loser,
                NotificationType.RESERVATION_CONFIRMED to R.string.inbox_reservation_confirmed,
                NotificationType.RESERVATION_CANCELLED to R.string.inbox_reservation_cancelled,
                NotificationType.OWNER_ANNOUNCEMENT to R.string.inbox_announcement,
                NotificationType.NEW_MESSAGE to R.string.inbox_message,
            )
        expected.forEach { (type, resource) ->
            assertEquals(resource, notificationTitleResource(type))
        }
    }
}

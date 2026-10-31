package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import android.app.Application
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationText
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Four fixed copy contracts shared by Inbox rendering and resolved OS publication. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationTextTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()
    private val won =
        Notification(
            "won",
            null,
            NotificationType.LOTTERY_WON,
            NotificationSubject(SubjectType.LOTTERY, "draw"),
            null,
            CampingFixtures.now,
        )

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

    /** Uses the actual lottery title or historical booked campsite without another read. */
    @Test fun loadedContextUsesItsOriginalSavedFacts() {
        val lottery = CampingFixtures.lottery().copy(title = "Saved draw title")
        assertEquals(
            NotificationText(context.getString(R.string.inbox_winner), lottery.title),
            notificationText(
                context,
                NotificationContent.Loaded(won, NotificationSubjectContent.LotteryContent(lottery)),
            ),
        )
        val reservation = CampingFixtures.reservation()
        val event =
            won.copy(
                type = NotificationType.RESERVATION_CONFIRMED,
                subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
            )
        assertEquals(
            NotificationText(
                context.getString(R.string.inbox_reservation_confirmed),
                reservation.bookedCampsite.name,
            ),
            notificationText(
                context,
                NotificationContent.Loaded(
                    event,
                    NotificationSubjectContent.ReservationContent(reservation),
                ),
            ),
        )
    }

    /** Owner-supplied announcement spelling, whitespace and line breaks are retained exactly. */
    @Test fun announcementCopyIsNotRewritten() {
        val announcement = Announcement("  Camp update α ", "Bring water.\nMeet at 10:00.  ")
        val event =
            won.copy(
                type = NotificationType.OWNER_ANNOUNCEMENT,
                announcement = announcement,
            )
        assertEquals(
            NotificationText(announcement.title, announcement.body),
            notificationText(
                context,
                NotificationContent.Loaded(event, NotificationSubjectContent.Unavailable),
            ),
        )
    }

    /** Missing, failed and unsupported context keep distinct honest copy without invented names. */
    @Test fun contextOutcomesKeepTheirExistingResourceCopy() {
        val outcomes =
            mapOf(
                NotificationSubjectContent.Unavailable to R.string.inbox_context_unavailable,
                NotificationSubjectContent.Failure to R.string.inbox_context_failed,
                NotificationSubjectContent.Unsupported to R.string.inbox_context_unsupported,
            )
        outcomes.forEach { (subject, resource) ->
            assertEquals(
                NotificationText(
                    context.getString(R.string.inbox_winner),
                    context.getString(resource),
                ),
                notificationText(context, NotificationContent.Loaded(won, subject)),
            )
        }
    }
}

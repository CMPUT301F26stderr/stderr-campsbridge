package io.github.CMPUT301F26stderr.campsbridge.domain.notification

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertThrows
import org.junit.Test

/** Owns announcement content and event subject relationships. */
class NotificationTest {
    /** Both announcement fields require visible content. */
    @Test fun announcementsRequireTitleAndBody() {
        for (body in listOf("", " \n\t")) {
            assertThrows(IllegalArgumentException::class.java) { Announcement(body, "Content") }
            assertThrows(IllegalArgumentException::class.java) { Announcement("Title", body) }
        }
    }

    /** Each event category points at its own entity type and only announcements carry text. */
    @Test fun notificationCategoriesDetermineSubjectAndContent() {
        val subjects =
            mapOf(
                NotificationType.LOTTERY_WON to SubjectType.LOTTERY,
                NotificationType.LOTTERY_LOST to SubjectType.LOTTERY,
                NotificationType.OWNER_ANNOUNCEMENT to SubjectType.LOTTERY,
                NotificationType.RESERVATION_CONFIRMED to SubjectType.RESERVATION,
                NotificationType.RESERVATION_CANCELLED to SubjectType.RESERVATION,
                NotificationType.NEW_MESSAGE to SubjectType.MESSAGE,
            )
        for ((type, expected) in subjects) {
            val content =
                if (type ==
                    NotificationType.OWNER_ANNOUNCEMENT
                ) {
                    Announcement("Title", "Content")
                } else {
                    null
                }
            val event =
                Notification(
                    "event",
                    null,
                    type,
                    NotificationSubject(expected, "subject"),
                    content,
                    CampingFixtures.now,
                )
            for (wrong in SubjectType.entries.filter { it != expected }) {
                assertThrows(IllegalArgumentException::class.java) {
                    event.copy(subject = NotificationSubject(wrong, "subject"))
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                event.copy(
                    announcement =
                        if (content ==
                            null
                        ) {
                            Announcement("Title", "Content")
                        } else {
                            null
                        },
                )
            }
        }
    }
}

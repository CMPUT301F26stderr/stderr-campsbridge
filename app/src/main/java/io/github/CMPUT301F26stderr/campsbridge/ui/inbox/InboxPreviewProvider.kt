package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.previewReservation

/** Supplies readable win/loss/saved history and independent source/context failure variants. */
class InboxPreviewProvider : PreviewParameterProvider<InboxUiState> {
    override val values: Sequence<InboxUiState> =
        run {
            val reservation = previewReservation()
            val at = reservation.createdAt
            val lottery =
                Lottery(
                    "may-weekends",
                    "owner",
                    "May camping",
                    "Quiet riverside stays",
                    LotteryConfiguration(
                        at.minusSeconds(7200),
                        at.minusSeconds(3600),
                        at.minusSeconds(1800),
                        at.plusSeconds(3600),
                        1,
                        null,
                    ),
                    0,
                    at.minusSeconds(10800),
                    publishedAt = at.minusSeconds(9000),
                    drawnAt = at,
                )
            val win =
                Notification(
                    "winner",
                    null,
                    NotificationType.LOTTERY_WON,
                    NotificationSubject(SubjectType.LOTTERY, lottery.id),
                    null,
                    reservation.createdAt,
                )
            val loss =
                win.copy(
                    id = "loser",
                    type = NotificationType.LOTTERY_LOST,
                    subject = NotificationSubject(SubjectType.LOTTERY, "other-draw"),
                )
            val booked =
                win.copy(
                    id = "booked",
                    type = NotificationType.RESERVATION_CONFIRMED,
                    subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
                )
            val content =
                InboxUiState(
                    InboxSourceState.READY,
                    listOf(
                        InboxItem(win, null, NotificationSubjectContent.LotteryContent(lottery)),
                        InboxItem(
                            loss,
                            reservation.createdAt,
                            NotificationSubjectContent.LotteryContent(
                                lottery.copy(id = "other-draw", title = "Summer camping"),
                            ),
                        ),
                        InboxItem(
                            booked,
                            null,
                            NotificationSubjectContent.ReservationContent(reservation),
                        ),
                    ),
                    orderingPending = false,
                    unreadCountPending = false,
                    unreadCount = 2,
                )
            val single = content.copy(items = listOf(content.items.first()), unreadCount = 1)
            sequenceOf(
                content,
                content.copy(readFailures = setOf(win.id)),
                single.copy(
                    items =
                        listOf(
                            single.items.first().copy(subject = NotificationSubjectContent.Failure),
                        ),
                ),
                content.copy(
                    unreadCount = 3,
                    unavailable =
                        listOf(
                            UnavailableInboxReceipt(
                                InboxReceipt("unresolved", null),
                                InboxEventStatus.FAILURE,
                            ),
                        ),
                ),
                InboxUiState(source = InboxSourceState.READY),
                InboxUiState(),
                InboxUiState(source = InboxSourceState.FAILURE),
                single.copy(
                    items =
                        listOf(
                            InboxItem(
                                win.copy(
                                    type = NotificationType.OWNER_ANNOUNCEMENT,
                                    senderId = lottery.ownerId,
                                    announcement =
                                        Announcement(
                                            "Trail access and quiet hours for the long weekend",
                                            "The riverside trail is open again. " +
                                                "Arrive before sunset and keep food secured.\n" +
                                                "Please leave the clearing clean and respect quiet hours after 10 pm.",
                                        ),
                                ),
                                null,
                                NotificationSubjectContent.LotteryContent(
                                    lottery.copy(
                                        title = "Long weekend by the river and the northern meadow",
                                    ),
                                ),
                            ),
                        ),
                ),
                single.copy(
                    items =
                        listOf(
                            InboxItem(
                                win.copy(
                                    type = NotificationType.NEW_MESSAGE,
                                    subject = NotificationSubject(SubjectType.MESSAGE, "thread"),
                                ),
                                null,
                                NotificationSubjectContent.Unsupported,
                            ),
                        ),
                ),
                single.copy(
                    items =
                        listOf(
                            single.items.first().copy(
                                subject = NotificationSubjectContent.Unavailable,
                            ),
                        ),
                ),
                single.copy(items = listOf(single.items.first().copy(isLoading = true))),
                single.copy(
                    items = emptyList(),
                    unavailable =
                        listOf(
                            UnavailableInboxReceipt(
                                InboxReceipt("loading-event", null),
                                InboxEventStatus.LOADING,
                            ),
                        ),
                ),
                single.copy(
                    items = emptyList(),
                    unavailable =
                        listOf(
                            UnavailableInboxReceipt(
                                InboxReceipt("missing-event", null),
                                InboxEventStatus.UNAVAILABLE,
                            ),
                        ),
                ),
            )
        }
}

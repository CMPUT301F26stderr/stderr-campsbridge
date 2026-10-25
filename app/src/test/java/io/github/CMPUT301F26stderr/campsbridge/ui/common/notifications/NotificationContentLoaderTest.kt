package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Immutable event facts survive independent subject outcomes without receipt writes or
 * observers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationContentLoaderTest {
    private val lottery = CampingFixtures.lottery()
    private val reservation = CampingFixtures.reservation()
    private val notifications = FakeNotificationRepository()
    private val lotteries = FakeLotteryRepository(listOf(lottery))
    private val bookings = FakeBookingRepository(listOf(reservation))
    private val loader = NotificationContentLoader(notifications, lotteries, bookings)

    /** Both draw outcomes retain the original event and reference the actual saved lottery. */
    @Test fun winnerAndLoserUseTheSameActualLotteryContext() =
        runTest {
            for (type in listOf(NotificationType.LOTTERY_WON, NotificationType.LOTTERY_LOST)) {
                val event = event(type)
                notifications.records.value = listOf(event)
                assertEquals(
                    NotificationContent.Loaded(
                        event,
                        NotificationSubjectContent.LotteryContent(lottery),
                    ),
                    loader.load(event.id),
                )
            }
            assertEquals(List(2) { "get:${lottery.id}" }, lotteries.events)
            assertTrue(bookings.getRequests.isEmpty())
            assertTrue(lotteries.observedRecordIds.isEmpty())
            assertTrue(lotteries.entrantRequests.isEmpty())
        }

    /** Confirmation uses the historical saved snapshot, not editable campsite data. */
    @Test fun confirmationUsesTheCompleteSavedReservation() =
        runTest {
            val event = event(NotificationType.RESERVATION_CONFIRMED)
            notifications.records.value = listOf(event)
            assertEquals(
                NotificationContent.Loaded(
                    event,
                    NotificationSubjectContent.ReservationContent(reservation),
                ),
                loader.load(event.id),
            )
            assertEquals(listOf(reservation.id), bookings.getRequests)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
        }

    /** Cancellation/refund history stays a saved context and never becomes an unused win. */
    @Test fun cancelledReservationRetainsItsHistoricalPaymentAndTerms() =
        runTest {
            val cancelled =
                reservation.copy(
                    cancellation =
                        ReservationCancellation(
                            CampingFixtures.now,
                            "owner",
                            Role.OWNER,
                            null,
                        ),
                    payment = reservation.payment.copy(refundedAt = CampingFixtures.now),
                )
            bookings.records.value = listOf(cancelled)
            val event = event(NotificationType.RESERVATION_CANCELLED)
            notifications.records.value = listOf(event)
            assertEquals(
                NotificationContent.Loaded(
                    event,
                    NotificationSubjectContent.ReservationContent(cancelled),
                ),
                loader.load(event.id),
            )
        }

    /** Confirmed missing events do not produce invented time/copy or another repository request. */
    @Test fun missingEventDoesNotReadItsImaginedSubject() =
        runTest {
            assertEquals(NotificationContent.Unavailable, loader.load("missing-event"))
            assertEquals(listOf("missing-event"), notifications.getRequests)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.getRequests.isEmpty())
        }

    /** Event transport failure remains distinct from missing event and from subject failure. */
    @Test fun failedEventReadDoesNotReadASubject() =
        runTest {
            notifications.getHandler = { throw IOException("event offline") }
            assertEquals(NotificationContent.Failure, loader.load("result-event"))
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.getRequests.isEmpty())
        }

    /** A wrong event returned by a source cannot authorize unrelated subject navigation. */
    @Test fun inconsistentEventIdentityIsUnavailable() =
        runTest {
            notifications.getHandler =
                { event(NotificationType.LOTTERY_WON).copy(id = "other-event") }
            assertEquals(NotificationContent.Unavailable, loader.load("result-event"))
            assertTrue(lotteries.events.isEmpty())
        }

    /** Missing lottery retains the actual event identity/time for sortable readable history. */
    @Test fun missingLotteryRetainsItsOriginalEvent() =
        runTest {
            lotteries.records.value = emptyList()
            assertSubject(NotificationType.LOTTERY_WON, NotificationSubjectContent.Unavailable)
        }

    /** A different saved lottery cannot be substituted for the referenced subject. */
    @Test fun inconsistentLotteryIdentityRetainsUnavailableContext() =
        runTest {
            lotteries.getHandler = { lottery.copy(id = "other-draw") }
            assertSubject(NotificationType.LOTTERY_LOST, NotificationSubjectContent.Unavailable)
        }

    /** Subject transport failure does not discard the successfully loaded event. */
    @Test fun failedLotteryRetainsItsOriginalEvent() =
        runTest {
            lotteries.getHandler = { throw IOException("lottery offline") }
            assertSubject(NotificationType.LOTTERY_WON, NotificationSubjectContent.Failure)
        }

    /** Missing saved reservation remains history with unavailable destination context. */
    @Test fun missingReservationRetainsItsOriginalEvent() =
        runTest {
            bookings.records.value = emptyList()
            assertSubject(
                NotificationType.RESERVATION_CONFIRMED,
                NotificationSubjectContent.Unavailable,
            )
        }

    /** A different saved reservation never supplies factual copy or a target for this event. */
    @Test fun inconsistentReservationIdentityRetainsUnavailableContext() =
        runTest {
            bookings.getHandler = { reservation.copy(id = "other-reservation") }
            assertSubject(
                NotificationType.RESERVATION_CANCELLED,
                NotificationSubjectContent.Unavailable,
            )
        }

    /** Failed reservation context is independent of loaded event and not confirmed absence. */
    @Test fun failedReservationRetainsItsOriginalEvent() =
        runTest {
            bookings.getHandler = { throw IOException("reservation offline") }
            assertSubject(
                NotificationType.RESERVATION_CONFIRMED,
                NotificationSubjectContent.Failure,
            )
        }

    /**
     * Final message subjects keep history without calling an unrelated reader or inventing a
     * route.
     */
    @Test fun messageSubjectIsExplicitlyUnsupported() =
        runTest {
            assertSubject(NotificationType.NEW_MESSAGE, NotificationSubjectContent.Unsupported)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.getRequests.isEmpty())
        }

    /**
     * Existing announcement facts may share the actual lottery destination without a new sender.
     */
    @Test fun retainedAnnouncementUsesItsActualLotteryContext() =
        runTest {
            assertSubject(
                NotificationType.OWNER_ANNOUNCEMENT,
                NotificationSubjectContent.LotteryContent(lottery),
            )
        }

    /** Cancellation propagates from event or subject read instead of becoming failed history. */
    @Test fun cancellationNeverBecomesAReadFailure() =
        runTest {
            val event = event(NotificationType.LOTTERY_WON)
            notifications.getHandler = { throw CancellationException("event cancelled") }
            assertCancelled()
            notifications.getHandler = { event }
            lotteries.getHandler = { throw CancellationException("subject cancelled") }
            assertCancelled()
        }

    /** A cancelled noncooperative event read cannot continue with another subject request. */
    @Test fun retiredEventReadDoesNotStartTheSubjectQuery() =
        runTest {
            val gate = CompletableDeferred<Notification>()
            notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            val request = async { loader.load("result-event") }
            try {
                runCurrent()
                request.cancel()
                gate.complete(event(NotificationType.LOTTERY_WON))
                runCurrent()
                assertTrue(request.isCancelled)
                assertTrue(lotteries.events.isEmpty())
            } finally {
                gate.complete(event(NotificationType.LOTTERY_WON))
                request.cancel()
            }
        }

    /** Asserts loaded event identity/time survives a separate subject outcome.
     * @param type Event category under test.
     * @param expected Independent subject outcome.
     */
    private suspend fun assertSubject(
        type: NotificationType,
        expected: NotificationSubjectContent,
    ) {
        val event = event(type)
        notifications.records.value = listOf(event)
        assertEquals(NotificationContent.Loaded(event, expected), loader.load(event.id))
    }

    /** Verifies the caller receives cancellation rather than a typed missing/failed result. */
    private suspend fun assertCancelled() {
        try {
            loader.load("result-event")
            fail("Caller cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected caller-owned cancellation.
        }
    }

    /** Creates valid immutable event facts with actual domain references.
     * @param type Event category controlling its required subject.
     */
    private fun event(type: NotificationType): Notification {
        val subject =
            when (type) {
                NotificationType.NEW_MESSAGE -> {
                    NotificationSubject(SubjectType.MESSAGE, "message")
                }

                NotificationType.RESERVATION_CONFIRMED, NotificationType.RESERVATION_CANCELLED -> {
                    NotificationSubject(SubjectType.RESERVATION, reservation.id)
                }

                else -> {
                    NotificationSubject(SubjectType.LOTTERY, lottery.id)
                }
            }
        return Notification(
            "result-event",
            if (type ==
                NotificationType.OWNER_ANNOUNCEMENT
            ) {
                "owner"
            } else {
                null
            },
            type,
            subject,
            if (type ==
                NotificationType.OWNER_ANNOUNCEMENT
            ) {
                Announcement("Notice", "Saved text")
            } else {
                null
            },
            CampingFixtures.now,
        )
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads immutable event/context facts without owning receipts, UI copy or navigation.
 * @param notifications Immutable event source, never receipt writes.
 * @param lotteries Existing saved lottery reader.
 * @param bookings Existing complete saved reservation reader.
 */
class NotificationContentLoader(
    private val notifications: NotificationRepository,
    private val lotteries: LotteryRepository,
    private val bookings: BookingRepository,
) {
    /** Loads the event first and preserves it through independent subject absence/failure.
     * @param notificationId Actual event identity from a receipt or log entry.
     * @return Typed event/context outcome, without performing navigation or acknowledgement.
     * @throws kotlinx.coroutines.CancellationException When the caller's work is cancelled.
     */
    suspend fun load(notificationId: String): NotificationContent {
        val notification =
            try {
                notifications.get(notificationId).also { currentCoroutineContext().ensureActive() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return NotificationContent.Failure
            }
        if (notification == null || notification.id != notificationId) {
            return NotificationContent.Unavailable
        }
        return NotificationContent.Loaded(notification, loadSubject(notification.subject))
    }

    /** Reads only the referenced record and preserves a distinct missing/failed/unsupported result.
     * @param subject Actual event reference, never a client-derived persistence key.
     * @throws CancellationException When the caller retires this read.
     */
    private suspend fun loadSubject(subject: NotificationSubject): NotificationSubjectContent =
        try {
            when (subject.type) {
                SubjectType.LOTTERY -> {
                    val lottery = lotteries.get(subject.id)
                    currentCoroutineContext().ensureActive()
                    if (lottery?.id == subject.id) {
                        NotificationSubjectContent.LotteryContent(lottery)
                    } else {
                        NotificationSubjectContent.Unavailable
                    }
                }

                SubjectType.RESERVATION -> {
                    val reservation = bookings.get(subject.id)
                    currentCoroutineContext().ensureActive()
                    if (reservation?.id == subject.id) {
                        NotificationSubjectContent.ReservationContent(reservation)
                    } else {
                        NotificationSubjectContent.Unavailable
                    }
                }

                SubjectType.MESSAGE -> {
                    NotificationSubjectContent.Unsupported
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NotificationSubjectContent.Failure
        }
}

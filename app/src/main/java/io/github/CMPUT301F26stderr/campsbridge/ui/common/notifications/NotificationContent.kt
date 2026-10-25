package io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification

/** Event loading is independent of its subject; no invented identity or timestamp fills absence. */
sealed interface NotificationContent {
    /** The requested immutable event is missing or has an inconsistent returned identity. */
    data object Unavailable : NotificationContent

    /** The immutable event could not be read. */
    data object Failure : NotificationContent

    /** Real event facts are retained even when the subject is missing or fails.
     * @param notification Actual immutable event with original identity and creation time.
     * @param subject Separate context outcome for factual copy and supported navigation.
     */
    data class Loaded(
        val notification: Notification,
        val subject: NotificationSubjectContent,
    ) : NotificationContent
}

/** Context facts only; receipt acknowledgement and navigation remain in the actual caller. */
sealed interface NotificationSubjectContent {
    /** The event's referenced record is missing or inconsistent with its subject identity. */
    data object Unavailable : NotificationSubjectContent

    /** Required subject context could not be read; the original event remains readable. */
    data object Failure : NotificationSubjectContent

    /** This Halfway build supplies no destination for this subject category. */
    data object Unsupported : NotificationSubjectContent

    /** Actual saved lottery; copy may use its title without another campsite query.
     * @param lottery Matching referenced lottery, including retained historical records.
     */
    data class LotteryContent(
        val lottery: Lottery,
    ) : NotificationSubjectContent

    /** Actual saved aggregate; historical campsite/payment/terms do not depend on the live listing.
     * @param reservation Matching referenced reservation, including cancelled/refunded history.
     */
    data class ReservationContent(
        val reservation: Reservation,
    ) : NotificationSubjectContent
}

/*
 * Classifies saved stays from their dates and cancellation state without persisting presentation categories.
 */

package io.github.CMPUT301F26stderr.campsbridge.domain.booking

/** Derived stay presentation from the F17 date rule; never persisted. */
enum class ReservationCategory { UPCOMING, IN_PROGRESS, PAST, CANCELLED }

/** Classifies one saved stay using the caller's already-resolved campsite-local date.
 * @param reservation Original complete saved stay, including explicit cancellation.
 * @param today Caller-supplied civil date in that stay's booked zone.
 * @return CANCELLED first; otherwise PAST on/after checkout, UPCOMING before arrival, IN_PROGRESS.
 */
fun classifyReservation(
    reservation: Reservation,
    today: java.time.LocalDate,
): ReservationCategory =
    when {
        reservation.cancellation != null -> ReservationCategory.CANCELLED
        today >= reservation.checkOutDate -> ReservationCategory.PAST
        today < reservation.checkInDate -> ReservationCategory.UPCOMING
        else -> ReservationCategory.IN_PROGRESS
    }

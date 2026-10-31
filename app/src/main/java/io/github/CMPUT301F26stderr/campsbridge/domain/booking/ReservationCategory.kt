package io.github.CMPUT301F26stderr.campsbridge.domain.booking

/** Derived stay presentation, supplied by the reserved F17 rule and never persisted. */
enum class ReservationCategory { UPCOMING, IN_PROGRESS, PAST, CANCELLED }

/** Classifies one saved stay using the caller's already-resolved campsite-local date.
 * Pending F17 implementation; this placeholder must remain isolated until reviewed.
 * @param reservation Original complete saved stay, including explicit cancellation.
 * @param today Caller-supplied civil date in that stay's booked zone.
 * @return CANCELLED first; otherwise PAST on/after checkout, UPCOMING before arrival, IN_PROGRESS.
 */
fun classifyReservation(
    reservation: Reservation,
    today: java.time.LocalDate,
): ReservationCategory =
    ReservationCategory.UPCOMING

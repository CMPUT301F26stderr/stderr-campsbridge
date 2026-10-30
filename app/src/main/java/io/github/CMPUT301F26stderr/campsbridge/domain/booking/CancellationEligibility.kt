package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import java.time.LocalDate

/** Allows a new cancellation only for an uncancelled stay before its excluded checkout day.
 * Actor authorization and booked-zone conversion belong to the caller, not this date predicate.
 * @param reservation Complete saved stay, including its explicit cancellation fact.
 * @param today Caller-supplied current civil date in the booked campsite zone.
 * @return Whether a new public cancellation remains date-eligible.
 */
fun canCancelBeforeCheckout(
    reservation: Reservation,
    today: LocalDate,
): Boolean =
    reservation.cancellation == null && today < reservation.checkOutDate

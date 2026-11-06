/*
 * Orders supplied stays without changing list membership or the caller's original list.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation

/** Orders supplied stays without changing list membership or the caller's original list.
 * @param reservations Complete rows already selected by the caller.
 * @return Every row ordered by check-in ascending, then exact case-sensitive ID ascending.
 */
fun sortReservationsByCheckIn(reservations: List<Reservation>): List<Reservation> =
    reservations.sortedWith(compareBy<Reservation> { it.checkInDate }.thenBy { it.id })

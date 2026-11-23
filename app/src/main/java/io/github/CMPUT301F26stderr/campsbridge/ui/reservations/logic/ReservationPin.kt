/*
 * Turns saved reservation rows into one map pin per booked campsite.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListItem

/** One booked campsite on the reservation map and the stays booked there.
 * @param campsiteId Saved campsite identity.
 * @param name Campsite name from the first stay's booked snapshot.
 * @param locationLabel Human-readable location from that snapshot.
 * @param location Booked coordinate shown on the map.
 * @param reservationIds Stays at this campsite, in list order.
 */
data class ReservationPin(
    val campsiteId: String,
    val name: String,
    val locationLabel: String,
    val location: Coordinates,
    val reservationIds: List<String>,
)

/** Groups active and past stays by campsite, keeping the order the list shows them in.
 * @param items Current reservation rows.
 * @return One pin per campsite; cancelled stays are left out.
 */
fun reservationPins(items: List<ReservationListItem>): List<ReservationPin> =
    items
        .filter { it.category != ReservationCategory.CANCELLED }
        .groupBy { it.reservation.campsiteId }
        .map { (campsiteId, rows) ->
            val booked = rows.first().reservation.bookedCampsite
            ReservationPin(
                campsiteId,
                booked.name,
                booked.locationLabel,
                booked.location,
                rows.map { it.reservation.id },
            )
        }

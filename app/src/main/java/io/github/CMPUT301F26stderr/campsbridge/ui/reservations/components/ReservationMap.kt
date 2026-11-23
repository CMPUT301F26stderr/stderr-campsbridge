/*
 * Entrant reservation map: one pin per booked campsite and the chosen pin's saved stays.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.CampsitePinsMap
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.MapPin
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListItem
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic.reservationPins
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows the rows' campsites as pins and lists the chosen campsite's stays below the map.
 * @param items Current rows from the shared list source.
 * @param onOpen Opens a saved reservation identity.
 * @param onShowReservations Reports the stays whose photos are on screen.
 * @param modifier Caller layout.
 * @param photos Supplemental photos keyed by campsite identity.
 * @param onRetryPhoto Retries one failed photo.
 */
@Composable
fun ReservationMapContent(
    items: List<ReservationListItem>,
    onOpen: (String) -> Unit,
    onShowReservations: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    photos: Map<String, ImageSlotState> = emptyMap(),
    onRetryPhoto: (String) -> Unit = {},
) {
    val pins = remember(items) { reservationPins(items) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = pins.firstOrNull { it.campsiteId == selectedId }
    val rows = selected?.let { pin -> items.filter { it.reservation.id in pin.reservationIds } }.orEmpty()
    // Only the chosen pin's cards are on screen, so only their photos are requested.
    LaunchedEffect(rows) { onShowReservations(rows.map { it.reservation.id }.toSet()) }
    if (pins.isEmpty()) {
        EmptyContent(stringResource(R.string.reservation_map_empty))
        return
    }
    Column(modifier) {
        CampsitePinsMap(
            pins.map { MapPin(it.campsiteId, it.name, it.location) },
            selected?.campsiteId,
            { selectedId = it },
            stringResource(R.string.reservation_map_description),
            stringResource(R.string.reservation_map_unavailable),
            Modifier.fillMaxWidth().weight(1f).testTag("reservation-map"),
        )
        if (selected == null) {
            Text(
                stringResource(R.string.reservation_map_hint),
                Modifier.padding(Spacing.medium),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                items(rows, key = { "reservation:${it.reservation.id}" }) { row ->
                    val record = row.reservation
                    ReservationCard(
                        record,
                        reservationCategoryLabel(row.category),
                        { onOpen(record.id) },
                        Modifier.testTag("reservation:${record.id}"),
                        photo = photos[record.campsiteId] ?: ImageSlotState(),
                        onRetryPhoto = { onRetryPhoto(record.campsiteId) },
                    )
                }
            }
        }
    }
}

/** Localized status shown on a reservation card.
 * @param category Saved stay category for the booked-local date.
 */
@Composable
fun reservationCategoryLabel(category: ReservationCategory): String =
    stringResource(
        when (category) {
            ReservationCategory.UPCOMING -> R.string.reservation_upcoming
            ReservationCategory.IN_PROGRESS -> R.string.reservation_in_progress
            ReservationCategory.PAST -> R.string.reservation_completed
            ReservationCategory.CANCELLED -> R.string.reservation_cancelled
        },
    )

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import java.time.Instant
import java.time.ZoneId

/** Identifies the exact retained record on each historical page.
 * @param record Complete saved reservation.
 */
@Composable
internal fun ReservationReference(record: Reservation) {
    SummarySection(stringResource(R.string.reservation_reference)) {
        DetailRow(stringResource(R.string.reservation_reference), record.id)
        DetailRow(
            stringResource(R.string.reservation_booked_at),
            reservationTime(record, record.createdAt),
        )
    }
}

/** Renders recorded cancellation metadata without exposing a mutation action.
 * @param record Saved history; an uncancelled record contributes no section.
 */
@Composable
internal fun ReservationCancellationDetails(record: Reservation) {
    val cancellation = record.cancellation ?: return
    SummarySection(stringResource(R.string.reservation_cancellation)) {
        DetailRow(
            stringResource(R.string.reservation_cancelled_at),
            reservationTime(record, cancellation.at),
        )
        cancellation.reason?.takeIf { it.isNotBlank() }?.let { reason ->
            DetailRow(stringResource(R.string.reservation_reason), reason)
        }
    }
}

/** Names the initiator from saved history rather than the current viewer's role.
 * @param record Saved reservation with optional cancellation metadata.
 */
@Composable
internal fun reservationStatusLabel(record: Reservation): String =
    stringResource(
        when (record.cancellation?.initiatedAs) {
            Role.OWNER -> R.string.reservation_cancelled_by_owner
            Role.ADMIN -> R.string.reservation_cancelled_by_admin
            Role.ENTRANT -> R.string.reservation_cancelled_by_entrant
            null -> R.string.reservation_confirmed
        },
    )

/** Formats a saved timestamp in its booked campsite's explicit timezone.
 * @param record Source of the historical timezone.
 * @param instant Saved event time.
 */
@Composable
internal fun reservationTime(
    record: Reservation,
    instant: Instant,
): String =
    formatDateTime(
        instant,
        ZoneId.of(record.bookedCampsite.timeZone),
        LocalConfiguration.current.locales[0],
    )

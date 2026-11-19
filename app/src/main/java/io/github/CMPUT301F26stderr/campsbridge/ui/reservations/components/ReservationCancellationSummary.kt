package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shows the saved initiator and campsite-local cancellation time on historical rows.
 * @param record Saved stay whose cancellation is authoritative.
 */
@Composable
internal fun ReservationCancellationSummary(record: Reservation) {
    val cancellation = record.cancellation ?: return
    Text(reservationStatusLabel(record))
    val format =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(LocalConfiguration.current.locales[0])
            .withZone(ZoneId.of(record.bookedCampsite.timeZone))
    Text("${stringResource(R.string.reservation_cancelled_at)}: ${format.format(cancellation.at)}")
}

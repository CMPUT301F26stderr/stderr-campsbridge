package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Clickable historical stay summary; status is supplied rather than inferred from a clock.
 * @param reservation Stored stay and payment aggregate.
 * @param status Caller-derived state label.
 * @param onClick Parent-owned detail action.
 * @param modifier Caller layout.
 */
@Composable
fun ReservationCard(
    reservation: Reservation,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Card(onClick, modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(reservation.bookedCampsite.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.stay_dates,
                    formatDate(reservation.checkInDate, locale),
                    formatDate(reservation.checkOutDate, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatCad(reservation.payment.amountCents, locale),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

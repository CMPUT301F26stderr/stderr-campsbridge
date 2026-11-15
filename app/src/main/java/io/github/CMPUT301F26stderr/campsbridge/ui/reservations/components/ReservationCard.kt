/*
 * Shows saved stay facts with an independently supplied current photo and optional owner label.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows saved stay facts with an independently supplied current photo and optional owner label.
 * @param reservation Complete saved stay.
 * @param status Localized category supplied by the caller.
 * @param onClick Opens this saved identity.
 * @param modifier Parent layout and semantics.
 * @param photo Supplemental image read outcome.
 * @param onRetryPhoto Retries only this photo.
 * @param supportingContent Optional owner-row label.
 */
@Composable
fun ReservationCard(
    reservation: Reservation,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photo: ImageSlotState = ImageSlotState(loadState = ImageLoadState.READY),
    onRetryPhoto: () -> Unit = {},
    supportingContent: @Composable (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                ReservationPhoto(photo, reservation.bookedCampsite.name)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Text(
                        reservation.bookedCampsite.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        reservation.bookedCampsite.locationLabel,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    stringResource(
                        R.string.stay_dates,
                        formatDate(reservation.checkInDate, locale),
                        formatDate(reservation.checkOutDate, locale),
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.guests,
                        reservation.guestCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        reservation.guestCount,
                    ),
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    formatCad(reservation.payment.amountCents, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            supportingContent?.invoke()
            if (photo.loadState == ImageLoadState.FAILED || photo.displayFailed) {
                TextButton(
                    onClick = onRetryPhoto,
                ) { Text(stringResource(R.string.reservation_retry_photo)) }
            }
        }
    }
}

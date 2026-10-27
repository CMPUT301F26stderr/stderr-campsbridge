package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Compact saved capacity and nightly price, wrapping together at enlarged text sizes.
 * @param campsite Authoritative saved listing facts.
 * @param modifier Caller layout.
 */
@Composable
fun CampsiteFacts(
    campsite: Campsite,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            stringResource(R.string.up_to_guests, campsite.details.maxGuests),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                R.string.per_night,
                formatCad(
                    campsite.details.nightlyPriceCents,
                    LocalConfiguration.current.locales[0],
                ),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

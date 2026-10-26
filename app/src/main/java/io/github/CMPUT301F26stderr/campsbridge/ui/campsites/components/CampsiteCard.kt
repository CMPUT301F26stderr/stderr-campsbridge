package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Clickable campsite identity/facts shared by discovery and listing contexts.
 * @param campsite Supplied application record; no lookup occurs during composition.
 * @param photoReference Optional display URL or local fixture reference.
 * @param onClick Parent-owned open action.
 * @param modifier Caller layout.
 * @param status Optional caller-derived lifecycle/eligibility text.
 */
@Composable
fun CampsiteCard(
    campsite: Campsite,
    photoReference: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
) {
    Card(onClick, modifier.fillMaxWidth()) {
        RemoteImage(
            photoReference,
            campsite.details.name,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            compactFailure = true,
        )
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(campsite.details.name, style = MaterialTheme.typography.titleLarge)
            Text(campsite.details.locationLabel, style = MaterialTheme.typography.bodyMedium)
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.up_to_guests, campsite.details.maxGuests),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.per_night,
                    formatCad(
                        campsite.details.nightlyPriceCents,
                        LocalConfiguration.current.locales[0],
                    ),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** Compact listing row for owner inventory, choices and administrative inspection.
 * @param campsite Supplied listing identity and facts.
 * @param photoReference Optional thumbnail reference.
 * @param onClick Parent-owned detail action.
 * @param modifier Caller layout.
 * @param status Optional caller-derived state label.
 * @param showPrice Whether this compact row includes the shared nightly price label.
 */
@Composable
fun CampsiteRow(
    campsite: Campsite,
    photoReference: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    showPrice: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(campsite.details.name) },
        supportingContent = {
            Column {
                Text(campsite.details.locationLabel)
                if (showPrice) {
                    Text(
                        stringResource(
                            R.string.per_night,
                            formatCad(
                                campsite.details.nightlyPriceCents,
                                LocalConfiguration.current.locales[0],
                            ),
                        ),
                    )
                }
            }
        },
        overlineContent = status?.let { { Text(it) } },
        leadingContent = {
            RemoteImage(
                photoReference,
                campsite.details.name,
                Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                compactFailure = true,
            )
        },
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

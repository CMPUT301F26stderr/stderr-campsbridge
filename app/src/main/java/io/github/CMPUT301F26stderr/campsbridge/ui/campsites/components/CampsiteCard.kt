package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
 * @param ratingText Optional real rating read feedback supplied by the caller.
 * @param photoContent Optional caller-owned read feedback inside the standard image geometry.
 */
@Composable
fun CampsiteCard(
    campsite: Campsite,
    photoReference: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    ratingText: String? = null,
    photoContent: (@Composable () -> Unit)? = null,
) {
    OutlinedCard(
        onClick,
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        if (photoContent == null) {
            RemoteImage(
                photoReference,
                campsite.details.name,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                compactFailure = true,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                photoContent()
            }
        }
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(campsite.details.name, style = MaterialTheme.typography.titleLarge)
            Text(
                campsite.details.locationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ratingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            CampsiteFacts(campsite)
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
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                photoReference,
                campsite.details.name,
                Modifier.size(80.dp).clip(MaterialTheme.shapes.small),
                compactFailure = true,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(campsite.details.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    campsite.details.locationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showPrice) {
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

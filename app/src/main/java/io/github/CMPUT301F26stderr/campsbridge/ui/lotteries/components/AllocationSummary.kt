package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteFacts
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.formatNightRange
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.nightRanges
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.TagLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays offered campsite facts without checking free inventory or changing the allocation.
 * @param offer Saved or locally applied offered nights.
 * @param campsite Optional resolved saved listing.
 * @param modifier Caller layout.
 * @param ownerLabel Optional resolved owner label.
 * @param actions Optional caller content receiving the allocated campsite identity.
 */
@Composable
fun AllocationSummary(
    offer: LotteryOffer,
    campsite: Campsite?,
    modifier: Modifier = Modifier,
    ownerLabel: String? = null,
    actions: @Composable ((String) -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    SummarySection(
        campsite?.details?.name ?: stringResource(R.string.lottery_missing_campsite, offer.campsiteId),
        modifier,
    ) {
        if (ownerLabel != null) {
            Text(
                ownerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (campsite != null) {
            Text(campsite.details.locationLabel, style = MaterialTheme.typography.bodyMedium)
            CampsiteFacts(campsite)
        }
        Text(
            pluralStringResource(R.plurals.allocation_offered_nights, offer.nights.size, offer.nights.size),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            for (range in nightRanges(offer.nights.toSet())) TagLabel(formatNightRange(range, locale))
        }
        actions?.invoke(offer.campsiteId)
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceCard
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryCampsiteChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState

/** One owned published choice and one explicitly applied night allocation.
 * @param state Parent input and independent choice source.
 * @param onChoose Selects an actual campsite identity.
 * @param onAllocate Opens the working allocation child.
 * @param onRetry Retries only the failed choice source.
 */
@Composable
fun LotteryCampsiteStep(
    state: LotterySetupUiState,
    onChoose: (String) -> Unit,
    onAllocate: () -> Unit,
    onRetry: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    when (val choices = state.choices) {
        LotteryCampsiteChoices.Loading -> {
            LoadingContent()
        }

        LotteryCampsiteChoices.Failure -> {
            FailureContent(stringResource(R.string.lottery_site_failure), onRetry)
        }

        is LotteryCampsiteChoices.Content -> {
            if (choices.campsites.isEmpty()) Text(stringResource(R.string.lottery_no_sites))
            for (site in choices.campsites) {
                ChoiceCard(
                    site.details.name,
                    state.draft.campsiteId == site.id,
                    { onChoose(site.id) },
                    Icons.Outlined.Place,
                    supportingText =
                        site.details.locationLabel + " · " +
                            stringResource(
                                R.string.per_night,
                                formatCad(site.details.nightlyPriceCents, locale),
                            ),
                )
            }
            if (choices.campsites.any { it.id == state.draft.campsiteId }) {
                val count = state.draft.nights.size
                Text(pluralStringResource(R.plurals.lottery_allocated_nights, count, count))
                TextButton(onAllocate) { Text(stringResource(R.string.lottery_allocate)) }
            }
        }
    }
    if (state.errors.campsiteRequired) Text(stringResource(R.string.lottery_site_required))
    if (state.errors.allocationRequired) Text(stringResource(R.string.lottery_allocation_required))
}

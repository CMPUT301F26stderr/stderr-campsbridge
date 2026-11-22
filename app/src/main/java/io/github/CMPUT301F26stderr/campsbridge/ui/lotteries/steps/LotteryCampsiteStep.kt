/*
 * Keeps each applied campsite allocation visible while one calendar has editing focus.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.formatNightRange
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.nightRanges
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceCard
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryCampsiteChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState

/** Focuses an owned campsite while retaining and showing every applied allocation.
 * @param state Parent allocations and current campsite source.
 * @param onChoose Focuses a published campsite without discarding applied offers.
 * @param onAllocate Opens the focused campsite's working calendar.
 * @param onRetry Retries only the failed choice source.
 * @param onRemove Removes one applied allocation locally.
 */
@Composable
fun LotteryCampsiteStep(
    state: LotterySetupUiState,
    onChoose: (String) -> Unit,
    onAllocate: () -> Unit,
    onRetry: () -> Unit,
    onRemove: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(stringResource(R.string.lottery_campsite_focus_hint))
    when (val choices = state.choices) {
        LotteryCampsiteChoices.Loading -> {
            LoadingContent()
        }

        LotteryCampsiteChoices.Failure -> {
            FailureContent(stringResource(R.string.lottery_site_failure), onRetry)
        }

        is LotteryCampsiteChoices.Content -> {
            val sites = choices.campsites.associateBy { it.id }
            val offers = state.draft.allocations.associateBy { it.campsiteId }
            if (sites.isEmpty()) Text(stringResource(R.string.lottery_no_sites))
            for (id in sites.keys + offers.keys) {
                val site = sites[id]
                val offer = offers[id]
                val name = site?.details?.name ?: stringResource(R.string.lottery_missing_campsite, id)
                ChoiceCard(
                    name,
                    state.draft.campsiteId == id,
                    { onChoose(id) },
                    Icons.Outlined.Place,
                    enabled = site != null,
                    supportingText =
                        offer?.let {
                            nightRanges(
                                it.nights.toSet(),
                            ).joinToString("\n") { range -> formatNightRange(range, locale) }
                        } ?: site?.let {
                            it.details.locationLabel + " · " +
                                stringResource(
                                    R.string.per_night,
                                    formatCad(it.details.nightlyPriceCents, locale),
                                )
                        },
                )
                if (offer != null) {
                    val label = stringResource(R.string.lottery_remove_campsite_label, name)
                    TextButton(
                        { onRemove(id) },
                        Modifier.semantics { contentDescription = label },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.lottery_remove_offer)) }
                }
            }
            sites[state.draft.campsiteId]?.let { focused ->
                Text(stringResource(R.string.lottery_edit_nights_for, focused.details.name))
                val count = state.draft.nights.size
                Text(pluralStringResource(R.plurals.lottery_allocated_nights, count, count))
                TextButton(onAllocate) { Text(stringResource(R.string.lottery_allocate)) }
            }
        }
    }
    if (state.errors.campsiteRequired) Text(stringResource(R.string.lottery_selected_site_unavailable))
    if (state.errors.allocationRequired) Text(stringResource(R.string.lottery_allocation_required))
}

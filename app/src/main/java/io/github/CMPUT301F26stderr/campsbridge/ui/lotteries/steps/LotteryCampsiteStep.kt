/*
 * Applied owned allocations with a separate published-campsite picker and working calendar.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocatedNights
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryCampsiteChoices
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Applied owned allocations with a separate published-campsite picker and working calendar.
 * @param state Local applied offers and current owner choices.
 * @param onAllocate Opens the chosen campsite's working calendar.
 * @param onRemove Removes one applied offer from this local form.
 * @param onRetry Retries only the failed choice source.
 */
@Composable
fun LotteryCampsiteStep(
    state: LotterySetupUiState,
    onAllocate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    when (val choices = state.choices) {
        LotteryCampsiteChoices.Loading -> {
            LoadingContent()
        }

        LotteryCampsiteChoices.Failure -> {
            FailureContent(stringResource(R.string.lottery_site_failure), onRetry)
        }

        is LotteryCampsiteChoices.Content -> {
            val sites = choices.campsites.associateBy { it.id }
            for (offer in state.draft.allocations) {
                val site = sites[offer.campsiteId]
                val name = site?.details?.name ?: stringResource(R.string.lottery_missing_campsite, offer.campsiteId)
                val editLabel = stringResource(R.string.lottery_edit_nights_for, name)
                val removeLabel = stringResource(R.string.lottery_remove_campsite_label, name)
                SummarySection(name) {
                    LotteryAllocatedNights(offer.nights.toSet())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        TextButton(
                            { onAllocate(offer.campsiteId) },
                            Modifier.semantics { contentDescription = editLabel },
                            enabled = site != null,
                        ) { Text(stringResource(R.string.lottery_edit_nights)) }
                        TextButton(
                            { onRemove(offer.campsiteId) },
                            Modifier.semantics { contentDescription = removeLabel },
                        ) { Text(stringResource(R.string.lottery_remove_offer)) }
                    }
                }
            }
            if (state.draft.allocations.isEmpty()) {
                val suggested = sites[state.draft.campsiteId]
                if (suggested != null) {
                    SummarySection(suggested.details.name) {
                        Text(stringResource(R.string.lottery_no_offers), style = MaterialTheme.typography.bodyMedium)
                        TextButton({ onAllocate(suggested.id) }) { Text(stringResource(R.string.lottery_allocate)) }
                    }
                } else {
                    Text(stringResource(R.string.lottery_no_offers))
                }
            }
            val available =
                choices.campsites.filter { site ->
                    state.draft.allocations.none { it.campsiteId == site.id }
                }
            if (choices.campsites.isEmpty()) Text(stringResource(R.string.lottery_no_sites))
            OutlinedButton({ choosing = true }, enabled = available.isNotEmpty()) {
                Text(stringResource(R.string.lottery_add_campsite))
            }
            if (choosing) {
                LotteryCampsitePicker(available, { id ->
                    choosing = false
                    onAllocate(id)
                }, { choosing = false })
            }
        }
    }
    if (state.errors.campsiteRequired) Text(stringResource(R.string.lottery_site_required))
    if (state.errors.allocationRequired) Text(stringResource(R.string.lottery_allocation_required))
}

/** Lists unallocated published choices; selecting one opens its calendar without applying nights.
 * @param campsites Current available owned choices.
 * @param onSelect Opens the selected identity.
 * @param onDismiss Keeps the applied list unchanged.
 */
@Composable
private fun LotteryCampsitePicker(
    campsites: List<Campsite>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lottery_add_campsite)) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                for (site in campsites) {
                    TextButton({ onSelect(site.id) }) {
                        Column {
                            Text(site.details.name)
                            Text(site.details.locationLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

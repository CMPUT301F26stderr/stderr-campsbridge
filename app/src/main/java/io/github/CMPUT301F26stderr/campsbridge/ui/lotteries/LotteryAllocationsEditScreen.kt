package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.AllocationSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays this owner's complete local set, with one explicit saved-lottery replacement.
 * @param state Required context, local offers and action feedback.
 * @param saving Root editor operation, retained even during a required-source failure.
 * @param onBack Requests leaving under the editor's discard rules.
 * @param onRetry Reloads required saved context.
 * @param onSave Saves the complete owned set, including empty.
 * @param onAdd Opens the eligible owned campsite picker.
 * @param onEdit Opens temporary dates for an existing allocation.
 * @param onRemove Removes only a local allocation.
 */
@Composable
fun LotteryAllocationsEditScreen(
    state: LotteryAllocationsEditUiState,
    saving: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val content = state as? LotteryAllocationsEditUiState.Content
    AppScreen(
        stringResource(R.string.lottery_allocations_editor_title),
        onBack = onBack,
        backEnabled = !saving,
        bottomContent = {
            if (content != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.lottery_allocations_save),
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled = content.canSave,
                        isSubmitting = saving,
                        progressDescription = stringResource(R.string.lottery_allocations_saving),
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state) {
                LotteryAllocationsEditUiState.Loading -> {
                    LoadingContent()
                }

                LotteryAllocationsEditUiState.Failure -> {
                    FailureContent(stringResource(R.string.lottery_site_failure), onRetry)
                }

                LotteryAllocationsEditUiState.Unavailable -> {
                    Text(stringResource(R.string.lottery_unavailable))
                }

                is LotteryAllocationsEditUiState.Content -> {
                    Text(state.lottery.title, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.lottery_allocations_hint), style = MaterialTheme.typography.bodyMedium)
                    if (!state.isEditable) AllocationLockedNotice()
                    if (state.contextChanged) {
                        InlineNotice(
                            stringResource(R.string.lottery_edit_review_title),
                            stringResource(R.string.lottery_allocations_changed),
                        )
                        Text(
                            stringResource(R.string.lottery_allocations_saved),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.savedOffers.isEmpty()) Text(stringResource(R.string.lottery_allocations_empty))
                        for (offer in state.savedOffers) {
                            AllocationSummary(offer, state.campsites.firstOrNull { it.id == offer.campsiteId })
                        }
                        Text(
                            stringResource(R.string.lottery_allocations_local),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (state.saveFailed) {
                        FailureContent(
                            stringResource(R.string.lottery_allocations_failed),
                            onSave,
                            retryEnabled = state.canSave,
                        )
                    }
                    if (state.offers.isEmpty()) Text(stringResource(R.string.lottery_allocations_empty))
                    for (offer in state.offers) {
                        val site = state.campsites.firstOrNull { it.id == offer.campsiteId }
                        val name =
                            site?.details?.name ?: stringResource(R.string.lottery_missing_campsite, offer.campsiteId)
                        AllocationSummary(offer, site, actions = { id ->
                            if (site?.isPublished !=
                                true
                            ) {
                                Text(stringResource(R.string.lottery_allocations_unavailable))
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                val editLabel = stringResource(R.string.lottery_edit_nights_for, name)
                                val removeLabel = stringResource(R.string.lottery_remove_campsite_label, name)
                                TextButton(
                                    { onEdit(id) },
                                    Modifier.semantics { contentDescription = editLabel },
                                    enabled = state.isEditable && !saving && site?.isPublished == true,
                                ) {
                                    Text(stringResource(R.string.lottery_edit_nights))
                                }
                                TextButton(
                                    { onRemove(id) },
                                    Modifier.semantics { contentDescription = removeLabel },
                                    enabled = state.isEditable && !saving,
                                ) { Text(stringResource(R.string.lottery_remove_offer)) }
                            }
                        })
                    }
                    AppActionButton(
                        stringResource(R.string.lottery_add_campsite),
                        onAdd,
                        enabled = state.isEditable && !saving,
                        style = ActionStyle.SECONDARY,
                    )
                }
            }
        }
    }
}

/** Lists published owned choices without changing the applied set when a row is opened.
 * @param state Current saved editor context.
 * @param onBack Returns without changing any allocation.
 * @param onRetry Reloads required context.
 * @param onChoose Opens one temporary calendar.
 */
@Composable
fun LotteryAllocationChoiceScreen(
    state: LotteryAllocationsEditUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AppScreen(stringResource(R.string.lottery_add_campsite), onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state) {
                LotteryAllocationsEditUiState.Loading -> {
                    LoadingContent()
                }

                LotteryAllocationsEditUiState.Failure -> {
                    FailureContent(stringResource(R.string.lottery_site_failure), onRetry)
                }

                LotteryAllocationsEditUiState.Unavailable -> {
                    Text(stringResource(R.string.lottery_unavailable))
                }

                is LotteryAllocationsEditUiState.Content -> {
                    if (!state.isEditable) AllocationLockedNotice()
                    val choices =
                        state.campsites.filter { site ->
                            site.isPublished && state.offers.none { it.campsiteId == site.id }
                        }
                    if (choices.isEmpty()) {
                        EmptyContent(stringResource(R.string.lottery_allocations_no_choices))
                        Text(stringResource(R.string.lottery_allocations_no_choices_body))
                    } else {
                        NavigationGroup(
                            buildList<@Composable () -> Unit> {
                                for (site in choices) {
                                    add {
                                        NavigationRow(
                                            site.details.name,
                                            { onChoose(site.id) },
                                            supportingText = site.details.locationLabel,
                                            enabled = state.isEditable,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Explains why the observed saved context no longer permits allocation editing. */
@Composable
internal fun AllocationLockedNotice() {
    InlineNotice(
        stringResource(R.string.lottery_allocations_locked),
        stringResource(R.string.lottery_allocations_locked_body),
    )
}

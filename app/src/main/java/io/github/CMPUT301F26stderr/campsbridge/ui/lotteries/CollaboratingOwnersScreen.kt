/*
 * Shows saved member context and local checkboxes with one explicit complete-set Save.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppSearchField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shows saved member context and local checkboxes with one explicit complete-set Save.
 * @param state Required-source outcome, retained query/selections and action feedback.
 * @param onBack Requests leaving under the picker's discard rules.
 * @param onQuery Changes local search only.
 * @param onSelection Requests one permitted local checkbox value.
 * @param onSave Saves the complete membership set.
 * @param onRetry Reloads required context without submitting.
 */
@Composable
fun CollaboratingOwnersScreen(
    state: CollaboratingOwnersUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onSelection: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    AppScreen(
        stringResource(R.string.lottery_owners_title),
        onBack = onBack,
        backEnabled = !state.isSaving,
        bottomContent = {
            if (state.canEdit || state.isSaving || state.saveFailed || state.contextChanged) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.lottery_owners_save),
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled =
                            state.canSave &&
                                (
                                    state.selectedOwnerIds != state.savedOwnerIds || state.saveFailed ||
                                        state.contextChanged
                                ),
                        isSubmitting = state.isSaving,
                        progressDescription = stringResource(R.string.lottery_owners_saving),
                    )
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state.targetLoad) {
                LotteryTargetLoad.LOADING -> {
                    LoadingContent()
                }

                LotteryTargetLoad.FAILURE -> {
                    FailureContent(
                        stringResource(R.string.lottery_owners_load_failed),
                        onRetry,
                        retryEnabled = !state.isSaving,
                    )
                }

                LotteryTargetLoad.UNAVAILABLE -> {
                    Text(stringResource(R.string.lottery_unavailable))
                }

                LotteryTargetLoad.READY -> {
                    state.lottery?.let { Text(it.title, style = MaterialTheme.typography.headlineSmall) }
                    Text(stringResource(R.string.lottery_owners_hint), style = MaterialTheme.typography.bodyMedium)
                    if (!state.canEdit) {
                        InlineNotice(
                            stringResource(R.string.lottery_owners_locked),
                            stringResource(R.string.lottery_owners_locked_body),
                        )
                    }
                    if (state.needsPublishedCampsites) {
                        InlineNotice(
                            stringResource(R.string.lottery_owners_publish),
                            stringResource(R.string.lottery_owners_publish_body),
                            action = {
                                AppActionButton(
                                    stringResource(R.string.lottery_owners_refresh),
                                    onRetry,
                                    enabled = !state.isSaving,
                                    style = ActionStyle.TEXT,
                                )
                            },
                        )
                    }
                    if (state.contextChanged) {
                        InlineNotice(
                            stringResource(R.string.lottery_edit_review_title),
                            stringResource(R.string.lottery_owners_changed),
                        )
                        Text(
                            stringResource(R.string.lottery_owners_saved),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        for (owner in state.owners.filter { it.id in state.savedOwnerIds }) {
                            val name = owner.name ?: stringResource(R.string.lottery_owners_unavailable)
                            Text(
                                if (owner.showId ||
                                    owner.name == null
                                ) {
                                    stringResource(R.string.lottery_owners_identity, name, owner.id)
                                } else {
                                    name
                                },
                            )
                        }
                        Text(
                            stringResource(R.string.lottery_owners_local),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (state.saveFailed) {
                        FailureContent(
                            stringResource(R.string.lottery_owners_save_failed),
                            onSave,
                            retryEnabled = state.canSave,
                        )
                    }
                    AppSearchField(
                        state.query,
                        onQuery,
                        stringResource(R.string.lottery_owners_search),
                        enabled = !state.isSaving,
                    )
                    NavigationGroup(
                        buildList<@Composable () -> Unit> {
                            for (owner in state.owners) {
                                add {
                                    CheckboxRow(
                                        owner.name ?: stringResource(R.string.lottery_owners_unavailable),
                                        owner.id in state.selectedOwnerIds,
                                        { onSelection(owner.id, it) },
                                        enabled = state.canEdit && !state.isSaving && owner.canChange,
                                        supportingText = ownerSupportingText(owner),
                                    )
                                }
                            }
                        },
                    )
                    if (state.query.isNotBlank() &&
                        state.owners.none { it.id !in state.savedOwnerIds && it.id !in state.selectedOwnerIds }
                    ) {
                        EmptyContent(stringResource(R.string.lottery_owners_no_matches))
                    }
                }
            }
        }
    }
}

/** Keeps duplicate identities and lock explanations readable beside the standard checkbox row.
 * @param owner Current identity and its membership restrictions.
 */
@Composable
private fun ownerSupportingText(owner: CollaboratingOwnerChoice): String? {
    val reasons = mutableListOf<String>()
    if (owner.showId || owner.name == null) reasons += owner.id
    when {
        owner.isOrganizer -> reasons += stringResource(R.string.lottery_owners_organizer)
        owner.hasAllocations -> reasons += stringResource(R.string.lottery_owners_allocated)
    }
    return reasons.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

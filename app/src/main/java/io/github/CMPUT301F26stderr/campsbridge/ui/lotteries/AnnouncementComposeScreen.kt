/*
 * Provides local composition and send/retry recovery controls for lottery announcements.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Provides local composition and send/retry recovery controls for lottery announcements.
 * @param state Immutable composition, frozen attempt and lifecycle phase facts.
 * @param onBack Requests back navigation with confirmation check.
 * @param onTitleChange Updates title field.
 * @param onBodyChange Updates message body field.
 * @param onSend Submits validated draft.
 * @param onRetrySend Retries unconfirmed attempt.
 * @param onEditAttempt Converts frozen attempt back to editable draft.
 * @param onDismissDiscard Hides discard confirmation dialog.
 * @param onConfirmDiscard Confirms discarding draft.
 * @param onDismissLeaveUnknown Hides leave unknown confirmation dialog.
 * @param onConfirmLeaveUnknown Confirms leaving in-flight/unconfirmed operation.
 * @param modifier Screen layout modifier.
 */
@Composable
fun AnnouncementComposeScreen(
    state: AnnouncementComposeUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: () -> Unit,
    onEditAttempt: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissLeaveUnknown: () -> Unit,
    onConfirmLeaveUnknown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = state.phase == AnnouncementPhase.IDLE && state.attempt == null && state.canSend

    AppScreen(
        title = stringResource(R.string.announcement_title),
        onBack = onBack,
        modifier = modifier,
        bottomContent = {
            ActionFooter {
                when (state.phase) {
                    AnnouncementPhase.IDLE -> {
                        AppActionButton(
                            label = stringResource(R.string.announcement_send),
                            onClick = onSend,
                            enabled = state.canSend,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnnouncementPhase.SENDING -> {
                        AppActionButton(
                            label = stringResource(R.string.announcement_sending),
                            onClick = {},
                            enabled = false,
                            isSubmitting = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnnouncementPhase.CHECKING -> {
                        AppActionButton(
                            label = stringResource(R.string.announcement_checking),
                            onClick = {},
                            enabled = false,
                            isSubmitting = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnnouncementPhase.ABSENT, AnnouncementPhase.RETRY -> {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                            AppActionButton(
                                label = stringResource(R.string.announcement_retry),
                                onClick = onRetrySend,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppActionButton(
                                label = stringResource(R.string.announcement_edit),
                                onClick = onEditAttempt,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    AnnouncementPhase.CONFLICT -> {
                        AppActionButton(
                            label = stringResource(R.string.announcement_new),
                            onClick = onEditAttempt,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.announcement_audience),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.canSend) {
                Text(
                    text = stringResource(R.string.announcement_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (state.phase) {
                AnnouncementPhase.SENDING -> {
                    Text(
                        text = stringResource(R.string.announcement_sending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                AnnouncementPhase.CHECKING -> {
                    Text(
                        text = stringResource(R.string.announcement_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                AnnouncementPhase.ABSENT -> {
                    Text(
                        text = stringResource(R.string.announcement_absent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AnnouncementPhase.RETRY -> {
                    Text(
                        text = stringResource(R.string.announcement_check_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AnnouncementPhase.CONFLICT -> {
                    Text(
                        text = stringResource(R.string.announcement_conflict),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AnnouncementPhase.IDLE -> Unit
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.announcement_title_label)) },
                isError = state.titleInvalid,
                supportingText =
                    if (state.titleInvalid) {
                        { Text(stringResource(R.string.announcement_title_required)) }
                    } else {
                        null
                    },
                enabled = editable,
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("announcement-title"),
            )

            OutlinedTextField(
                value = state.body,
                onValueChange = onBodyChange,
                label = { Text(stringResource(R.string.announcement_body_label)) },
                isError = state.bodyInvalid,
                supportingText =
                    if (state.bodyInvalid) {
                        { Text(stringResource(R.string.announcement_body_required)) }
                    } else {
                        null
                    },
                enabled = editable,
                minLines = 4,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("announcement-body"),
            )
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.announcement_discard_title)) },
            text = { Text(stringResource(R.string.announcement_discard_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text("Discard draft")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) {
                    Text("Keep draft")
                }
            },
        )
    }

    if (state.showLeaveUnknownConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissLeaveUnknown,
            title = { Text(stringResource(R.string.announcement_leave_unknown_title)) },
            text = { Text(stringResource(R.string.announcement_leave_unknown_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmLeaveUnknown) {
                    Text(stringResource(R.string.announcement_leave_unknown_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLeaveUnknown) {
                    Text("Keep checking")
                }
            },
        )
    }
}

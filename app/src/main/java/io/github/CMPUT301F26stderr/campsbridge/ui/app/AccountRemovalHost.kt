/*
 * Keeps root confirmation/cleanup feedback mounted when profile navigation disappears.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent

/** Keeps root confirmation/cleanup feedback mounted when profile navigation disappears.
 * @param state Actual original request and cleanup/recovery outcome.
 * @param onConfirm Confirms the current profile's destructive operation.
 * @param onCancel Cancels an unsubmitted confirmation or a request proved not to have started.
 * @param onRetry Explicitly retries the original cleanup.
 * @param onRetryRecovery Rechecks original identity/removal facts without writing.
 * @param onReview Starts fresh confirmation after proving the original profile is still active.
 */
@Composable
fun AccountRemovalHost(
    state: AccountRemovalUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRetryRecovery: () -> Unit,
    onReview: () -> Unit,
) {
    when (state.phase) {
        AccountRemovalPhase.IDLE, AccountRemovalPhase.COMPLETE -> {
            return
        }

        AccountRemovalPhase.CONFIRMING -> {
            ConfirmationDialog(
                stringResource(R.string.account_removal_title),
                stringResource(R.string.account_removal_body),
                onConfirm,
                onCancel,
                onCancel,
                confirmLabel = stringResource(R.string.account_removal_confirm),
                destructive = true,
            )
            return
        }

        AccountRemovalPhase.NOT_STARTED -> {
            ConfirmationDialog(
                stringResource(R.string.account_removal_not_started),
                stringResource(R.string.account_removal_active_body),
                onReview,
                onCancel,
                onCancel,
                confirmLabel = stringResource(R.string.account_removal_review),
            )
            return
        }

        else -> {
            Unit
        }
    }
    val pending = state.phase == AccountRemovalPhase.REMOVING || state.phase == AccountRemovalPhase.RESTORING
    val title =
        when (state.phase) {
            AccountRemovalPhase.REMOVING -> R.string.account_removal_progress
            AccountRemovalPhase.RESTORING -> R.string.account_removal_checking
            AccountRemovalPhase.RETRY -> R.string.account_removal_retry_title
            AccountRemovalPhase.RECOVERY_FAILED -> R.string.account_removal_check_failed
            else -> R.string.account_removal_unavailable
        }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(title)) },
        text = {
            if (pending) {
                LoadingContent(stringResource(R.string.account_removal_wait))
            } else {
                Text(
                    stringResource(
                        when (state.phase) {
                            AccountRemovalPhase.RETRY -> R.string.account_removal_incomplete
                            AccountRemovalPhase.RECOVERY_FAILED -> R.string.account_removal_check_failed_body
                            else -> R.string.account_removal_unavailable_body
                        },
                    ),
                )
            }
        },
        confirmButton = {
            if (!pending) {
                TextButton(if (state.phase == AccountRemovalPhase.RETRY) onRetry else onRetryRecovery) {
                    Text(
                        stringResource(
                            if (state.phase ==
                                AccountRemovalPhase.RETRY
                            ) {
                                R.string.account_removal_retry
                            } else {
                                R.string.account_removal_check
                            },
                        ),
                    )
                }
            }
        },
    )
}

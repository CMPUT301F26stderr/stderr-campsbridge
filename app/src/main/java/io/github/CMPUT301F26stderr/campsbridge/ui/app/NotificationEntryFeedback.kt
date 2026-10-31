package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Presents explicit tap recovery without acquiring models or changing ordinary screen state.
 * @param state Root-owned event/context outcome.
 * @param selectionFailed A requested workspace did not become confirmed; retry is explicit.
 * @param onRetry Retries only the failed read or workspace selection indicated by the caller.
 * @param onDismiss Closes feedback and retires the retained tap, including a held read.
 */
@Composable
internal fun NotificationEntryFeedback(
    state: NotificationEntryUiState,
    selectionFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == NotificationEntryUiState.None || state is NotificationEntryUiState.Waiting) return
    val failure =
        when {
            selectionFailed -> {
                R.string.notification_entry_workspace_failure
            }

            state is NotificationEntryUiState.Failure -> {
                R.string.notification_entry_failure
            }

            state is NotificationEntryUiState.Unavailable -> {
                R.string.notification_entry_unavailable
            }

            state is NotificationEntryUiState.WrongAccount -> {
                R.string.notification_entry_wrong_account
            }

            else -> {
                null
            }
        }
    val retry =
        selectionFailed || state is NotificationEntryUiState.Failure ||
            state is NotificationEntryUiState.Unavailable
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_entry_title)) },
        text = {
            if (failure == null) {
                LoadingContent(stringResource(R.string.notification_entry_loading))
            } else {
                Text(stringResource(failure))
            }
        },
        confirmButton = {
            if (retry) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.notification_entry_close)) }
        },
    )
}

/** Supplies passive root-feedback states without bootstrap, reads or pending production helpers. */
class NotificationEntryPreviewProvider : PreviewParameterProvider<NotificationEntryUiState> {
    override val values: Sequence<NotificationEntryUiState>
        get() {
            val entry =
                NotificationEntry(
                    "account",
                    "result",
                    NotificationSubject(SubjectType.LOTTERY, "draw"),
                )
            return sequenceOf(
                NotificationEntryUiState.Loading(entry),
                NotificationEntryUiState.Failure(entry),
                NotificationEntryUiState.Unavailable(entry),
                NotificationEntryUiState.WrongAccount(entry),
                NotificationEntryUiState.Ready(entry, Role.OWNER),
            )
        }
}

/** Shows ordinary Material feedback with inert callbacks for every visible read outcome.
 * @param state Fixture only; rendering starts no reads or writes.
 */
@Preview(showBackground = true)
@Composable
private fun NotificationEntryFeedbackPreview(
    @PreviewParameter(NotificationEntryPreviewProvider::class) state: NotificationEntryUiState,
) {
    CampsbridgeTheme { NotificationEntryFeedback(state, false, {}, {}) }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationAvailability
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.MessageSendPhase
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Arranges retained input and explicit send recovery without owning a submission.
 * @param state Current availability and retained send attempt.
 * @param onRetry Retries participant reads.
 * @param onText Retains unsent text in the controller.
 * @param onSend Submits one explicit message.
 * @param onRetrySend Reconciles the existing request.
 * @param onEditAttempt Abandons an absent or conflicting attempt before editing.
 */
@Composable
internal fun ConversationComposer(
    state: ConversationUiState,
    onRetry: () -> Unit,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: () -> Unit,
    onEditAttempt: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pending = state.phase == MessageSendPhase.SENDING || state.phase == MessageSendPhase.CHECKING
    when (state.availability) {
        ConversationAvailability.LOADING -> {
            LoadingContent(stringResource(R.string.conversation_profiles_loading))
        }

        ConversationAvailability.FAILURE -> {
            FailureContent(stringResource(R.string.conversation_profiles_failed), onRetry)
        }

        ConversationAvailability.UNAVAILABLE -> {
            Text(stringResource(R.string.conversation_send_unavailable))
            TextButton(onRetry, enabled = !pending) { Text(stringResource(R.string.conversation_refresh)) }
        }

        ConversationAvailability.AVAILABLE -> {}
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f || LocalControlScale.current > 1.3f
        val field: @Composable (Modifier) -> Unit = { layout ->
            AppTextField(
                state.input,
                onText,
                stringResource(R.string.conversation_input),
                layout.testTag("message-input"),
                enabled = state.canSend && state.attempt == null,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
            )
        }
        val send: @Composable (Modifier) -> Unit = { layout ->
            AppActionButton(
                stringResource(R.string.conversation_send),
                {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSend()
                },
                layout,
                enabled = state.phase == MessageSendPhase.IDLE && state.canSend && state.input.isNotBlank(),
                isSubmitting = state.phase == MessageSendPhase.SENDING,
                progressDescription = stringResource(R.string.conversation_sending),
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                field(Modifier.fillMaxWidth())
                send(Modifier.align(Alignment.End))
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.Bottom,
            ) {
                field(Modifier.weight(1f))
                send(Modifier)
            }
        }
    }
    when (state.phase) {
        MessageSendPhase.IDLE, MessageSendPhase.SENDING -> {}

        MessageSendPhase.CHECKING -> {
            LoadingContent(stringResource(R.string.conversation_checking))
        }

        MessageSendPhase.RETRY -> {
            FailureContent(
                stringResource(R.string.conversation_check_failed),
                onRetrySend,
                retryEnabled = state.canSend,
                retryLabel = stringResource(R.string.conversation_retry),
            )
        }

        MessageSendPhase.ABSENT -> {
            Text(stringResource(R.string.conversation_absent))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                TextButton(onRetrySend, enabled = state.canSend) { Text(stringResource(R.string.conversation_retry)) }
                TextButton(onEditAttempt) { Text(stringResource(R.string.conversation_edit)) }
            }
        }

        MessageSendPhase.CONFLICT -> {
            Text(stringResource(R.string.conversation_conflict))
            TextButton(onEditAttempt) { Text(stringResource(R.string.conversation_new_attempt)) }
        }
    }
}

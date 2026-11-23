package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.ConversationComposer
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.ConversationHistory
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays observed messages separately from the retained composer and explicit recovery.
 * @param state Actual relationship, messages and local command state.
 * @param onBack Requests shared Back or unsent-text confirmation.
 * @param onRetry Retries failed context/message reads without submitting text.
 * @param onText Retains raw multiline input locally.
 * @param onSend Freezes one new valid explicit request.
 * @param onRetrySend Reconciles/retries the retained request.
 * @param onEditAttempt Explicitly abandons an absent/conflicting request before editing.
 * @param onRetryReads Retries failed visible recipient acknowledgements.
 * @param onDismissDiscard Keeps the unsent draft.
 * @param onOpenReservation Opens the saved stay while preserving this conversation.
 * @param onConfirmDiscard Abandons only local composition and returns.
 */
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: () -> Unit,
    onEditAttempt: () -> Unit,
    onRetryReads: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onOpenReservation: () -> Unit = {},
) {
    val pending = state.phase == MessageSendPhase.SENDING || state.phase == MessageSendPhase.CHECKING
    AppScreen(
        stringResource(R.string.conversation_title),
        onBack = onBack,
        backEnabled = !pending,
        bottomContent = {
            if (state.read == ConversationRead.READY) {
                ActionFooter {
                    ConversationComposer(state, onRetry, onText, onSend, onRetrySend, onEditAttempt)
                }
            }
        },
    ) {
        when (state.read) {
            ConversationRead.LOADING -> {
                LoadingContent()
            }

            ConversationRead.FAILURE -> {
                FailureContent(stringResource(R.string.conversation_context_failed), onRetry)
            }

            ConversationRead.UNAVAILABLE -> {
                Text(stringResource(R.string.conversation_unavailable))
            }

            ConversationRead.READY -> {
                val stay = checkNotNull(state.context).reservation
                TextButton(onOpenReservation, Modifier.testTag("conversation-reservation"), enabled = !pending) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Text(stay.bookedCampsite.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.conversation_stay,
                                formatDate(stay.checkInDate, LocalConfiguration.current.locales[0]),
                                formatDate(stay.checkOutDate, LocalConfiguration.current.locales[0]),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ConversationHistory(state, onRetry, onRetryReads, Modifier.weight(1f))
            }
        }
    }

    if (state.confirmingDiscard) {
        val unknown = state.phase == MessageSendPhase.RETRY
        ConfirmationDialog(
            stringResource(
                if (unknown) R.string.conversation_leave_unknown_title else R.string.conversation_discard_title,
            ),
            stringResource(
                if (unknown) R.string.conversation_leave_unknown_body else R.string.conversation_discard_body,
            ),
            onConfirmDiscard,
            onDismissDiscard,
            onDismissDiscard,
            confirmLabel =
                stringResource(
                    if (unknown) R.string.conversation_leave_unknown_confirm else R.string.booking_discard,
                ),
            cancelLabel = stringResource(R.string.booking_keep_draft),
            destructive = true,
        )
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationAvailability
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.MessageSendPhase
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessagePresentation
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessageRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Keeps the reader's position while displaying confirmed history and explicit read recovery.
 * @param state Saved messages and current participant context.
 * @param onRetry Retries message observation without resending text.
 * @param onRetryReads Retries recipient acknowledgements.
 * @param modifier Bounded history area supplied by the screen.
 */
@Composable
internal fun ConversationHistory(
    state: ConversationUiState,
    onRetry: () -> Unit,
    onRetryReads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = checkNotNull(state.context)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(state.messages.lastOrNull())
    val nearEnd = with(LocalDensity.current) { 48.dp.roundToPx() }
    var following by rememberSaveable(context.reservation.id) { mutableStateOf(true) }
    var previousLatest by rememberSaveable(context.reservation.id) { mutableStateOf<String?>(null) }
    var newMessages by rememberSaveable(context.reservation.id) { mutableStateOf(false) }

    LaunchedEffect(list, context.reservation.id, nearEnd) {
        snapshotFlow { Triple(latest, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .collect { (message, index, offset) ->
                val atLatest = index == 0 && offset <= nearEnd
                if (message != null && message.id != previousLatest) {
                    // Read the previous viewport's follow mode before keyed insertion moves its anchor.
                    val followArrival = previousLatest == null || following || message.senderId == context.actorId
                    previousLatest = message.id
                    if (followArrival) {
                        following = true
                        newMessages = false
                        list.scrollToItem(0)
                    } else {
                        newMessages = true
                    }
                } else {
                    following = atLatest
                    if (atLatest) newMessages = false
                }
            }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (state.failedReadIds.isNotEmpty()) {
            FailureContent(stringResource(R.string.conversation_reads_failed), onRetryReads)
        }
        if (state.messagesFailed) {
            FailureContent(stringResource(R.string.conversation_messages_failed), onRetry)
        }
        if (state.messagesLoading) LoadingContent()
        if (!state.messagesLoading && !state.messagesFailed && state.messages.isEmpty()) {
            EmptyContent(stringResource(R.string.conversation_empty))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("conversation-history"),
            state = list,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(state.messages.asReversed(), key = { it.id }) { message ->
                MessageRow(
                    message,
                    state.labels[message.senderId] ?: message.senderId,
                    if (message.senderId == context.actorId) MessagePresentation.SENT else MessagePresentation.RECEIVED,
                )
            }
        }
        if (newMessages) {
            TextButton(
                onClick = {
                    following = true
                    newMessages = false
                    scope.launch { list.scrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.conversation_new_messages)) }
        }
    }
}

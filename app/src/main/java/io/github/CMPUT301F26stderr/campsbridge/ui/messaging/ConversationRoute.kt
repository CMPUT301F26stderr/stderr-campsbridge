package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects actual visibility to recipient reads and keeps navigation in the shared Back owner.
 * @param model Entry-scoped participant conversation controller.
 * @param onOpenReservation Opens the original saved stay without discarding the draft.
 * @param onBack Returns to the actual reservation or notification caller.
 */
@Composable
fun ConversationRoute(
    model: ConversationViewModel,
    onOpenReservation: () -> Unit,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(model, lifecycle) { model.setVisible(lifecycle == Lifecycle.State.RESUMED) }
    DisposableEffect(model) { onDispose { model.setVisible(false) } }
    ConversationScreen(
        state,
        onBack = { if (model.requestBack()) onBack() },
        onRetry = model::retry,
        onOpenReservation = onOpenReservation,
        onText = model::edit,
        onSend = model::send,
        onRetrySend = model::retrySend,
        onEditAttempt = model::editAttempt,
        onRetryReads = model::retryReads,
        onDismissDiscard = model::dismissDiscard,
        onConfirmDiscard = { if (model.confirmDiscard()) onBack() },
    )
}

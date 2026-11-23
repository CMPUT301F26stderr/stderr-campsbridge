/*
 * Connects the local form and consumes one confirmed result only while resumed.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects the local form and consumes one confirmed result only while resumed.
 * @param model Back-stack-owned original owner/lottery request.
 * @param onBack Returns after local discard rules permit it.
 * @param onSent Returns with confirmed immutable-event feedback.
 */
@Composable
fun AnnouncementComposeRoute(
    model: AnnouncementComposeViewModel,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(state.sent, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED && state.sent && model.consumeSent()) onSent()
    }
    AnnouncementComposeScreen(
        state,
        onBack = { if (model.requestBack()) onBack() },
        onTitle = model::editTitle,
        onBody = model::editBody,
        onSend = model::send,
        onRetry = model::retry,
        onRetrySend = model::retrySend,
        onEditAttempt = model::editAttempt,
        onDismissDiscard = model::dismissDiscard,
        onConfirmDiscard = { if (model.confirmDiscard()) onBack() },
    )
}

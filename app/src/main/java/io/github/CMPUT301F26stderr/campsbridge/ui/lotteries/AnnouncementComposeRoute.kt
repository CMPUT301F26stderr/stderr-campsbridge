/*
 * Binds announcement composition UI to its ViewModel owner and handles single-shot exit.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Binds announcement composition UI to its ViewModel owner and handles single-shot exit.
 * @param model Announcement composition view model.
 * @param onBack Single-shot pop back stack callback.
 * @param modifier Screen layout modifier.
 */
@Composable
fun AnnouncementComposeRoute(
    model: AnnouncementComposeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.sent) {
        if (model.consumeSent()) {
            onBack()
        }
    }

    AnnouncementComposeScreen(
        state = state,
        onBack = {
            if (model.requestBack()) {
                onBack()
            }
        },
        onTitleChange = model::editTitle,
        onBodyChange = model::editBody,
        onSend = model::send,
        onRetrySend = model::retrySend,
        onEditAttempt = model::editAttempt,
        onDismissDiscard = model::dismissDiscardConfirmation,
        onConfirmDiscard = {
            model.confirmDiscard()
            onBack()
        },
        onDismissLeaveUnknown = model::dismissLeaveUnknownConfirmation,
        onConfirmLeaveUnknown = {
            model.confirmLeaveUnknown()
            onBack()
        },
        modifier = modifier,
    )
}

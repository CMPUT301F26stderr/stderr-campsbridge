/*
 * Delivers explicit saved-target selection only from the resumed Sent page.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Delivers explicit saved-target selection only from the resumed Sent page.
 * @param model Original actor's read-only sender history owner.
 * @param onOpen Opens the lottery or actual participant conversation.
 */
@Composable
fun SentNotificationsRoute(
    model: SentNotificationsViewModel,
    onOpen: (SentTarget) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SentNotificationsScreen(state, model::editQuery, { id ->
        if (lifecycle.currentState == Lifecycle.State.RESUMED) model.targetFor(id)?.let(onOpen)
    }, model::retry, model::retryItem)
}

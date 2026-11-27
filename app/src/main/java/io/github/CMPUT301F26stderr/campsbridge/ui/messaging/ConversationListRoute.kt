/*
 * Connects the participant conversation list to its ViewModel and conversation navigation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects the participant conversation list to its ViewModel and conversation navigation.
 * @param model Account-scoped list owner.
 * @param onOpen Opens a reservation's existing conversation.
 */
@Composable
fun ConversationListRoute(
    model: ConversationListViewModel,
    onOpen: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    ConversationListScreen(state, onOpen, model::retry)
}

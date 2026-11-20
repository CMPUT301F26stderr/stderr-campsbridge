package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Reads an actual saved message independently of the list's local query.
 * @param model Shared admin log owner, never a participant conversation model.
 * @param id Original decoded saved message identity.
 * @param onBack Returns to retained tab/search/scroll.
 */
@Composable
fun MessageLogDetailRoute(
    model: MessageLogViewModel,
    id: String,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val rows by model.savedRows.collectAsStateWithLifecycle()
    MessageLogDetailScreen(
        row = rows.firstOrNull { it.message.id == id },
        read = state.read,
        id = id,
        onRetry = model::retry,
        onRetryContext = model::retryContext,
        onBack = onBack,
        history = model.conversation(id),
    )
}

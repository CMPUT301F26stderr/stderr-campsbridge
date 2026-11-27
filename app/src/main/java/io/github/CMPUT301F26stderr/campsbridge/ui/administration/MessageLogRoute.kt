/*
 * Collects the independent admin reader and opens only a current saved record.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Collects the independent admin reader and opens only a current saved record.
 * @param model Graph-owned read-only message log.
 * @param onOpen Navigates with an original saved message ID.
 */
@Composable
fun MessageLogRoute(
    model: MessageLogViewModel,
    onOpen: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scroll = rememberLazyListState()
    MessageLogScreen(state, model::editQuery, model::retry, model::retryContext, { id ->
        if (lifecycle.currentState == Lifecycle.State.RESUMED && model.find(id) != null) onOpen(id)
    }, scroll)
}

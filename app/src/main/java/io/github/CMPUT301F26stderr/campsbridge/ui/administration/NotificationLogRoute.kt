package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Binds retained filters and permits saved-ID navigation only from the current resumed list.
 * @param model One graph-scoped log owner.
 * @param onOpen Opens an actual immutable event identity.
 */
@Composable
fun NotificationLogRoute(
    model: NotificationLogViewModel,
    onOpen: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scroll = rememberLazyListState()
    NotificationLogScreen(
        state,
        model::editQuery,
        model::selectType,
        onOpen = { id ->
            val events = (model.state.value.events as? NotificationLogEvents.Content)?.events.orEmpty()
            if (lifecycle.currentState == Lifecycle.State.RESUMED && events.any { it.id == id }) onOpen(id)
        },
        onRetry = model::retry,
        listState = scroll,
    )
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects the current saved summary to its own graph-scoped read owner.
 * @param model Management graph owner, never borrowed from another context.
 * @param onBack Returns to the caller's retained primary destination.
 */
@Composable
fun CampsiteDetailRoute(
    model: CampsiteDetailViewModel,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    CampsiteManagementScreen(state, photo, onBack, model::retry, model::retryPhoto)
}

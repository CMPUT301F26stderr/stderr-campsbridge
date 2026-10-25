package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connects submitted criteria and visible-row read lifetime to the graph-owned model.
 * @param model Primary discovery owner, shared with its filter child.
 * @param onOpenFilters Opens the registered working filter child.
 * @param onOpenCampsite Opens a separate saved-ID detail parent.
 */
@Composable
fun ExploreRoute(
    model: ExploreViewModel,
    onOpenFilters: () -> Unit,
    onOpenCampsite: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(model, list) {
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .toSet()
        }.distinctUntilChanged()
            .collect(model::setVisibleCampsites)
    }
    DisposableEffect(model) { onDispose { model.setVisibleCampsites(emptySet()) } }
    ExploreScreen(state, model::editQuery, model::submitSearch, {
        model.beginFilters()
        onOpenFilters()
    }, onOpenCampsite, model::retry, model::retryRow, list)
}

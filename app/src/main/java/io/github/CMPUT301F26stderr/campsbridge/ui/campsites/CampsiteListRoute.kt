package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

/** Binds list state and visible-row read ownership without performing a write.
 * @param model Primary-destination inventory owner.
 * @param onOpenCampsite Navigation by saved identity.
 */
@Composable
fun CampsiteListRoute(
    model: CampsiteListViewModel,
    onOpenCampsite: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(model, list, tab) {
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .toSet()
        }.distinctUntilChanged().collect(model::setVisibleCampsites)
    }
    DisposableEffect(model) {
        onDispose { model.setVisibleCampsites(emptySet()) }
    }
    CampsiteListScreen(state, tab, model::selectTab, onOpenCampsite, model::retry, list)
}

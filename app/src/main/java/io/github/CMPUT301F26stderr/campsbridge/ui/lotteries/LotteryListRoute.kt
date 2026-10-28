package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connects the read owner to real navigation and retains a scroll position per category.
 * @param model Account-scoped source owner.
 * @param onCreate Registered new-setup entry, absent in entrant presentation.
 * @param onOpen Navigation using the selected actual row identity.
 */
@Composable
fun LotteryListRoute(
    model: LotteryListViewModel,
    onCreate: (() -> Unit)?,
    onOpen: (LotteryListItem) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val category by model.category.collectAsStateWithLifecycle()
    val campsite by model.campsiteId.collectAsStateWithLifecycle()
    val contexts by model.contexts.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(model, lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    // Each remembered category keeps its own saveable lazy-list position.
    val positions = model.categories.associateWith { item -> key(item) { rememberLazyListState() } }
    val listState = positions.getValue(category)
    val shownIds =
        (state as? LotteryListUiState.Content)
            ?.items
            .orEmpty()
            .map { it.lottery.id }
            .toSet()
    DisposableEffect(model) {
        onDispose { model.showVisibleLotteries(emptySet()) }
    }
    LaunchedEffect(model, listState, category, shownIds) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .filter { it in shownIds }
                .toSet()
        }.distinctUntilChanged().collect(model::showVisibleLotteries)
    }
    LotteryListScreen(
        state,
        category,
        model.categories,
        model::selectCategory,
        onOpen,
        model::retry,
        listState = listState,
        contexts = contexts,
        onCreate = onCreate,
        campsiteId = campsite,
        onClearCampsite = { model.selectCampsite(null) },
    )
}

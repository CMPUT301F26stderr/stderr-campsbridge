package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connects the list's lifecycle, visible photos and saved-ID navigation.
 * @param model Actor-scoped list controller.
 * @param onOpen Parent-owned saved destination action.
 */
@Composable
fun ReservationListRoute(
    model: ReservationListViewModel,
    onOpen: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val campsite by model.campsiteId.collectAsStateWithLifecycle()
    val photos by model.photos.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val rowIds =
        (state as? ReservationListUiState.Content)
            ?.items
            .orEmpty()
            .map {
                it.reservation.id
            }.toSet()
    LaunchedEffect(model, listState, rowIds) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { item ->
                    (item.key as? String)
                        ?.takeIf { it.startsWith("reservation:") }
                        ?.removePrefix("reservation:")
                        ?.takeIf { it in rowIds }
                }.toSet()
        }.distinctUntilChanged().collect(model::showVisibleReservations)
    }
    DisposableEffect(model, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refreshTime()
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            model.showVisibleReservations(emptySet())
        }
    }
    ReservationListScreen(
        state,
        model.context,
        campsite,
        model::selectCampsite,
        onOpen = { id ->
            val current = (model.state.value as? ReservationListUiState.Content)?.items.orEmpty()
            if (lifecycle.currentState == Lifecycle.State.RESUMED &&
                current.any { it.reservation.id == id }
            ) {
                onOpen(id)
            }
        },
        onRetry = model::retry,
        onRetryNames = model::retryNames,
        listState = listState,
        photos = photos.mapKeys { it.key.id },
        onRetryPhoto = model::retryPhoto,
    )
}

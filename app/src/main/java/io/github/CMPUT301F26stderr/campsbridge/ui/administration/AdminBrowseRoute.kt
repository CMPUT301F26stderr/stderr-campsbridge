/*
 * Binds graph-owned inputs and only the currently visible thumbnail readers.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import kotlinx.coroutines.flow.distinctUntilChanged

/** Binds graph-owned inputs and only the currently visible thumbnail readers.
 * @param model Browse state owned by the admin graph.
 * @param onProfile Opens a profile by account ID.
 * @param onImage Opens an image slot.
 */
@Composable
fun AdminBrowseRoute(
    model: AdminBrowseViewModel,
    onProfile: (String) -> Unit,
    onImage: (ImageTarget) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photos by model.photos.collectAsStateWithLifecycle()
    val campsiteList = rememberLazyListState()
    val profileList = rememberLazyListState()
    val grid = rememberLazyGridState()
    val targets =
        when (val content = state.content) {
            is AdminBrowseContent.Profiles -> {
                content.rows.map { ImageTarget(ImageTargetType.ACCOUNT, it.id) }
            }

            is AdminBrowseContent.Campsites -> {
                content.rows.map { ImageTarget(ImageTargetType.CAMPSITE, it.id) }
            }

            is AdminBrowseContent.Images -> {
                content.rows.map { it.target }
            }

            else -> {
                emptyList()
            }
        }.associateBy { "${it.type}:${it.id}" }
    LaunchedEffect(model, state.tab, targets, campsiteList, profileList, grid) {
        snapshotFlow {
            val keys =
                when (state.tab) {
                    AdminBrowseTab.CAMPSITES -> {
                        campsiteList.layoutInfo.visibleItemsInfo.map {
                            it.key
                        }
                    }

                    AdminBrowseTab.PROFILES -> {
                        profileList.layoutInfo.visibleItemsInfo.map {
                            it.key
                        }
                    }

                    AdminBrowseTab.IMAGES -> {
                        grid.layoutInfo.visibleItemsInfo.map { it.key }
                    }
                }
            keys.mapNotNull { targets[it] }.toSet()
        }.distinctUntilChanged().collect(model::setVisibleTargets)
    }
    DisposableEffect(model) { onDispose { model.setVisibleTargets(emptySet()) } }
    AdminBrowseScreen(
        state,
        photos,
        campsiteList,
        profileList,
        grid,
        onSelectTab = model::selectTab,
        onQuery = model::editQuery,
        onRole = model::selectRole,
        onRetry = model::retry,
        onRetryThumbnail = model::retryThumbnail,
        onProfile = onProfile,
        onImage = onImage,
    )
}

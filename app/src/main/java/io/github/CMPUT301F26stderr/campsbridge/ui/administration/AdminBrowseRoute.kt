package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.*
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** Binds graph-owned inputs and only the currently visible thumbnail readers. */
@Composable
fun AdminBrowseRoute(model: AdminBrowseViewModel, onProfile: (String) -> Unit, onImage: (ImageTarget) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val photos by model.inspection.photos.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val grid = rememberLazyGridState()
    val targets = when (state.tab) {
        AdminBrowseTab.PROFILES -> (state.profiles as? AdminRead.Content)?.value.orEmpty().map { ImageTarget(ImageTargetType.ACCOUNT, it.id) }
        AdminBrowseTab.CAMPSITES -> (state.campsites as? AdminRead.Content)?.value.orEmpty().map { ImageTarget(ImageTargetType.CAMPSITE, it.id) }
        AdminBrowseTab.IMAGES -> (state.images as? AdminRead.Content)?.value.orEmpty().map { it.target }
    }.associateBy { "${it.type}:${it.id}" }
    LaunchedEffect(model, state.tab, targets, list, grid) {
        snapshotFlow {
            if (state.tab == AdminBrowseTab.IMAGES) grid.layoutInfo.visibleItemsInfo.mapNotNull { targets[it.key] }.toSet()
            else list.layoutInfo.visibleItemsInfo.mapNotNull { targets[it.key] }.toSet()
        }.distinctUntilChanged().collect(model::setVisibleTargets)
    }
    DisposableEffect(model) { onDispose { model.setVisibleTargets(emptySet()) } }
    Column(Modifier.fillMaxSize()) {
        TabRow(state.tab.ordinal) {
            AdminBrowseTab.entries.forEach { tab ->
                Tab(selected = state.tab == tab, onClick = { model.selectTab(tab) }, text = { Text(stringResource(tab.label())) })
            }
        }
        OutlinedTextField(state.query, model::setQuery, Modifier.fillMaxWidth().padding(16.dp),
            label = { Text(stringResource(R.string.admin_search)) }, singleLine = true)
        if (state.tab == AdminBrowseTab.PROFILES) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (listOf<Role?>(null) + Role.entries).forEach { role ->
                    FilterChip(selected = state.role == role, onClick = { model.setRole(role) },
                        label = { Text(role?.name ?: stringResource(R.string.admin_all_roles)) })
                }
            }
        }
        when (state.tab) {
            AdminBrowseTab.PROFILES -> BrowseSource(state.profiles, { model.retry() }) { rows ->
                LazyColumn(state = list) {
                    items(rows, key = { "ACCOUNT:${it.id}" }) { account ->
                        val target = ImageTarget(ImageTargetType.ACCOUNT, account.id)
                        BrowseRow(account.name, listOf(account.id, account.email.orEmpty(), account.phone.orEmpty(),
                            account.roles.joinToString(), if (account.isActive) "Active" else "Removed").filter { it.isNotBlank() }.joinToString(" · "),
                            photos[target] ?: ImageSlotState(), { model.inspection.retry(target) }, { onProfile(account.id) })
                    }
                }
            }
            AdminBrowseTab.CAMPSITES -> BrowseSource(state.campsites, { model.retry() }) { rows ->
                LazyColumn(state = list) {
                    items(rows, key = { "CAMPSITE:${it.id}" }) { campsite ->
                        val target = ImageTarget(ImageTargetType.CAMPSITE, campsite.id)
                        BrowseRow(campsite.details.name, "${campsite.id} · ${campsite.details.locationLabel} · ${campsite.ownerId}",
                            photos[target] ?: ImageSlotState(), { model.inspection.retry(target) }, { onImage(target) })
                    }
                }
            }
            AdminBrowseTab.IMAGES -> BrowseSource(state.images, { model.retry() }) { rows ->
                LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), state = grid) {
                    items(rows, key = { "${it.target.type}:${it.target.id}" }) { image ->
                        Column(Modifier.padding(8.dp).clickable { onImage(image.target) }) {
                            AttachmentImage(photos[image.target] ?: ImageSlotState(), image.target.id,
                                { model.inspection.retry(image.target) }, Modifier.fillMaxWidth().height(120.dp))
                            Text("${image.target.type} · ${image.target.id}")
                            Text(image.blobId)
                            Text(image.uploadedAt.toString())
                        }
                    }
                }
            }
        }
    }
}

private fun AdminBrowseTab.label(): Int = when (this) {
    AdminBrowseTab.PROFILES -> R.string.admin_profiles
    AdminBrowseTab.CAMPSITES -> R.string.admin_campsites
    AdminBrowseTab.IMAGES -> R.string.admin_images
}

@Composable
private fun <T> BrowseSource(source: AdminRead<List<T>>, retry: () -> Unit, content: @Composable (List<T>) -> Unit) {
    when (source) {
        AdminRead.Loading -> CircularProgressIndicator()
        AdminRead.Failure -> FailureContent(stringResource(R.string.admin_source_failed), retry)
        AdminRead.Unavailable -> EmptyContent(stringResource(R.string.admin_no_results))
        is AdminRead.Content -> if (source.value.isEmpty()) EmptyContent(stringResource(R.string.admin_no_results)) else content(source.value)
    }
}

@Composable
private fun BrowseRow(title: String, metadata: String, photo: ImageSlotState, retry: () -> Unit, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = open).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(metadata)
        AttachmentImage(photo, title, retry, Modifier.size(96.dp))
        HorizontalDivider()
    }
}

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
    val photos by model.photos.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val grid = rememberLazyGridState()
    val targets = when (val content = state.content) {
        is AdminBrowseContent.Profiles -> content.rows.map { ImageTarget(ImageTargetType.ACCOUNT, it.id) }
        is AdminBrowseContent.Campsites -> content.rows.map { ImageTarget(ImageTargetType.CAMPSITE, it.id) }
        is AdminBrowseContent.Images -> content.rows.map { it.target }
        else -> emptyList()
    }.associateBy { "${it.type}:${it.id}" }
    LaunchedEffect(model, state.tab, targets, list, grid) {
        snapshotFlow {
            if (state.tab == AdminBrowseTab.IMAGES) grid.layoutInfo.visibleItemsInfo.mapNotNull { targets[it.key] }.toSet()
            else list.layoutInfo.visibleItemsInfo.mapNotNull { targets[it.key] }.toSet()
        }.distinctUntilChanged().collect(model::setVisibleTargets)
    }
    DisposableEffect(model) { onDispose { model.setVisibleTargets(emptySet()) } }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(state.tab.ordinal) {
            AdminBrowseTab.entries.forEach { tab ->
                Tab(selected = state.tab == tab, onClick = { model.selectTab(tab) }, text = { Text(stringResource(tab.label())) })
            }
        }
        OutlinedTextField(state.query, model::editQuery, Modifier.fillMaxWidth().padding(16.dp),
            label = { Text(stringResource(R.string.admin_search)) }, singleLine = true)
        if (state.tab == AdminBrowseTab.PROFILES) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (listOf<Role?>(null) + Role.entries).forEach { role ->
                    FilterChip(selected = state.role == role, onClick = { model.selectRole(role) },
                        label = { Text(role?.name ?: stringResource(R.string.admin_all_roles)) })
                }
            }
        }
        when (val content = state.content) {
            AdminBrowseContent.Loading -> CircularProgressIndicator()
            AdminBrowseContent.Failure -> FailureContent(stringResource(R.string.admin_source_failed), model::retry)
            AdminBrowseContent.Pending -> EmptyContent(stringResource(R.string.feature_pending))
            is AdminBrowseContent.Profiles -> BrowseRows(content.rows) { rows ->
                LazyColumn(state = list) {
                    items(rows, key = { "ACCOUNT:${it.id}" }) { account ->
                        val target = ImageTarget(ImageTargetType.ACCOUNT, account.id)
                        BrowseRow(account.name, listOf(account.id, account.email.orEmpty(), account.phone.orEmpty(),
                            account.roles.joinToString(), if (account.isActive) "Active" else "Removed").filter { it.isNotBlank() }.joinToString(" · "),
                            photos[target] ?: ImageSlotState(), { model.retryThumbnail(target) }, { onProfile(account.id) })
                    }
                }
            }
            is AdminBrowseContent.Campsites -> BrowseRows(content.rows) { rows ->
                LazyColumn(state = list) {
                    items(rows, key = { "CAMPSITE:${it.id}" }) { campsite ->
                        val target = ImageTarget(ImageTargetType.CAMPSITE, campsite.id)
                        BrowseRow(campsite.details.name, "${campsite.id} · ${campsite.details.locationLabel} · ${campsite.ownerId}",
                            photos[target] ?: ImageSlotState(), { model.retryThumbnail(target) }, { onImage(target) })
                    }
                }

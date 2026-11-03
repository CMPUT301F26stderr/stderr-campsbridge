package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.roleLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

private const val METADATA_SEPARATOR = " · "

/** Admin browse tabs, search and rows; the route owns the ViewModel and navigation.
 * @param state Selected tab, its inputs and the source outcome.
 * @param photos Thumbnail states for the rows currently on screen.
 * @param campsiteListState Scroll position of the campsite list.
 * @param profileListState Scroll position of the profile list.
 * @param gridState Scroll position of the image gallery.
 * @param onSelectTab Switches to another record type.
 * @param onQuery Edits the selected tab's search text.
 * @param onRole Filters profiles by role; null shows every role.
 * @param onRetry Retries the failed source.
 * @param onRetryThumbnail Retries one failed thumbnail.
 * @param onProfile Opens a profile by account ID.
 * @param onImage Opens an image slot.
 * @param modifier Caller layout.
 */
@Composable
fun AdminBrowseScreen(
    state: AdminBrowseUiState,
    photos: Map<ImageTarget, ImageSlotState>,
    campsiteListState: LazyListState,
    profileListState: LazyListState,
    gridState: LazyGridState,
    onSelectTab: (AdminBrowseTab) -> Unit,
    onQuery: (String) -> Unit,
    onRole: (Role?) -> Unit,
    onRetry: () -> Unit,
    onRetryThumbnail: (ImageTarget) -> Unit,
    onProfile: (String) -> Unit,
    onImage: (ImageTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SecondaryTabRow(state.tab.ordinal) {
            AdminBrowseTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(stringResource(tab.label())) },
                )
            }
        }
        OutlinedTextField(
            state.query,
            onQuery,
            Modifier.fillMaxWidth().padding(Spacing.medium),
            label = { Text(stringResource(R.string.admin_search)) },
            singleLine = true,
        )
        if (state.tab == AdminBrowseTab.PROFILES) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                (listOf<Role?>(null) + Role.entries).forEach { role ->
                    FilterChip(
                        selected = state.role == role,
                        onClick = { onRole(role) },
                        label = {
                            Text(
                                role?.let {
                                    roleLabel(
                                        it,
                                    )
                                } ?: stringResource(R.string.admin_all_roles),
                            )
                        },
                    )
                }
            }
        }
        when (val content = state.content) {
            AdminBrowseContent.Loading -> {
                BrowseFeedback { LoadingContent() }
            }

            AdminBrowseContent.Failure -> {
                BrowseFeedback {
                    FailureContent(
                        stringResource(R.string.admin_source_failed),
                        onRetry,
                    )
                }
            }

            AdminBrowseContent.Pending -> {
                BrowseFeedback { EmptyContent(stringResource(R.string.feature_pending)) }
            }

            is AdminBrowseContent.Profiles -> {
                BrowseRows(content.rows) { rows ->
                    LazyColumn(state = profileListState) {
                        items(rows, key = { "ACCOUNT:${it.id}" }) { account ->
                            val target = ImageTarget(ImageTargetType.ACCOUNT, account.id)
                            BrowseRow(
                                account.name,
                                listOf(
                                    account.id,
                                    account.email.orEmpty(),
                                    account.phone.orEmpty(),
                                    account.roles.map { roleLabel(it) }.joinToString(),
                                    stringResource(
                                        if (account.isActive) {
                                            R.string.admin_account_active
                                        } else {
                                            R.string.admin_account_removed
                                        },
                                    ),
                                ).filter { it.isNotBlank() }.joinToString(METADATA_SEPARATOR),
                                photos[target] ?: ImageSlotState(),
                                { onRetryThumbnail(target) },
                                { onProfile(account.id) },
                            )
                        }
                    }
                }
            }

            is AdminBrowseContent.Campsites -> {
                BrowseRows(content.rows) { rows ->
                    LazyColumn(state = campsiteListState) {
                        items(rows, key = { "CAMPSITE:${it.id}" }) { campsite ->
                            val target = ImageTarget(ImageTargetType.CAMPSITE, campsite.id)
                            BrowseRow(
                                campsite.details.name,
                                listOf(
                                    campsite.id,
                                    campsite.details.locationLabel,
                                    campsite.ownerId,
                                ).joinToString(METADATA_SEPARATOR),
                                photos[target] ?: ImageSlotState(),
                                { onRetryThumbnail(target) },
                                { onImage(target) },
                            )
                        }
                    }
                }
            }

            is AdminBrowseContent.Images -> {
                BrowseRows(content.rows) { rows ->
                    AdminImageGallery(rows, photos, gridState, onRetryThumbnail, onImage)
                }
            }
        }
    }
}

/** Resolves a tab's visible label. */
private fun AdminBrowseTab.label(): Int =
    when (this) {
        AdminBrowseTab.PROFILES -> R.string.admin_profiles
        AdminBrowseTab.CAMPSITES -> R.string.admin_campsites
        AdminBrowseTab.IMAGES -> R.string.admin_images
    }

/** Shows the no-results message instead of an empty list.
 * @param rows Records left after filtering.
 * @param content Renders non-empty rows.
 */
@Composable
private fun <T> BrowseRows(
    rows: List<T>,
    content: @Composable (List<T>) -> Unit,
) {
    if (rows.isEmpty()) {
        BrowseFeedback { EmptyContent(stringResource(R.string.admin_no_results)) }
    } else {
        content(rows)
    }
}

/** Lines status messages up with the search field.
 * @param content Loading, failure or empty message.
 */
@Composable
private fun BrowseFeedback(content: @Composable () -> Unit) {
    Box(Modifier.padding(Spacing.medium)) { content() }
}

/** One record row; a failed thumbnail stays inside its slot and keeps the metadata visible.
 * @param title Record name.
 * @param metadata Identity and status line.
 * @param photo This record's thumbnail state.
 * @param retry Retries this thumbnail.
 * @param open Opens this record.
 */
@Composable
private fun BrowseRow(
    title: String,
    metadata: String,
    photo: ImageSlotState,
    retry: () -> Unit,
    open: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = open).padding(Spacing.medium)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(metadata)
        AttachmentImage(photo, title, retry, Modifier.size(96.dp))
        HorizontalDivider()
    }
}

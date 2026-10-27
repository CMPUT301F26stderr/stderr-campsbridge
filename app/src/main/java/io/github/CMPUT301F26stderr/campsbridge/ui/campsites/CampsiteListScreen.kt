package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Owner inventory content; the workspace frame belongs to navigation.
 * @param state Confirmed source outcome.
 * @param tab Selected inventory category.
 * @param onSelectTab Explicit category selection.
 * @param onOpenCampsite Opens the saved identity, not a copied record.
 * @param onRetry Retries the source.
 * @param listState Saveable scroll context supplied by the route.
 * @param modifier Caller layout.
 * @param onCreate Starts a real registered setup flow when supplied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteListScreen(
    state: CampsiteListUiState,
    tab: CampsiteListTab,
    onSelectTab: (CampsiteListTab) -> Unit,
    onOpenCampsite: (String) -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            for (item in CampsiteListTab.entries) {
                Tab(
                    selected = item == tab,
                    onClick = { onSelectTab(item) },
                    text = {
                        Text(
                            stringResource(
                                if (item == CampsiteListTab.PUBLISHED) {
                                    R.string.campsite_published
                                } else {
                                    R.string.campsite_drafts
                                },
                            ),
                        )
                    },
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state) {
                CampsiteListUiState.Loading -> {
                    item { LoadingContent() }
                }

                CampsiteListUiState.Failure -> {
                    item { FailureContent(stringResource(R.string.campsite_list_failure), onRetry) }
                }

                is CampsiteListUiState.Content -> {
                    if (state.orderingPending) {
                        item {
                            Text(
                                stringResource(R.string.campsite_ordering_pending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.campsites.isEmpty()) {
                        item {
                            EmptyContent(
                                stringResource(
                                    if (tab == CampsiteListTab.PUBLISHED) {
                                        R.string.campsite_list_empty_published
                                    } else {
                                        R.string.campsite_list_empty_drafts
                                    },
                                ),
                            )
                        }
                    } else {
                        items(state.campsites, key = { it.id }) { site ->
                            CampsiteRow(
                                site,
                                state.photos[site.id]?.displayUrl,
                                { onOpenCampsite(site.id) },
                                status =
                                    stringResource(
                                        if (site.isPublished) {
                                            R.string.campsite_published
                                        } else {
                                            R.string.campsite_draft
                                        },
                                    ),
                                showPrice = true,
                            )
                        }
                    }
                }
            }
        }
        onCreate?.let { action ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
                horizontalArrangement = Arrangement.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = action,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text(stringResource(R.string.campsite_create))
                }
            }
        }
    }
}

/** Shows real content composition without navigation, SDK access or fake saves. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Preview(name = "Large text", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun CampsiteListPreview() {
    CampsiteListPreviewFrame {
        CampsiteListScreen(
            CampsiteListUiState.Content(
                listOf(previewCampsite()),
                true,
                mapOf(previewCampsite().id to previewCampsitePhoto()),
            ),
            CampsiteListTab.PUBLISHED,
            {},
            {},
            {},
            onCreate = {},
        )
    }
}

/** Verifies failed source feedback keeps the same tabs. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteListFailurePreview() {
    CampsiteListPreviewFrame {
        CampsiteListScreen(
            CampsiteListUiState.Failure,
            CampsiteListTab.DRAFTS,
            {},
            {},
            {},
            onCreate = {},
        )
    }
}

/** Verifies an empty inventory remains a successful read. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteListEmptyPreview() {
    CampsiteListPreviewFrame {
        CampsiteListScreen(
            CampsiteListUiState.Content(emptyList(), true),
            CampsiteListTab.PUBLISHED,
            {},
            {},
            {},
            onCreate = {},
        )
    }
}

/** Frames passive inventory fixtures without adding a second frame to production routes.
 * @param content Inventory state supplied without repositories or navigation.
 */
@Composable
private fun CampsiteListPreviewFrame(content: @Composable () -> Unit) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.campsites)) { content() }
    }
}

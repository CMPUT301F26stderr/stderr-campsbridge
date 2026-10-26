package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

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
        onCreate?.let { action ->
            TextButton(action) { Text(stringResource(R.string.campsite_create)) }
        }
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
        when (state) {
            CampsiteListUiState.Loading -> {
                LoadingContent()
            }

            CampsiteListUiState.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_list_failure),
                    onRetry,
                )
            }

            is CampsiteListUiState.Content -> {
                if (state.orderingPending) {
                    Text(stringResource(R.string.campsite_ordering_pending))
                }
                if (state.campsites.isEmpty()) {
                    EmptyContent(
                        stringResource(
                            if (tab == CampsiteListTab.PUBLISHED) {
                                R.string.campsite_list_empty_published
                            } else {
                                R.string.campsite_list_empty_drafts
                            },
                        ),
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
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
    }
}

/** Shows real content composition without navigation, SDK access or fake saves. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Preview(name = "Large text", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun CampsiteListPreview() {
    CampsbridgeTheme {
        CampsiteListScreen(
            CampsiteListUiState.Content(listOf(previewCampsite()), true),
            CampsiteListTab.PUBLISHED,
            {},
            {},
            {},
        )
    }
}

/** Verifies failed source feedback keeps the same tabs. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteListFailurePreview() {
    CampsbridgeTheme {
        CampsiteListScreen(CampsiteListUiState.Failure, CampsiteListTab.DRAFTS, {}, {}, {})
    }
}

/** Verifies an empty inventory remains a successful read. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteListEmptyPreview() {
    CampsbridgeTheme {
        CampsiteListScreen(
            CampsiteListUiState.Content(emptyList(), true),
            CampsiteListTab.PUBLISHED,
            {},
            {},
            {},
        )
    }
}

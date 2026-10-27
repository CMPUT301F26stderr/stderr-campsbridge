package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteCard
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.ratingLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsitePhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDate
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.components.DiscoveryToolbar
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Primary discovery content; navigation owns its workspace frame.
 * @param state Editable criteria and independent result outcome.
 * @param onQueryChange Edits raw toolbar input.
 * @param onSearch Explicitly applies the toolbar query.
 * @param onOpenFilters Opens the shared owner's working filter child.
 * @param onOpenCampsite Opens an actual saved listing ID.
 * @param onRetry Retries required source reads.
 * @param onRetryRow Retries optional reads for one visible ID.
 * @param listState Caller-owned saveable scroll state.
 * @param modifier Caller layout.
 */
@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCampsite: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryRow: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            DiscoveryToolbar(
                state.queryText,
                onQueryChange,
                false,
                {},
                onOpenFilters,
                onSearch = onSearch,
                showViewToggle = false,
            )
            AppliedFilterSummary(state.filters)
        }
        when (val result = state.result) {
            ExploreResult.Loading -> {
                LoadingContent()
            }

            ExploreResult.Failure -> {
                FailureContent(stringResource(R.string.discovery_failure), onRetry)
            }

            ExploreResult.HelperPending -> {
                Text(
                    stringResource(R.string.discovery_helpers_pending),
                    Modifier.padding(vertical = Spacing.medium),
                )
            }

            is ExploreResult.Content -> {
                if (result.orderingPending) {
                    Text(
                        stringResource(R.string.campsite_ordering_pending),
                        Modifier.padding(vertical = Spacing.small),
                    )
                }
                if (result.campsites.isEmpty()) {
                    EmptyContent(stringResource(R.string.discovery_empty))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                    ) {
                        items(result.campsites, key = { it.id }) { site ->
                            val photo = result.photos[site.id]
                            val rating = result.ratings[site.id] ?: RatingState.Loading
                            val photoPending =
                                photo == null || photo.loadState == ImageLoadState.LOADING ||
                                    photo.isResolvingUrl
                            CampsiteCard(
                                site,
                                null,
                                { onOpenCampsite(site.id) },
                                ratingText = ratingLabel(rating),
                                photoContent = {
                                    when {
                                        photoPending -> {
                                            LoadingContent()
                                        }

                                        photo?.loadState == ImageLoadState.FAILED ||
                                            photo?.displayFailed == true -> {
                                            Text(
                                                stringResource(R.string.photo_display_failure),
                                            )
                                        }

                                        else -> {
                                            RemoteImage(
                                                photo?.displayUrl,
                                                site.details.name,
                                                Modifier.fillMaxSize(),
                                                compactFailure = true,
                                            )
                                        }
                                    }
                                },
                            )
                            if (photo?.loadState == ImageLoadState.FAILED ||
                                photo?.displayFailed == true ||
                                rating == RatingState.Failure
                            ) {
                                TextButton({ onRetryRow(site.id) }) {
                                    Text(
                                        stringResource(
                                            R.string.discovery_retry_row,
                                            site.details.name,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Presents applied facts only; an open working draft cannot change this summary.
 * @param filters Validated applied criteria.
 */
@Composable
private fun AppliedFilterSummary(filters: DiscoveryFilters) {
    val locale = LocalConfiguration.current.locales[0]
    if (filters.locationText.isNotEmpty()) {
        Text(
            stringResource(R.string.discovery_location_summary, filters.locationText),
        )
    }
    if (filters.checkIn != null && filters.checkOut != null) {
        Text(
            stringResource(
                R.string.stay_dates,
                formatDate(filters.checkIn, locale),
                formatDate(filters.checkOut, locale),
            ),
        )
    }
    filters.requestedGuests?.let { Text(stringResource(R.string.discovery_guests_summary, it)) }
}

/** Frames isolated discovery states with the same app-bar/inset owner as the actual route.
 * @param state Passive presentation state; no controller or persistence calls are created.
 */
@Composable
private fun ExplorePreviewContent(state: ExploreUiState) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.discovery_title)) {
            ExploreScreen(state, {}, {}, {}, {}, {}, {})
        }
    }
}

/** Shows a realistic loaded listing using supplied content and a credited local image. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ExplorePreview() {
    val site = previewCampsite()
    ExplorePreviewContent(
        ExploreUiState(
            result =
                ExploreResult.Content(
                    listOf(site),
                    false,
                    photos = mapOf(site.id to previewCampsitePhoto()),
                    ratings = mapOf(site.id to RatingState.Ready(RatingSummary(3, 4.7))),
                ),
        ),
    )
}

/** Shows unresolved optional image/rating reads while confirmed listings stay usable. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ExplorePendingPhotosPreview() {
    ExplorePreviewContent(
        ExploreUiState(result = ExploreResult.Content(listOf(previewCampsite()), false)),
    )
}

/** Shows confirmed empty results without hiding editable search. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ExploreEmptyPreview() {
    ExplorePreviewContent(ExploreUiState(result = ExploreResult.Content(emptyList(), false)))
}

/** Shows a required-source failure while preserving the toolbar input. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ExploreFailurePreview() {
    ExplorePreviewContent(ExploreUiState(queryText = "River", result = ExploreResult.Failure))
}

/** Shows pending required reads independently from optional row sources. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ExploreLoadingPreview() {
    ExplorePreviewContent(ExploreUiState())
}

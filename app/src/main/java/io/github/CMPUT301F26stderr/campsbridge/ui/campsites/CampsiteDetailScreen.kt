package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.ratingLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Saved overview shared by entrants, owner previews and admin inspection.
 * @param state Actual listing outcome, never copied from a discovery row.
 * @param photo Independent attachment/display state.
 * @param onBack Returns to the actual caller.
 * @param onRetry Retries the parent listing read.
 * @param onRetryPhoto Retries only attachment/display reads.
 * @param modifier Caller layout.
 * @param rating Independent real rating result.
 * @param context Fixed caller presentation; only VIEW renders entrant participation.
 * @param onRetryRatings Retries only the failed rating read.
 * @param onOpenSection Opens a child within the same saved reader graph.
 * @param lotteryContent Optional actual entrant entry owned by the saved route.
 */
@Composable
fun CampsiteDetailScreen(
    state: CampsiteDetailUiState,
    photo: ImageSlotState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    rating: RatingState = RatingState.Loading,
    context: CampsiteDetailContext = CampsiteDetailContext.VIEW,
    onRetryRatings: () -> Unit = {},
    onOpenSection: (CampsiteDetailSection) -> Unit = {},
    lotteryContent: (@Composable () -> Unit)? = null,
) {
    AppScreen(stringResource(R.string.campsite), modifier, onBack = onBack) {
        when (state) {
            CampsiteDetailUiState.Loading -> {
                LoadingContent()
            }

            CampsiteDetailUiState.Failure -> {
                FailureContent(stringResource(R.string.campsite_detail_failure), onRetry)
            }

            CampsiteDetailUiState.Unavailable -> {
                Text(stringResource(R.string.campsite_unavailable))
            }

            is CampsiteDetailUiState.Content -> {
                val site = state.campsite
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    if (photo.loadState == ImageLoadState.LOADING || photo.isResolvingUrl) {
                        LoadingContent()
                    } else {
                        RemoteImage(
                            photo.displayUrl,
                            site.details.name,
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                    if (photo.loadState == ImageLoadState.FAILED || photo.displayFailed) {
                        FailureContent(stringResource(R.string.photo_display_failure), onRetryPhoto)
                    }
                    Text(site.details.name, style = MaterialTheme.typography.headlineSmall)
                    if (context == CampsiteDetailContext.PREVIEW) {
                        Text(stringResource(R.string.campsite_preview))
                    } else if (context == CampsiteDetailContext.INSPECT) {
                        Text(stringResource(R.string.campsite_inspection))
                        Text(
                            stringResource(
                                if (site.removal != null) {
                                    R.string.campsite_removed
                                } else if (site.isPublished) {
                                    R.string.campsite_published
                                } else {
                                    R.string.campsite_draft
                                },
                            ),
                        )
                    }
                    Text(site.details.locationLabel)
                    Text(ratingLabel(rating))
                    if (rating == RatingState.Failure) {
                        TextButton(onRetryRatings) {
                            Text(stringResource(R.string.campsite_retry_ratings))
                        }
                    }
                    Text(stringResource(R.string.up_to_guests, site.details.maxGuests))
                    Text(
                        stringResource(
                            R.string.per_night,
                            formatCad(
                                site.details.nightlyPriceCents,
                                LocalConfiguration.current.locales[0],
                            ),
                        ),
                    )
                    DetailRow(stringResource(R.string.campsite_reference), site.id)
                    if (context == CampsiteDetailContext.VIEW) lotteryContent?.invoke()
                    for (section in listOf(
                        CampsiteDetailSection.ABOUT,
                        CampsiteDetailSection.MAP,
                        CampsiteDetailSection.AVAILABILITY,
                        CampsiteDetailSection.RULES,
                    )) {
                        NavigationRow(campsiteSectionTitle(section), { onOpenSection(section) })
                    }
                }
            }
        }
    }
}

/** Shows the first saved public overview without unsupported lottery controls. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteDetailPreview() {
    CampsbridgeTheme {
        CampsiteDetailScreen(
            CampsiteDetailUiState.Content(
                previewCampsite(),
            ),
            ImageSlotState(loadState = ImageLoadState.READY),
            {
            },
            {},
            {},
            rating = RatingState.Ready(RatingSummary(0, null)),
        )
    }
}

/** Shows an unresolved public listing without substituting fixture facts. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteDetailLoadingPreview() {
    CampsbridgeTheme {
        CampsiteDetailScreen(CampsiteDetailUiState.Loading, ImageSlotState(), {}, {}, {})
    }
}

/** Shows read retry independently of any prior discovery card. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteDetailFailurePreview() {
    CampsbridgeTheme {
        CampsiteDetailScreen(CampsiteDetailUiState.Failure, ImageSlotState(), {}, {}, {})
    }
}

/** Shows a removed or inaccessible saved identity with an actual-caller Back action. */
@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CampsiteDetailUnavailablePreview() {
    CampsbridgeTheme {
        CampsiteDetailScreen(CampsiteDetailUiState.Unavailable, ImageSlotState(), {}, {}, {})
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Read-only saved overview; section readers are added with their actual M2b consumers.
 * @param state Actual listing outcome, never copied from a discovery row.
 * @param photo Independent attachment/display state.
 * @param onBack Returns to the actual caller.
 * @param onRetry Retries the parent listing read.
 * @param onRetryPhoto Retries only attachment/display reads.
 * @param modifier Caller layout.
 */
@Composable
fun CampsiteDetailScreen(
    state: CampsiteDetailUiState,
    photo: ImageSlotState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPhoto: () -> Unit,
    modifier: Modifier = Modifier,
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
                    Text(site.details.locationLabel)
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
                    Text(site.details.description)
                    DetailRow(stringResource(R.string.campsite_reference), site.id)
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

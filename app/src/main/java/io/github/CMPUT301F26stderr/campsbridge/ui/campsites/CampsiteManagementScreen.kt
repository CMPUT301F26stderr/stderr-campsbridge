package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatCad
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.StatusLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.DetailRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/** Saved owner summary; future actions appear only when their actual routes are registered.
 * @param state Actual saved listing outcome.
 * @param photo Transient attachment/display outcome, independent of listing failure.
 * @param onBack Returns to the caller.
 * @param onRetry Retries the listing read.
 * @param onRetryPhoto Retries an attachment/display reference read.
 * @param modifier Caller layout.
 * @param onAvailability Opens the registered independent offer editor.
 * @param onResume Opens existing draft setup; never used for a published listing.
 * @param onPreview Opens the separate read-only owner preview graph.
 */
@Composable
fun CampsiteManagementScreen(
    state: CampsiteDetailUiState,
    photo: ImageSlotState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    onResume: (() -> Unit)? = null,
    onAvailability: (() -> Unit)? = null,
    onPreview: (() -> Unit)? = null,
) {
    AppScreen(stringResource(R.string.manage_campsite), modifier, onBack = onBack) {
        when (state) {
            CampsiteDetailUiState.Loading -> {
                LoadingContent()
            }

            CampsiteDetailUiState.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_detail_failure),
                    onRetry,
                )
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
                    StatusLabel(
                        stringResource(
                            if (site.isPublished) {
                                R.string.campsite_published
                            } else {
                                R.string.campsite_draft
                            },
                        ),
                        if (site.isPublished) Icons.Default.CheckCircle else Icons.Default.Edit,
                    )
                    DetailRow(
                        stringResource(R.string.campsite_location),
                        site.details.locationLabel,
                    )
                    Text(stringResource(R.string.up_to_guests, site.details.maxGuests))
                    DetailRow(
                        stringResource(R.string.campsite_nightly_price),
                        stringResource(
                            R.string.per_night,
                            formatCad(
                                site.details.nightlyPriceCents,
                                LocalConfiguration.current.locales[0],
                            ),
                        ),
                    )
                    DetailRow(stringResource(R.string.campsite_reference), site.id)
                    if (onPreview != null) {
                        TextButton(onPreview) { Text(stringResource(R.string.campsite_preview)) }
                    }
                    if (onAvailability != null) {
                        TextButton(
                            onAvailability,
                        ) { Text(stringResource(R.string.availability_title)) }
                    }
                    if (!site.isPublished && onResume != null) {
                        TextButton(onResume) { Text(stringResource(R.string.campsite_resume)) }
                    }
                }
            }
        }
    }
}

/** Stable ordinary domain values used only by themed content previews. */
internal fun previewCampsite(): Campsite =
    Campsite(
        "preview-campsite",
        "preview-owner",
        CampsiteDetails(
            "River Camp",
            "A quiet riverside campsite.",
            Coordinates(53.0, -118.0),
            "Jasper, Alberta",
            "America/Edmonton",
            8,
            setOf("water"),
            setOf("lakeside"),
            6500,
            "Keep the campsite clean.",
        ),
        Instant.EPOCH,
        Instant.EPOCH,
    )

/** Shows the read-only summary with the same theme as owner inventory. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteManagementPreview() {
    CampsbridgeTheme {
        CampsiteManagementScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            ImageSlotState(loadState = ImageLoadState.READY),
            {},
            {},
            {},
        )
    }
}

/** Shows recoverable failure without replacing the detail frame or Back action. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CampsiteManagementFailurePreview() {
    CampsbridgeTheme {
        CampsiteManagementScreen(CampsiteDetailUiState.Failure, ImageSlotState(), {}, {}, {})
    }
}

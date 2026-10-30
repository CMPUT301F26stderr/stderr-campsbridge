package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.components.CampsiteFacts
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.StatusLabel
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

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
 * @param onCreateLottery Opens the registered local lottery setup from a published listing.
 * @param onViewLotteries Opens the registered allocation-filtered owner list.
 * @param onViewReservations Opens the registered saved-site reservation list.
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
    onCreateLottery: (() -> Unit)? = null,
    onViewLotteries: (() -> Unit)? = null,
    onViewReservations: (() -> Unit)? = null,
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
                    OutlinedCard(
                        colors =
                            CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(Spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (photo.loadState == ImageLoadState.LOADING ||
                                        photo.isResolvingUrl
                                    ) {
                                        CircularProgressIndicator()
                                    } else {
                                        RemoteImage(
                                            photo.displayUrl,
                                            site.details.name,
                                            Modifier.fillMaxSize(),
                                            compactFailure = true,
                                        )
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                                ) {
                                    Text(
                                        site.details.name,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        site.details.locationLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            StatusLabel(
                                stringResource(
                                    if (site.isPublished) {
                                        R.string.campsite_published
                                    } else {
                                        R.string.campsite_draft
                                    },
                                ),
                                if (site.isPublished) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Edit
                                },
                            )
                            CampsiteFacts(site)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                Text(
                                    stringResource(R.string.campsite_reference),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    site.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (photo.loadState == ImageLoadState.FAILED || photo.displayFailed) {
                        FailureContent(stringResource(R.string.photo_display_failure), onRetryPhoto)
                    }
                    Column {
                        if (!site.isPublished && onResume != null) {
                            NavigationRow(
                                stringResource(R.string.campsite_resume),
                                onResume,
                                icon = Icons.Default.Edit,
                            )
                        }
                        if (onAvailability != null) {
                            NavigationRow(
                                stringResource(R.string.availability_title),
                                onAvailability,
                                icon = Icons.Default.DateRange,
                            )
                        }
                        if (onViewLotteries != null) {
                            NavigationRow(
                                stringResource(R.string.campsite_lotteries),
                                onViewLotteries,
                                icon = Icons.Default.List,
                            )
                        }
                        if (site.isPublished && onCreateLottery != null) {
                            NavigationRow(
                                stringResource(R.string.lottery_create),
                                onCreateLottery,
                                icon = Icons.Default.Add,
                            )
                        }
                        if (onViewReservations != null) {
                            NavigationRow(
                                stringResource(R.string.reservation_view_list),
                                onViewReservations,
                                icon = Icons.Default.CheckCircle,
                            )
                        }
                        if (onPreview != null) {
                            NavigationRow(
                                stringResource(R.string.campsite_preview),
                                onPreview,
                                icon = Icons.Default.Info,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shows the read-only summary with the same theme as owner inventory. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteManagementPreview() {
    CampsbridgeTheme {
        CampsiteManagementScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            previewCampsitePhoto(),
            {},
            {},
            {},
            onAvailability = {},
            onPreview = {},
            onCreateLottery = {},
            onViewLotteries = {},
            onViewReservations = {},
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

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays the actual published campsite's code without exporting it or changing any saved data.
 * @param state Existing management reader's saved listing outcome.
 * @param image Transient QR generation outcome for that saved identity.
 * @param onBack Returns to the retained campsite manager through the shared Back input.
 * @param onRetry Retries only the failed source or native generation.
 */
@Composable
fun CampsiteQrScreen(
    state: CampsiteDetailUiState,
    image: CampsiteQrImage,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    AppScreen(stringResource(R.string.campsite_qr_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
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
                    if (!state.campsite.isPublished) {
                        Text(stringResource(R.string.campsite_unavailable))
                    } else {
                        val name = state.campsite.details.name
                        Text(name, style = MaterialTheme.typography.headlineSmall)
                        when (image) {
                            CampsiteQrImage.Loading -> {
                                LoadingContent()
                            }

                            CampsiteQrImage.Failure -> {
                                FailureContent(stringResource(R.string.campsite_qr_failed), onRetry)
                            }

                            is CampsiteQrImage.Ready -> {
                                Surface(
                                    modifier = Modifier.sizeIn(maxWidth = 320.dp).fillMaxWidth().aspectRatio(1f),
                                    color = Color.White,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Image(
                                        image.bitmap,
                                        stringResource(R.string.campsite_qr_description, name),
                                        Modifier.padding(Spacing.medium),
                                        contentScale = ContentScale.Fit,
                                        filterQuality = FilterQuality.None,
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.campsite_qr_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

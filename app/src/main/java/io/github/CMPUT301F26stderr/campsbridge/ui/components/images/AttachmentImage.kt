package io.github.CMPUT301F26stderr.campsbridge.ui.components.images

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent

/** Read-only saved image with stable geometry and independent attachment/URL retry.
 * @param state Parent-owned attachment and transient display outcome.
 * @param description Saved image identity for content semantics.
 * @param onRetry Retries only the parent's failed attachment or URL source.
 * @param imageModifier Image geometry; failure feedback remains outside its clipping bounds.
 */
@Composable
fun AttachmentImage(
    state: ImageSlotState,
    description: String,
    onRetry: () -> Unit,
    imageModifier: Modifier = Modifier,
) {
    Column {
        if (state.loadState == ImageLoadState.LOADING || state.isResolvingUrl) {
            Surface(modifier = imageModifier, color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            RemoteImage(state.displayUrl, description, imageModifier)
        }
        if (state.loadState == ImageLoadState.FAILED || state.displayFailed) {
            FailureContent(
                stringResource(
                    if (state.loadState == ImageLoadState.FAILED) {
                        R.string.photo_load_failure
                    } else {
                        R.string.photo_display_failure
                    },
                ),
                onRetry,
            )
        }
    }
}

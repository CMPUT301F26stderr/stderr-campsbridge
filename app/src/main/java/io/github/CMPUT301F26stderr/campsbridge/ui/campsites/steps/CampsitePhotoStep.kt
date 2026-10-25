package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.steps

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsitePhotoIntent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage

/** Stages an optional photo; no upload action exists before the explicit review save.
 * @param intent Unsaved keep/replace/remove choice.
 * @param saved Current saved attachment display.
 * @param reselect Whether restored transient content must be selected again.
 * @param selectionFailed Whether picker content was unreadable.
 * @param onChoose Native picker action.
 * @param onDiscard Retain the saved attachment.
 * @param onRemove Stage removal.
 * @param onRetry Retry saved-image display.
 */
@Composable
fun CampsitePhotoStep(
    intent: CampsitePhotoIntent,
    saved: ImageSlotState,
    reselect: Boolean,
    selectionFailed: Boolean,
    onChoose: () -> Unit,
    onDiscard: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val reference =
        when (intent) {
            CampsitePhotoIntent.Keep -> saved.displayUrl
            CampsitePhotoIntent.Remove -> null
            is CampsitePhotoIntent.Replace -> intent.photo.previewReference
        }
    if (intent == CampsitePhotoIntent.Keep &&
        (saved.loadState == ImageLoadState.LOADING || saved.isResolvingUrl)
    ) {
        LoadingContent()
    } else {
        RemoteImage(
            reference,
            stringResource(R.string.campsite_setup_photo),
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
    }
    Text(stringResource(R.string.campsite_photo_optional))
    if (intent is CampsitePhotoIntent.Replace) {
        Text(
            stringResource(R.string.campsite_photo_selected),
        )
    }
    if (intent == CampsitePhotoIntent.Remove) Text(stringResource(R.string.campsite_photo_removed))
    if (reselect) Text(stringResource(R.string.campsite_photo_reselect))
    if (selectionFailed) Text(stringResource(R.string.campsite_photo_selection_failure))
    if (saved.loadState == ImageLoadState.FAILED || saved.displayFailed) {
        FailureContent(stringResource(R.string.photo_display_failure), onRetry)
    }
    TextButton(onChoose) { Text(stringResource(R.string.campsite_choose_photo)) }
    if (intent != CampsitePhotoIntent.Keep || reselect) {
        TextButton(onDiscard) { Text(stringResource(R.string.campsite_discard_photo)) }
    }
    if (saved.attachment != null && intent != CampsitePhotoIntent.Remove) {
        TextButton(onRemove) { Text(stringResource(R.string.campsite_remove_photo)) }
    }
}

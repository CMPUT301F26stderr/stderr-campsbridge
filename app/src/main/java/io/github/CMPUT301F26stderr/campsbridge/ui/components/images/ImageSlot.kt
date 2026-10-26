package io.github.CMPUT301F26stderr.campsbridge.ui.components.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant

/**
 * Presents image facts, unsaved selection, explicit actions, and recoverable feedback.
 * @param state Controller state without SDK types.
 * @param onChoosePhoto Opens the native picker.
 * @param onSave Persists the selected candidate.
 * @param onDiscard Discards only unsaved input.
 * @param onRemove Removes the saved image after confirmation.
 * @param onRetry Retries the failed stage or reconciles an unconfirmed publication.
 * @param imageDescription Localized description of the saved image.
 * @param emptyIcon Parent-appropriate placeholder icon.
 * @param modifier Caller layout.
 * @param imageShape Shape shared with the parent feature's image convention.
 * @param imageModifier Optional parent geometry, such as a full-width campsite photo.
 */
@Composable
fun ImageSlot(
    state: ImageSlotState,
    onChoosePhoto: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    imageDescription: String,
    emptyIcon: ImageVector,
    modifier: Modifier = Modifier,
    imageShape: Shape = MaterialTheme.shapes.medium,
    imageModifier: Modifier? = null,
) {
    var confirmingRemoval by remember { mutableStateOf(false) }
    val imageSize = 128.dp * LocalControlScale.current
    val geometry = imageModifier ?: Modifier.size(imageSize)
    val reference = state.candidate?.previewReference ?: state.displayUrl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (reference != null) {
            RemoteImage(
                reference,
                if (state.candidate !=
                    null
                ) {
                    stringResource(R.string.photo_preview)
                } else {
                    imageDescription
                },
                geometry.clip(imageShape),
            )
        } else {
            Surface(shape = imageShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(geometry, contentAlignment = Alignment.Center) {
                    if (state.loadState == ImageLoadState.LOADING ||
                        state.isResolvingUrl
                    ) {
                        CircularProgressIndicator()
                    } else {
                        Icon(
                            emptyIcon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            if (state.loadState == ImageLoadState.READY &&
                state.attachment == null
            ) {
                Text(stringResource(R.string.photo_empty))
            }
        }
        when {
            state.unconfirmedBlobId != null -> {
                FailureContent(
                    stringResource(R.string.photo_unconfirmed),
                    onRetry,
                )
            }

            state.loadState == ImageLoadState.FAILED -> {
                FailureContent(
                    stringResource(R.string.photo_load_failure),
                    onRetry,
                )
            }

            state.failure == ImageFailure.SAVE -> {
                FailureContent(
                    stringResource(R.string.photo_save_failure),
                    onRetry,
                )
            }

            state.failure == ImageFailure.REMOVE -> {
                FailureContent(
                    stringResource(R.string.photo_remove_failure),
                    onRetry,
                )
            }

            state.failure == ImageFailure.SELECTION -> {
                FailureContent(
                    stringResource(R.string.photo_selection_failure),
                    onChoosePhoto,
                )
            }

            state.displayFailed && state.candidate == null -> {
                FailureContent(
                    stringResource(R.string.photo_display_failure),
                    onRetry,
                )
            }
        }
        if (state.operation ==
            ImageOperation.REMOVING
        ) {
            LoadingContent(stringResource(R.string.photo_removing))
        }
        if (state.operation ==
            ImageOperation.RECONCILING
        ) {
            LoadingContent(stringResource(R.string.photo_checking))
        }
        OutlinedButton(onClick = onChoosePhoto, enabled = state.canChoose) {
            Text(stringResource(R.string.photo_choose))
        }
        if (state.candidate != null) {
            AppActionButton(
                stringResource(R.string.photo_save),
                onSave,
                enabled = state.canSave,
                isSubmitting = state.operation == ImageOperation.SAVING,
                progressDescription = stringResource(R.string.photo_saving),
            )
            TextButton(onClick = onDiscard, enabled = state.canChoose) {
                Text(stringResource(R.string.photo_discard))
            }
        }
        if (state.attachment != null && state.candidate == null) {
            TextButton(
                onClick = { confirmingRemoval = true },
                enabled = state.canRemove,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) { Text(stringResource(R.string.photo_remove)) }
        }
    }
    if (confirmingRemoval && state.canRemove) {
        ConfirmationDialog(
            stringResource(R.string.photo_remove_title),
            stringResource(R.string.photo_remove_body),
            onConfirm = {
                confirmingRemoval = false
                onRemove()
            },
            onCancel = { confirmingRemoval = false },
            onDismiss = { confirmingRemoval = false },
            confirmLabel = stringResource(R.string.remove),
            destructive = true,
        )
    }
}

/** Frames image states without repositories, uploads, or public image requests.
 * @param state Static image presentation fixture.
 */
@Composable
private fun ImagePreviewContent(state: ImageSlotState) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.profile_photo)) {
            ImageSlot(
                state,
                {},
                {},
                {},
                {},
                {},
                stringResource(R.string.profile_photo),
                Icons.Default.AccountCircle,
                imageShape = CircleShape,
            )
        }
    }
}

/** Previews an unresolved attachment read. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LoadingImagePreview() {
    ImagePreviewContent(ImageSlotState())
}

/** Previews a confirmed empty slot and its native picker action. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EmptyImagePreview() {
    ImagePreviewContent(ImageSlotState(loadState = ImageLoadState.READY))
}

/** Previews retryable read failure rather than an empty-image success. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FailedImagePreview() {
    ImagePreviewContent(ImageSlotState(loadState = ImageLoadState.FAILED))
}

/** Previews a pending removal while the saved image's display reference is unresolved. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RemovingImagePreview() {
    ImagePreviewContent(
        ImageSlotState(
            loadState = ImageLoadState.READY,
            attachment =
                ImageAttachment(
                    ImageTarget(ImageTargetType.ACCOUNT, "account-1"),
                    "example",
                    Instant.EPOCH,
                ),
            isResolvingUrl = true,
            operation = ImageOperation.REMOVING,
        ),
    )
}

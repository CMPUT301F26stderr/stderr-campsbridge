package io.github.CMPUT301F26stderr.campsbridge.ui.preview

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.Instant

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

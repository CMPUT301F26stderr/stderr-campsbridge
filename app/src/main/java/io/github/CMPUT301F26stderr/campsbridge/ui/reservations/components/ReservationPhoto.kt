package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.RemoteImage

/** Keeps a bounded thumbnail while the saved row remains independently readable.
 * @param photo Current supplemental image state.
 * @param description Saved campsite name used for accessibility.
 */
@Composable
internal fun ReservationPhoto(
    photo: ImageSlotState,
    description: String,
) {
    val thumbnail = Modifier.size(80.dp).clip(MaterialTheme.shapes.small)
    if (photo.loadState == ImageLoadState.LOADING || photo.isResolvingUrl) {
        Surface(thumbnail, color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
    } else {
        RemoteImage(photo.displayUrl, description, thumbnail, compactFailure = true)
    }
}

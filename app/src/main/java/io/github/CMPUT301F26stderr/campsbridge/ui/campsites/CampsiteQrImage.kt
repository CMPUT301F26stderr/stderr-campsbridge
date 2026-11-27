/*
 * Temporary generated display content, never serialized or stored as a campsite attachment.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.graphics.ImageBitmap

/** Temporary generated display content, never serialized or stored as a campsite attachment. */
sealed interface CampsiteQrImage {
    /** Native generation has not returned for this identity. */
    data object Loading : CampsiteQrImage

    /** Native generation failed; an explicit retry can regenerate the same saved identity. */
    data object Failure : CampsiteQrImage

    /** Complete code including its white quiet zone.
     * @param bitmap Transient generated pixels owned by this route's composition.
     */
    data class Ready(
        val bitmap: ImageBitmap,
    ) : CampsiteQrImage
}

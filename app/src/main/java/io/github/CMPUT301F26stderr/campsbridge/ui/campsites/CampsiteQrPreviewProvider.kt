/*
 * Supplies explicit native-generation loading and failure without simulating success.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Supplies explicit native-generation loading and failure without simulating success. */
class CampsiteQrPreviewProvider : PreviewParameterProvider<CampsiteQrImage> {
    override val values: Sequence<CampsiteQrImage> = sequenceOf(CampsiteQrImage.Loading, CampsiteQrImage.Failure)
}

/*
 * Shows the actual encoded preview identity without storing pixels or substituting a decorative QR.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.createCampsiteQrBitmap
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Shows the actual encoded preview identity without storing pixels or substituting a decorative QR. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large QR display", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CampsiteQrPreview() {
    val site =
        previewCampsite().let {
            it.copy(details = it.details.copy(name = "Riverbend family campsite and riverside pitches"))
        }
    val image by rememberCampsiteQrImage(site.id, 0, ::createCampsiteQrBitmap)
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteQrScreen(CampsiteDetailUiState.Content(site), image, {}, {})
    }
}

/** Shows native generation feedback without loading native libraries in passive preview states.
 * @param image Explicit loading or failed-generation content.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large QR recovery", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CampsiteQrRecoveryPreview(
    @PreviewParameter(CampsiteQrPreviewProvider::class) image: CampsiteQrImage,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteQrScreen(CampsiteDetailUiState.Content(previewCampsite()), image, {}, {})
    }
}

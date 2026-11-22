package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Keeps scan recovery beside the actual Explore toolbar and retained results at both scales.
 * @param feedback Permission, startup or foreign-code recovery.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large scan recovery", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun QrScanRecoveryPreview(
    @PreviewParameter(QrScanPreviewProvider::class) feedback: QrScanFeedback,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        AppScreen(stringResource(R.string.discovery_title)) {
            ExploreScreen(
                ExploreUiState(result = ExploreResult.Content(listOf(previewCampsite()), false)),
                {},
                {},
                {},
                {},
                {},
                {},
                onScan = {},
                scanFeedback = feedback,
                onOpenSettings = {},
            )
        }
    }
}

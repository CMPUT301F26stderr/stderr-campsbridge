package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks saved feedback, retained author input and reachable recovery at both supported preview scales.
 * @param state Passive source and action outcome; no repositories are acquired.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large feedback", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteReviewsPreview(
    @PreviewParameter(CampsiteReviewsPreviewProvider::class) state: CampsiteReviewsUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteReviewsScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

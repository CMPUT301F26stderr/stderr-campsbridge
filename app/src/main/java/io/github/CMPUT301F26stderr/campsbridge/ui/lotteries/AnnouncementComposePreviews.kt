/*
 * Checks long announcement content and reachable form/recovery controls at both scale settings.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks long announcement content and reachable form/recovery controls at both scale settings.
 * @param state Passive saved context and local operation/input state.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large announcement", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun AnnouncementComposePreview(
    @PreviewParameter(AnnouncementComposePreviewProvider::class) state: AnnouncementComposeUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        AnnouncementComposeScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.SettingsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks root feedback and reachable actions with ordinary and enlarged text/controls.
 * @param state Original request and its confirmation or recovery phase.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large removal", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun AccountRemovalPreview(
    @PreviewParameter(AccountRemovalPreviewProvider::class) state: AccountRemovalUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        SettingsScreen({}, {}, onDeleteProfile = {})
        AccountRemovalHost(state, {}, {}, {}, {}, {})
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks long saved event content and read-only recovery at both font/control scales.
 * @param state Passive original event and source/context outcomes.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large sent history", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun SentNotificationsPreview(
    @PreviewParameter(SentNotificationsPreviewProvider::class) state: SentNotificationsUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        AppScreen(stringResource(R.string.inbox_sent)) { SentNotificationsScreen(state, {}, {}, {}, {}) }
    }
}

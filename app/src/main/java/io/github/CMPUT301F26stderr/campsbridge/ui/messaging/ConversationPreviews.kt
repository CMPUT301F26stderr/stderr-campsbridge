package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks independent history/composer overflow and explicit recovery at normal and doubled scale.
 * @param state Passive participant, message and draft outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large conversation", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ConversationPreview(
    @PreviewParameter(ConversationPreviewProvider::class) state: ConversationUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        ConversationScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

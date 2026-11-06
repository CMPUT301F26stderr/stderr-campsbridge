/*
 * Passive supplied-state previews; the primary frame belongs to its workspace host.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Passive supplied-state previews; the primary frame belongs to its workspace host.
 * @param state Real-shaped history or read outcome without a backend.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun InboxPreview(
    @PreviewParameter(InboxPreviewProvider::class) state: InboxUiState,
) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.inbox_title)) { InboxScreen(state, {}, {}, {}, {}) }
    }
}

/*
 * Previews the conversation list's content, loading, empty, failure and partial outcomes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Shows each list outcome at normal and enlarged text.
 * @param state Passive list outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ConversationListPreview(
    @PreviewParameter(ConversationListPreviewProvider::class) state: ConversationListUiState,
) {
    CampsbridgeTheme { ConversationListScreen(state, {}, {}) }
}

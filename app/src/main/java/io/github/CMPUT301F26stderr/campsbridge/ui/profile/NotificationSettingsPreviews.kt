package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermissionState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Previews settings and enlarged native controls with inert callbacks and explicit platform facts.
 * @param preference Passive preference projection.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun NotificationSettingsPreview(
    @PreviewParameter(NotificationSettingsPreviewProvider::class) preference: NotificationPreferenceUiState,
) {
    CampsbridgeTheme {
        NotificationSettingsScreen(
            if (preference.enabled ==
                null
            ) {
                ProfileUiState.Loading
            } else {
                ProfileUiState.Content(profilePreviewAccount())
            },
            preference,
            NotificationPermissionState(true, false, true, null),
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

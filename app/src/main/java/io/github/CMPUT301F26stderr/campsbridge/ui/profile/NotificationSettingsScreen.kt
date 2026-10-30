package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen

/** Keeps the settings destination usable until its preference controls are implemented.
 * @param model Existing profile parent, retained without starting a preference write.
 * @param permission Host permission boundary, unused by this pending screen.
 * @param onBack Returns to Settings.
 */
@Composable
fun NotificationSettingsRoute(
    model: ProfileViewModel,
    permission: NotificationPermission,
    onBack: () -> Unit,
) {
    AppScreen(stringResource(R.string.notification_settings_title), onBack = onBack) {
        EmptyContent(stringResource(R.string.feature_pending))
    }
}

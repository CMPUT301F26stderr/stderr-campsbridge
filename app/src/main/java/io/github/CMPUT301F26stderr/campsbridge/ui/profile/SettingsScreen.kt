package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Links the shared policy reader and notification preference/device settings.
 * @param onPolicies Opens the shared policy menu.
 * @param onBack Returns to the current workspace's profile menu.
 * @param onNotifications Opens saved preference and Android permission/status settings.
 * @param onCredits Opens bundled image/font credits and dependency licenses.
 * @param modifier Caller layout.
 */
@Composable
fun SettingsScreen(
    onPolicies: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNotifications: () -> Unit = {},
    onCredits: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(R.string.settings_title), modifier, onBack) {
        NavigationRow(stringResource(R.string.notification_settings_title), onNotifications)
        NavigationRow(stringResource(R.string.policies_title), onPolicies)
        NavigationRow(stringResource(R.string.credits_title), onCredits)
    }
}

/** Previews the sparse native settings menu without dropped or pending controls. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SettingsPreview() {
    CampsbridgeTheme { SettingsScreen({}, {}) }
}

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

/** Contains only implemented settings destinations; notification controls arrive with M4a.
 * @param onPolicies Opens the shared policy menu.
 * @param onBack Returns to the current workspace's profile menu.
 * @param modifier Caller layout.
 */
@Composable
fun SettingsScreen(
    onPolicies: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(R.string.settings_title), modifier, onBack) {
        NavigationRow(stringResource(R.string.policies_title), onPolicies)
    }
}

/** Previews the sparse native settings menu without dropped or pending controls. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SettingsPreview() {
    CampsbridgeTheme { SettingsScreen({}, {}) }
}

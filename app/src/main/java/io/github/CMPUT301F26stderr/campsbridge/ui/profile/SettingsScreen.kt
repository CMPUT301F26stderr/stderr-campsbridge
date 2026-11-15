/*
 * Links the shared policy reader and notification preference/device settings.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationGroup
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Links the shared policy reader and notification preference/device settings.
 * @param onPolicies Opens the shared policy menu.
 * @param onBack Returns to the current workspace's profile menu.
 * @param onNotifications Opens saved preference and Android permission/status settings.
 * @param onCredits Opens bundled image/font credits and dependency licenses.
 * @param modifier Caller layout.
 * @param onDeleteProfile Optional root-owned deletion confirmation entry.
 */
@Composable
fun SettingsScreen(
    onPolicies: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNotifications: () -> Unit = {},
    onCredits: () -> Unit = {},
    onDeleteProfile: (() -> Unit)? = null,
) {
    AppScreen(stringResource(R.string.settings_title), modifier, onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            NavigationGroup(
                listOf(
                    { NavigationRow(stringResource(R.string.notification_settings_title), onNotifications) },
                    { NavigationRow(stringResource(R.string.policies_title), onPolicies) },
                    { NavigationRow(stringResource(R.string.credits_title), onCredits) },
                ),
            )
            onDeleteProfile?.let { onDelete ->
                NavigationGroup(
                    listOf(
                        { NavigationRow(stringResource(R.string.account_removal_entry), onDelete, destructive = true) },
                    ),
                )
            }
        }
    }
}

/** Previews the sparse native settings menu without dropped or pending controls. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SettingsPreview() {
    CampsbridgeTheme { SettingsScreen({}, {}, onDeleteProfile = {}) }
}

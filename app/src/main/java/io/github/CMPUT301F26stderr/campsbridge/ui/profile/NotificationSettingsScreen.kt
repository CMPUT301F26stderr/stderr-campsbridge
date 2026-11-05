package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.SwitchRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Renders notification settings, alert preferences, and permission status.
 * @param model Existing profile parent holding notification preferences.
 * @param permission Host permission inspection and settings launcher.
 * @param onBack Returns to Settings.
 */
@Composable
fun NotificationSettingsRoute(
    model: ProfileViewModel,
    permission: NotificationPermission,
    onBack: () -> Unit,
) {
    val state by model.notificationPreferenceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, model, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.refreshPermission(permission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        model.refreshPermission(permission)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NotificationSettingsScreen(
        state = state,
        onToggleNotifications = model::updateNotificationsEnabled,
        onRetry = model::retryNotificationsEnabled,
        onOpenSettings = {
            try {
                context.startActivity(permission.settingsIntent())
            } catch (_: ActivityNotFoundException) {
                model.markSettingsActivityMissing()
            }
        },
        onBack = onBack,
    )
}

@Composable
fun NotificationSettingsScreen(
    state: NotificationPreferenceUiState,
    onToggleNotifications: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(R.string.notification_settings_title), modifier, onBack) {
        Column(Modifier.fillMaxSize().padding(Spacing.medium)) {
            SwitchRow(
                title = "Enable notifications",
                checked = state.requestedNotificationsEnabled ?: state.notificationsEnabled,
                onCheckedChange = onToggleNotifications,
                enabled = !state.isSaving,
                supportingText = if (state.saveFailed) "Save failed. Tap retry." else null,
            )
            if (state.saveFailed) {
                NavigationRow("Retry save", onRetry)
            }
            if (state.canOpenSettings) {
                NavigationRow("App notification settings", onOpenSettings)
            } else {
                Text("System notification settings activity could not be found.")
            }
            state.permissionState?.let { perm ->
                Text("Can post: ${perm.canPost}")
                Text("Permission granted: ${perm.permissionGranted}")
                Text("App enabled: ${perm.appEnabled}")
            }
        }
    }
}

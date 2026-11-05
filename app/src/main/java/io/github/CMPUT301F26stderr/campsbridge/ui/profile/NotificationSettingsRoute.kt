package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission

/** Connects one profile parent to user-triggered Android launchers and resumed device inspection.
 * @param model Existing workspace profile parent; settings never create another account observer.
 * @param permission Host-owned platform boundary, independent of saved preferences.
 * @param onBack Returns to this workspace's actual Settings menu.
 */
@Composable
fun NotificationSettingsRoute(
    model: ProfileViewModel,
    permission: NotificationPermission,
    onBack: () -> Unit,
) {
    val source by model.state.collectAsStateWithLifecycle()
    val preference by model.notificationPreference.collectAsStateWithLifecycle()
    var device by remember(permission) { mutableStateOf(permission.status()) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            device = permission.status()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            device = permission.status()
        }
    LaunchedEffect(permission, lifecycle) {
        if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) device = permission.status()
    }
    NotificationSettingsScreen(
        source,
        preference,
        device,
        onToggle = {
            if (owner.lifecycle.currentState ==
                Lifecycle.State.RESUMED
            ) {
                model.setNotificationsEnabled(it)
            }
        },
        onRetrySave = {
            if (owner.lifecycle.currentState ==
                Lifecycle.State.RESUMED
            ) {
                model.retryNotificationPreference()
            }
        },
        onRetrySource = model::retry,
        onRequestPermission = {
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                val current = permission.status()
                device = current
                if (current.requiresRuntimePermission && !current.permissionGranted) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        onSystemSettings = {
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                settingsUnavailable = false
                try {
                    settingsLauncher.launch(permission.settingsIntent())
                } catch (_: ActivityNotFoundException) {
                    settingsUnavailable = true
                }
            }
        },
        onBack = { if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) onBack() },
        settingsUnavailable = settingsUnavailable,
    )
}

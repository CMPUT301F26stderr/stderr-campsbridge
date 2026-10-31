package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Stable channel identity shared by settings inspection and the OS publisher. */
const val RESULT_NOTIFICATION_CHANNEL = "campsbridge_results"

/** Real device facts, independent of saved account alert preferences.
 * @param requiresRuntimePermission Whether this Android version uses POST_NOTIFICATIONS.
 * @param permissionGranted Whether the runtime permission is granted or not needed.
 * @param appEnabled Whether Android enables app-level notifications.
 * @param channelEnabled Existing channel allowance; null when absent or unsupported.
 */
data class NotificationPermissionState(
    val requiresRuntimePermission: Boolean,
    val permissionGranted: Boolean,
    val appEnabled: Boolean,
    val channelEnabled: Boolean?,
) {
    val canPost: Boolean
        get() = permissionGranted && appEnabled && channelEnabled != false
}

/** Platform inspection and settings launch contract; it neither saves profiles nor posts alerts. */
interface NotificationPermission {
    /** Reads current runtime/app/channel facts without creating or resetting a channel. */
    fun status(): NotificationPermissionState

    /** Builds the actual app's Android notification/settings Intent without launching it. */
    fun settingsIntent(): Intent
}

/** Inspects real Android notification facts; routes own user-triggered launchers.
 * @param context Application/activity context used only for platform services and package identity.
 */
class AndroidNotificationPermission(
    private val context: Context,
) : NotificationPermission {
    /** Returns current device facts without changing permission, channel or account state. */
    override fun status(): NotificationPermissionState {
        val runtime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val granted =
            !runtime || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        val channel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context
                    .getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(RESULT_NOTIFICATION_CHANNEL)
                    ?.let { it.importance != NotificationManager.IMPORTANCE_NONE }
            } else {
                null
            }
        return NotificationPermissionState(
            runtime,
            granted,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channel,
        )
    }

    /** Returns a version-appropriate app-settings Intent without launching another activity. */
    override fun settingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        }
}

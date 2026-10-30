package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Nine fixed SDK/Intent contracts using real Android APIs under controlled platform shadows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPermissionTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    /** Android 7 has no runtime notification permission or channel APIs. */
    @Test
    @Config(sdk = [24])
    fun android24NeedsNoRuntimePermissionOrChannel() {
        val status = adapter().status()
        assertFalse(status.requiresRuntimePermission)
        assertTrue(status.permissionGranted)
        assertTrue(status.appEnabled)
        assertNull(status.channelEnabled)
        assertTrue(status.canPost)
    }

    /** Android 12 retains app-level notification permission without a runtime request. */
    @Test
    @Config(sdk = [32])
    fun android32NeedsNoRuntimeNotificationRequest() {
        val status = adapter().status()
        assertFalse(status.requiresRuntimePermission)
        assertTrue(status.permissionGranted)
        assertTrue(status.canPost)
    }

    /** Android 13 denial is reported, and the installed manifest declares the real permission. */
    @Test fun android33DenialAndManifestDeclarationAreRealFacts() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val status = adapter().status()
        assertTrue(status.requiresRuntimePermission)
        assertFalse(status.permissionGranted)
        assertFalse(status.canPost)
        val declared =
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS,
                ).requestedPermissions
                .orEmpty()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in declared)
    }

    /** Runtime grant alone cannot override an app-wide system notification block. */
    @Test fun grantedPermissionAndAppLevelBlockingAreSeparate() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(adapter().status().permissionGranted)
        assertTrue(adapter().status().canPost)
        shadowOf(manager).setNotificationsEnabled(false)
        val blocked = adapter().status()
        assertTrue(blocked.permissionGranted)
        assertFalse(blocked.appEnabled)
        assertFalse(blocked.canPost)
    }

    /** Inspecting an absent canonical channel must not create a channel as a side effect. */
    @Test fun absentChannelInspectionDoesNotCreateAnything() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val status = adapter().status()
        assertTrue(status.requiresRuntimePermission)
        assertNull(status.channelEnabled)
        assertTrue(manager.notificationChannels.isEmpty())
    }

    /** A blocked canonical channel prevents posts even when app/runtime permission allow them. */
    @Test fun blockedResultChannelPreventsAllowance() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                RESULT_NOTIFICATION_CHANNEL,
                "Results",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        val status = adapter().status()
        assertEquals(false, status.channelEnabled)
        assertFalse(status.canPost)
    }

    /** An existing permitted channel is observed without changing its configured importance. */
    @Test fun enabledResultChannelPreservesAllowance() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_NOTIFICATION_CHANNEL,
                "Results",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val status = adapter().status()
        assertEquals(true, status.channelEnabled)
        assertTrue(status.canPost)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL).importance,
        )
    }

    /** Modern settings identify this app with the official package extra. */
    @Test fun modernSettingsIntentIdentifiesTheActualApp() {
        val intent = adapter().settingsIntent()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    /** Older supported Android versions receive an app-details/package URI fallback. */
    @Test
    @Config(sdk = [24])
    fun android24SettingsFallbackUsesPackageUri() {
        val intent = adapter().settingsIntent()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.fromParts("package", context.packageName, null), intent.data)
    }

    /** Supplies the real SDK adapter without replacing its Android reads. */
    private fun adapter(): AndroidNotificationPermission =
        AndroidNotificationPermission(context)
}

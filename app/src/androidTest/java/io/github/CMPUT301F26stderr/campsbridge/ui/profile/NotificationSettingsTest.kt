package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.AndroidNotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermissionState
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Seven fixed settings interactions matching the notification UI design. */
class NotificationSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val device = ControlledNotificationPermission()
    private val results = ControlledNotificationResults()

    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    @Test fun actualSettingsChildRestoresWithoutAutomaticPermissionOrSave() {
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText("Enable notifications").assertIsOn()
        compose.onNodeWithText("Permission granted: false").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Enable notifications").assertIsOn()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Policies").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Personal details").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.profileCreations)
            assertTrue(results.intents.isEmpty())
            assertTrue(host.accounts.settingsRequests.isEmpty())
        }
    }

    @Test fun heldToggleKeepsConfirmedDisplayAcrossRestore() {
        val held = CompletableDeferred<Unit>()
        host.accounts.settingsHandler = { _, settings ->
            held.await()
            val account = checkNotNull(host.accounts.account.value)
            host.accounts.account.value = account.copy(settings = settings)
        }
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText("Enable notifications").performClick()
        compose.onNodeWithText("Enable notifications").assertIsOn().assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            held.complete(Unit)
        }
        compose.onNodeWithText("Enable notifications").assertIsOff()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            assertTrue(results.intents.isEmpty())
        }
    }

    @Test fun failedToggleRetriesExplicitlyAndReopensConfirmed() {
        host.accounts.settingsHandler = { _, _ -> throw IOException("save offline") }
        start()
        compose.onNodeWithText("Enable notifications").performClick()
        compose.onNodeWithText("Save failed. Tap retry.").assertIsDisplayed()
        compose.onNodeWithText("Enable notifications").assertIsOn()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            succeed()
        }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Enable notifications").assertIsOff()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Enable notifications").assertIsOff()
        compose.runOnIdle {
            assertEquals(2, host.accounts.settingsRequests.size)
            assertTrue(results.intents.isEmpty())
        }
    }

    @Test fun deniedPermissionIsSeparateFromAppPreferenceAndInbox() {
        results.allowPermission = false
        start()
        compose.onNodeWithText("Enable notifications").assertIsOn()
        compose.runOnIdle {
            assertTrue(host.accounts.settingsRequests.isEmpty())
            assertTrue(host.notifications.readRequests.isEmpty())
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("No notifications yet.").assertIsDisplayed()
    }

    @Test fun systemSettingsReturnRefreshesDeviceStatusOnly() {
        host.accounts.account.value =
            sampleAccount().let {
                it.copy(
                    settings = it.settings.copy(notificationsEnabled = false),
                )
            }
        device.current = NotificationPermissionState(false, false, false, null)
        results.onSettings =
            { device.current = NotificationPermissionState(true, true, true, true) }
        start()
        compose.onNodeWithText("Enable notifications").assertIsOff()
        try {
            compose.onNodeWithText("App notification settings").performScrollTo().performClick()
        } catch (_: Throwable) {
            compose.onNodeWithText("App notification settings").performClick()
        }
        compose.runOnIdle {}
        compose.onNodeWithText("Permission granted: true").assertIsDisplayed()
        compose.onNodeWithText("Enable notifications").assertIsOff()
        compose.runOnIdle {
            val intent = results.intents.single()
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
            )
            assertTrue(host.accounts.settingsRequests.isEmpty())
        }
    }

    @Test fun failedAccountSourceNeedsReadRetryWithoutChangingDeviceStatus() {
        val profileSource = FakeAccountRepository(sampleAccount())
        host.profileAccounts = profileSource
        start()
        compose.runOnIdle { profileSource.streamFailure.value = IOException("account offline") }
        compose.onNodeWithText("Enable notifications").assertIsNotEnabled()
        compose.runOnIdle { profileSource.streamFailure.value = null }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Retry").performClick()
        try {
            compose.onNodeWithText("Settings").performScrollTo().performClick()
        } catch (_: Throwable) {
            compose.onNodeWithText("Settings").performClick()
        }
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Enable notifications").assertIsOn()
        compose.runOnIdle {
            assertTrue(host.accounts.settingsRequests.isEmpty())
            assertTrue(results.intents.isEmpty())
        }
    }

    @Test fun enlargedOwnerSettingsKeepActionsAndBackReachable() {
        start(owner = true, fontScale = 2f)
        try {
            compose.onNodeWithText("Enable notifications").performScrollTo().assertIsDisplayed()
        } catch (_: Throwable) {
            compose.onNodeWithText("Enable notifications").assertIsDisplayed()
        }
        try {
            compose.onNodeWithText("App notification settings").performScrollTo().assertIsDisplayed()
        } catch (_: Throwable) {
            compose.onNodeWithText("App notification settings").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Policies").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        try {
            compose.onNodeWithText("Personal details").performScrollTo().assertIsDisplayed()
        } catch (_: Throwable) {
            compose.onNodeWithText("Personal details").assertIsDisplayed()
        }
        compose.runOnIdle {
            assertEquals(1, host.profileCreations)
            assertTrue(results.intents.isEmpty())
        }
    }

    private fun start(
        restoration: StateRestorationTester? = null,
        owner: Boolean = false,
        fontScale: Float = 1f,
    ) {
        host.notificationPermission = device
        if (owner) {
            host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
            host.preferences.progress.value =
                host.preferences.progress.value
                    .copy(workspace = Role.OWNER)
        }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides results,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) { host.Content() }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithText("Profile").performClick()
        try {
            compose.onNodeWithText("Settings").performScrollTo().performClick()
        } catch (_: Throwable) {
            compose.onNodeWithText("Settings").performClick()
        }
        compose.onNodeWithText("Notifications").performClick()
    }

    private fun succeed() {
        host.accounts.settingsHandler = { _, settings ->
            val account = checkNotNull(host.accounts.account.value)
            host.accounts.account.value = account.copy(settings = settings)
        }
    }
}

private class ControlledNotificationPermission : NotificationPermission {
    var current = NotificationPermissionState(true, false, true, null)

    override fun status(): NotificationPermissionState =
        current

    override fun settingsIntent(): Intent =
        AndroidNotificationPermission(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).settingsIntent()
}

private class ControlledNotificationResults : ActivityResultRegistryOwner {
    val intents = mutableListOf<Intent>()
    var allowPermission = false
    var onSettings: () -> Unit = {}
    override val activityResultRegistry =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                intents += contract.createIntent(context, input)
                if (input == Manifest.permission.POST_NOTIFICATIONS) {
                    dispatchResult(
                        requestCode,
                        Activity.RESULT_OK,
                        Intent()
                            .putExtra(
                                RequestMultiplePermissions.EXTRA_PERMISSIONS,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            ).putExtra(
                                RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                                intArrayOf(
                                    if (allowPermission) {
                                        PackageManager.PERMISSION_GRANTED
                                    } else {
                                        PackageManager.PERMISSION_DENIED
                                    },
                                ),
                            ),
                    )
                } else {
                    onSettings()
                    dispatchResult(requestCode, Activity.RESULT_OK, null)
                }
            }
        }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
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

/** Settings interactions with independent platform and account outcomes. */
class NotificationSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val host = TestAppHost()
    private val device = ControlledNotificationPermission()
    private val results = ControlledNotificationResults()

    /** Retires each real profile/root owner after the independent settings scenario. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** The registered child retains its parent/Back and never auto-launches. */
    @Test fun actualSettingsChildRestoresWithoutAutomaticPermissionOrSave() {
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.onNodeWithText("Not allowed by Android").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("App alerts").assertIsOn()
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

    /** A held save keeps confirmed display and one callback through restoration. */
    @Test fun heldToggleKeepsConfirmedDisplayAcrossRestore() {
        val held = CompletableDeferred<Unit>()
        host.accounts.settingsHandler = { _, settings ->
            held.await()
            val account = checkNotNull(host.accounts.account.value)
            host.accounts.account.value = account.copy(settings = settings)
        }
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText("App alerts").performClick()
        compose.onNodeWithText("App alerts").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Saving alert preference…").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Saving alert preference…").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            held.complete(Unit)
        }
        compose.onNodeWithText("App alerts").assertIsOff()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            assertTrue(results.intents.isEmpty())
        }
    }

    /** A failed saved preference keeps its confirmed value and an explicit retry that reopens. */
    @Test fun failedToggleRetriesExplicitlyAndReopensConfirmed() {
        host.accounts.settingsHandler = { _, _ -> throw IOException("save offline") }
        start()
        compose.onNodeWithText("App alerts").performClick()
        compose.onNodeWithText("Could not save your alert preference.").assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.runOnIdle {
            assertEquals(1, host.accounts.settingsRequests.size)
            succeed()
        }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("App alerts").assertIsOff()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("App alerts").assertIsOff()
        compose.runOnIdle {
            assertEquals(2, host.accounts.settingsRequests.size)
            assertTrue(results.intents.isEmpty())
        }
    }

    /** Explicit denial uses the real contract without changing preference or Inbox. */
    @Test fun deniedPermissionIsSeparateFromAppPreferenceAndInbox() {
        results.allowPermission = false
        start()
        compose.onNodeWithText("Request permission").performClick()
        compose.onNodeWithText("Not allowed by Android").assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.runOnIdle {
            val intent = results.intents.single()
            assertEquals(
                RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS,
                intent.action,
            )
            assertEquals(
                listOf(Manifest.permission.POST_NOTIFICATIONS),
                intent
                    .getStringArrayExtra(
                        RequestMultiplePermissions.EXTRA_PERMISSIONS,
                    )?.toList(),
            )
            assertTrue(host.accounts.settingsRequests.isEmpty())
            assertTrue(host.notifications.readRequests.isEmpty())
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("No notifications yet.").assertIsDisplayed()
    }

    /** Returned Android allowance cannot change a saved app opt-out. */
    @Test fun systemSettingsReturnRefreshesDeviceStatusOnly() {
        host.accounts.account.value =
            sampleAccount().let {
                it.copy(
                    settings = it.settings.copy(notificationsEnabled = false),
                )
            }
        device.current = NotificationPermissionState(true, true, false, null)
        results.onSettings =
            { device.current = NotificationPermissionState(true, true, true, true) }
        start()
        compose.onNodeWithText("App alerts").assertIsOff()
        compose.onNodeWithText("Open Android settings").performScrollTo().performClick()
        compose.onNodeWithText("Allowed by Android").assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsOff()
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

    /** Failed account reads disable saves and recover independently of truthful Android status. */
    @Test fun failedAccountSourceNeedsReadRetryWithoutChangingDeviceStatus() {
        val profileSource = FakeAccountRepository(sampleAccount())
        host.profileAccounts = profileSource
        start()
        compose.runOnIdle { profileSource.streamFailure.value = IOException("account offline") }
        compose.onNodeWithText("Could not load profile").assertIsDisplayed()
        compose.onNodeWithText("Not allowed by Android").assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsNotEnabled()
        compose.runOnIdle { profileSource.streamFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.runOnIdle {
            assertTrue(host.accounts.settingsRequests.isEmpty())
            assertTrue(results.intents.isEmpty())
        }
    }

    /** Enlarged owner settings retain native actions, system scaling and actual parent Back. */
    @Test fun enlargedOwnerSettingsKeepActionsAndBackReachable() {
        start(owner = true, fontScale = 2f)
        compose.onNodeWithText("App alerts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Request permission").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Android settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Policies").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Personal details").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.profileCreations)
            assertTrue(results.intents.isEmpty())
        }
    }

    /** A missing Android handler reports feedback without saving an app preference. */
    @Test fun unavailableAndroidSettingsPreservesThePreference() {
        results.settingsUnavailable = true
        start()
        compose.onNodeWithText("Open Android settings").performScrollTo().performClick()
        compose
            .onNodeWithText("Android notification settings are unavailable.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.runOnIdle {
            assertEquals(1, results.intents.size)
            assertTrue(host.accounts.settingsRequests.isEmpty())
        }
    }

    /** Foreground reinspection sees external device changes without a launcher result or save. */
    @Test fun foregroundReturnRefreshesDeviceFactsWithoutASettingsLaunch() {
        start()
        compose.onNodeWithText("Not allowed by Android").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        device.current = NotificationPermissionState(true, true, true, true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Allowed by Android").assertIsDisplayed()
        compose.onNodeWithText("App alerts").assertIsOn()
        compose.runOnIdle {
            assertTrue(results.intents.isEmpty())
            assertTrue(host.accounts.settingsRequests.isEmpty())
        }
    }

    /** Opens actual root/profile/settings with explicit device and launcher boundaries.
     * @param restoration Optional composition/navigation restoration driver.
     * @param owner Whether to use the actual owner workspace.
     * @param fontScale System text scale for the scenario.
     */
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
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides results,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) { host.Content() }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Notifications").performClick()
    }

    /** Configures only an explicitly requested account settings outcome. */
    private fun succeed() {
        host.accounts.settingsHandler = { _, settings ->
            val account = checkNotNull(host.accounts.account.value)
            host.accounts.account.value = account.copy(settings = settings)
        }
    }
}

/** Controlled device facts; Intent construction still uses the production Android adapter. */
private class ControlledNotificationPermission : NotificationPermission {
    var current = NotificationPermissionState(true, false, true, null)

    /** Returns the current explicit fixture facts without changing account preferences. */
    override fun status(): NotificationPermissionState =
        current

    /** Builds the real Android settings Intent for the instrumented target package. */
    override fun settingsIntent(): Intent =
        AndroidNotificationPermission(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).settingsIntent()
}

/** Captures real Activity Result contracts and supplies explicit platform outcomes. */
private class ControlledNotificationResults : ActivityResultRegistryOwner {
    val intents = mutableListOf<Intent>()
    var allowPermission = false
    var settingsUnavailable = false
    var onSettings: () -> Unit = {}
    override val activityResultRegistry =
        object : ActivityResultRegistry() {
            /** Captures the real launch and returns a controlled platform outcome.
             * @param requestCode Registered result key.
             * @param contract Real route contract.
             * @param input Real route input.
             * @param options Optional Android launch options.
             */
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
                    if (settingsUnavailable) throw ActivityNotFoundException("No settings handler")
                    onSettings()
                    dispatchResult(requestCode, Activity.RESULT_OK, null)
                }
            }
        }
}

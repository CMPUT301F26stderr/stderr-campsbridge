package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ColorMode
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Ten fixed preference contracts, independent of Android permission and notification posting. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileNotificationPreferenceTest {
    @get:Rule val main = MainDispatcherRule()
    private val account = CampingFixtures.account()
    private val accounts = FakeAccountRepository(account)
    private val store = ViewModelStore()

    /** No default is invented before observation; confirmed external changes perform no writes. */
    @Test fun observedPreferenceHasHonestInitialAndExternalValues() =
        preferenceTest {
            val model = model()
            assertEquals(NotificationPreferenceUiState(), model.notificationPreference.value)
            runCurrent()
            assertEquals(true, model.notificationPreference.value.enabled)
            assertTrue(model.notificationPreference.value.canChange)
            accounts.account.value =
                account.copy(settings = account.settings.copy(notificationsEnabled = false))
            runCurrent()
            assertEquals(false, model.notificationPreference.value.enabled)
            assertTrue(accounts.settingsRequests.isEmpty())
        }

    /** Both toggle directions preserve latest unrelated settings and unsaved personal input. */
    @Test fun explicitTogglePreservesLatestSettingsActorAndDraft() =
        preferenceTest {
            val latest =
                account.settings.copy(
                    colorMode = ColorMode.COLOR_BLIND,
                    fontScale = 1.25,
                    controlScale = 1.1,
                )
            accounts.account.value = account.copy(settings = latest)
            succeed()
            val model = model()
            runCurrent()
            model.beginPersonalEdit()
            model.editName("Unsaved name")
            model.setNotificationsEnabled(false)
            runCurrent()
            assertEquals(
                listOf(account.id to latest.copy(notificationsEnabled = false)),
                accounts.settingsRequests,
            )
            assertEquals(false, model.notificationPreference.value.enabled)
            assertEquals(
                "Unsaved name",
                (model.state.value as ProfileUiState.Content).personalDraft?.nameText,
            )
            model.setNotificationsEnabled(true)
            runCurrent()
            assertEquals(account.id to latest, accounts.settingsRequests.last())
            assertEquals(2, accounts.settingsRequests.size)
            assertEquals(true, model.notificationPreference.value.enabled)
            assertEquals(
                "Unsaved name",
                (model.state.value as ProfileUiState.Content).personalDraft?.nameText,
            )
            assertTrue(accounts.profileUpdates.isEmpty())
        }

    /** Unknown/missing sources and requesting the already confirmed value cannot start writes. */
    @Test fun loadingMissingAndUnchangedPreferenceAreNoOps() =
        preferenceTest {
            val model = model()
            model.setNotificationsEnabled(false)
            runCurrent()
            model.setNotificationsEnabled(true)
            accounts.account.value = null
            runCurrent()
            model.setNotificationsEnabled(false)
            assertTrue(accounts.settingsRequests.isEmpty())
            assertFalse(model.notificationPreference.value.canChange)
        }

    /** Pending observed values cannot confirm a save or permit another command. */
    @Test fun heldSaveKeepsConfirmedDisplayAndRejectsRepeatedToggles() =
        preferenceTest {
            val held = CompletableDeferred<Unit>()
            accounts.settingsHandler = { _, settings ->
                accounts.account.value = account.copy(settings = settings)
                held.await()
            }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            assertTrue(model.notificationPreference.value.isSaving)
            runCurrent()
            assertEquals(true, model.notificationPreference.value.enabled)
            assertEquals(false, model.notificationPreference.value.requestedEnabled)
            assertFalse(model.notificationPreference.value.canChange)
            model.setNotificationsEnabled(true)
            model.setNotificationsEnabled(false)
            model.retryNotificationPreference()
            assertEquals(1, accounts.settingsRequests.size)
            held.complete(Unit)
            runCurrent()
            assertEquals(false, model.notificationPreference.value.enabled)
            assertFalse(model.notificationPreference.value.isSaving)
            assertTrue(model.notificationPreference.value.canChange)
        }

    /** Acknowledgement precedes display; old observed data cannot overwrite it before catch-up. */
    @Test fun acknowledgedValueWaitsForMatchingObservationBeforeExternalRefresh() =
        preferenceTest {
            accounts.settingsHandler = { _, _ -> }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            runCurrent()
            assertEquals(false, model.notificationPreference.value.enabled)
            accounts.account.value = account.copy(name = "Cached refresh")
            runCurrent()
            assertEquals(false, model.notificationPreference.value.enabled)
            accounts.account.value =
                account.copy(settings = account.settings.copy(notificationsEnabled = false))
            runCurrent()
            accounts.account.value = account
            runCurrent()
            assertEquals(true, model.notificationPreference.value.enabled)
            assertEquals(1, accounts.settingsRequests.size)
        }

    /** Failure retains the prior display/target; explicit Retry uses latest settings. */
    @Test fun failedSaveRetainsTargetForExplicitRetryWithLatestSettings() =
        preferenceTest {
            accounts.settingsHandler = { _, _ -> throw IOException("save offline") }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            runCurrent()
            assertTrue(model.notificationPreference.value.saveFailed)
            assertFalse(model.notificationPreference.value.isSaving)
            assertEquals(true, model.notificationPreference.value.enabled)
            assertEquals(false, model.notificationPreference.value.requestedEnabled)
            val latest = account.settings.copy(fontScale = 1.75, controlScale = 1.2)
            accounts.account.value = account.copy(settings = latest)
            runCurrent()
            assertEquals(1, accounts.settingsRequests.size)
            succeed()
            model.retryNotificationPreference()
            runCurrent()
            assertEquals(2, accounts.settingsRequests.size)
            assertEquals(
                account.id to latest.copy(notificationsEnabled = false),
                accounts.settingsRequests.last(),
            )
            assertEquals(false, model.notificationPreference.value.enabled)
            assertFalse(model.notificationPreference.value.saveFailed)
        }

    /** A failed source disables writes; recovering the source never replays a settings mutation. */
    @Test fun failedSourceNeedsReadRetryBeforeAnotherExplicitToggle() =
        preferenceTest {
            succeed()
            val model = model()
            runCurrent()
            accounts.streamFailure.value = IOException("observe offline")
            runCurrent()
            assertEquals(ProfileUiState.Failure, model.state.value)
            assertEquals(true, model.notificationPreference.value.enabled)
            assertFalse(model.notificationPreference.value.canChange)
            model.setNotificationsEnabled(false)
            assertTrue(accounts.settingsRequests.isEmpty())
            accounts.streamFailure.value = null
            model.retry()
            runCurrent()
            assertTrue(model.notificationPreference.value.canChange)
            assertTrue(accounts.settingsRequests.isEmpty())
            model.setNotificationsEnabled(false)
            runCurrent()
            assertEquals(1, accounts.settingsRequests.size)
        }

    /** Missing account data during acknowledgement cannot re-enable subsequent writes. */
    @Test fun missingAccountWhileSavingStaysUnavailableAfterAcknowledgement() =
        preferenceTest {
            val held = CompletableDeferred<Unit>()
            accounts.settingsHandler = { _, _ -> held.await() }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            runCurrent()
            accounts.account.value = null
            runCurrent()
            held.complete(Unit)
            runCurrent()
            assertEquals(ProfileUiState.Unavailable, model.state.value)
            assertFalse(model.notificationPreference.value.canChange)
            assertFalse(model.notificationPreference.value.isSaving)
            model.setNotificationsEnabled(true)
            assertEquals(1, accounts.settingsRequests.size)
        }

    /** Coroutine interruption propagates but cannot leave a permanently busy preference. */
    @Test fun interruptedSaveRemainsExplicitlyRetryable() =
        preferenceTest {
            accounts.settingsHandler = { _, _ -> throw CancellationException("interrupted") }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            runCurrent()
            assertFalse(model.notificationPreference.value.isSaving)
            assertTrue(model.notificationPreference.value.saveFailed)
            assertEquals(false, model.notificationPreference.value.requestedEnabled)
            succeed()
            model.retryNotificationPreference()
            runCurrent()
            assertEquals(false, model.notificationPreference.value.enabled)
            assertEquals(2, accounts.settingsRequests.size)
        }

    /** A retired graph ignores noncooperative completion and stale preference callbacks. */
    @Test fun clearedParentRejectsLateCompletionAndStaleCallbacks() =
        preferenceTest {
            val held = CompletableDeferred<Unit>()
            accounts.settingsHandler = { _, _ -> withContext(NonCancellable) { held.await() } }
            val model = model()
            runCurrent()
            model.setNotificationsEnabled(false)
            runCurrent()
            assertEquals(1, accounts.settingsRequests.size)
            val before = model.notificationPreference.value
            store.clear()
            held.complete(Unit)
            runCurrent()
            model.setNotificationsEnabled(true)
            model.retryNotificationPreference()
            assertEquals(before, model.notificationPreference.value)
            assertEquals(1, accounts.settingsRequests.size)
        }

    /** Creates the real profile parent with controlled repositories and no reserved parser body. */
    private fun model(): ProfileViewModel =
        ProfileViewModel(
            account.id,
            accounts,
            FakeImageRepository(),
        ).also { store.put("profile", it) }

    /** Supplies the requested settings result while retaining other account fields. */
    private fun succeed() {
        accounts.settingsHandler = { _, settings ->
            val current = checkNotNull(accounts.account.value)
            accounts.account.value = current.copy(settings = settings)
        }
    }

    /** Releases graph work before the controlled scheduler finishes each fixed scenario.
     * @param block One scenario from the frozen preference checklist.
     */
    private fun preferenceTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
            }
        }
}

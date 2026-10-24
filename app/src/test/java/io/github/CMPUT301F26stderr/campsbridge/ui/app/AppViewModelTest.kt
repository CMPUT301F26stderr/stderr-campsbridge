package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.ui.preview.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Startup contracts using the real ViewModel and controlled repository boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Pending identity never reads or invents an account. */
    @Test fun pendingIdentityRemainsInitializing() =
        runTest {
            val identity = FakeIdentityProvider().apply { pending = CompletableDeferred() }
            val accounts = FakeAccountRepository()
            val model = AppViewModel(identity, accounts, FakeLocalAppPreferences())
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                assertEquals(SessionState.Initializing, model.state.value)
                assertEquals(0, accounts.observations)
                assertEquals(0, accounts.writes)
                model.retry()
                runCurrent()
                assertEquals(1, identity.calls)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Missing, active, interrupted, and removed profiles have distinct root states. */
    @Test fun accountAndProgressDetermineRootWithoutWrites() =
        runTest {
            val accounts = FakeAccountRepository()
            val preferences = FakeLocalAppPreferences()
            val model = AppViewModel(FakeIdentityProvider(), accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                assertEquals(SessionState.SetupRequired("account-1"), model.state.value)
                accounts.account.value = sampleAccount()
                runCurrent()
                assertTrue(model.state.value is SessionState.Ready)
                preferences.progress.value = LocalAccountProgress(SetupProgress.IN_PROGRESS)
                runCurrent()
                assertTrue(model.state.value is SessionState.SetupRequired)
                accounts.account.value = sampleAccount().copy(name = "Updated name")
                runCurrent()
                assertTrue(model.state.value is SessionState.SetupRequired)
                preferences.progress.value = LocalAccountProgress(SetupProgress.COMPLETED)
                runCurrent()
                assertEquals("Updated name", (model.state.value as SessionState.Ready).account.name)
                accounts.account.value =
                    sampleAccount().copy(removal = Removal(Instant.EPOCH, "admin"))
                runCurrent()
                assertEquals(SessionState.Unavailable, model.state.value)
                assertEquals(0, accounts.writes)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Account and preference reads must both resolve before startup completes. */
    @Test fun pendingReadsDoNotGuessCompletion() =
        runTest {
            val preferences = FakeLocalAppPreferences().apply { readGate = CompletableDeferred() }
            val model =
                AppViewModel(
                    FakeIdentityProvider(),
                    FakeAccountRepository(sampleAccount()),
                    preferences,
                )
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                assertEquals(SessionState.Initializing, model.state.value)
                preferences.readGate!!.complete(Unit)
                runCurrent()
                assertTrue(model.state.value is SessionState.Ready)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Identity failure can be retried without manufacturing a session. */
    @Test fun identityFailureAndRetry() =
        runTest {
            val identity = FakeIdentityProvider().apply { failure = IOException("offline") }
            val model =
                AppViewModel(
                    identity,
                    FakeAccountRepository(sampleAccount()),
                    FakeLocalAppPreferences(),
                )
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                assertEquals(SessionState.Failure, model.state.value)
                identity.failure = null
                model.retry()
                runCurrent()
                assertTrue(model.state.value is SessionState.Ready)
                assertEquals(2, identity.calls)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** A failed account or preference read is a failure rather than a missing profile. */
    @Test fun readFailuresAreNotSetup() =
        runTest {
            for (accountFailure in listOf(true, false)) {
                val accounts = FakeAccountRepository(sampleAccount())
                val preferences = FakeLocalAppPreferences()
                if (accountFailure) {
                    accounts.readFailure = IOException("read")
                } else {
                    preferences.readFailure = IOException("read")
                }
                val model = AppViewModel(FakeIdentityProvider(), accounts, preferences)
                val store = ViewModelStore().apply { put("app", model) }
                try {
                    runCurrent()
                    assertEquals(SessionState.Failure, model.state.value)
                } finally {
                    store.clear()
                    runCurrent()
                }
            }
        }

    /** Workspace selection is confirmed only after a successful local write. */
    @Test fun workspaceWriteIsConfirmedAndFailureRetainsOldChoice() =
        runTest {
            val identity = FakeIdentityProvider()
            val accounts = FakeAccountRepository(sampleAccount(setOf(Role.ENTRANT, Role.OWNER)))
            val preferences = FakeLocalAppPreferences()
            val model = AppViewModel(identity, accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                preferences.writeGate = CompletableDeferred()
                model.selectWorkspace(Role.OWNER)
                runCurrent()
                assertEquals(Role.ENTRANT, (model.state.value as SessionState.Ready).workspace)
                assertTrue((model.state.value as SessionState.Ready).isSelectingWorkspace)
                model.selectWorkspace(Role.ADMIN)
                model.selectWorkspace(Role.OWNER)
                runCurrent()
                assertEquals(1, preferences.workspaceWrites)
                preferences.writeGate!!.complete(Unit)
                runCurrent()
                assertEquals(Role.OWNER, (model.state.value as SessionState.Ready).workspace)
                preferences.writeFailure = IOException("disk full")
                model.selectWorkspace(Role.ENTRANT)
                runCurrent()
                val failed = model.state.value as SessionState.Ready
                assertEquals(Role.OWNER, failed.workspace)
                assertTrue(failed.selectionFailed)
                assertEquals(1, identity.calls)
                preferences.writeFailure = null
                model.selectWorkspace(Role.ENTRANT)
                runCurrent()
                assertEquals(Role.ENTRANT, (model.state.value as SessionState.Ready).workspace)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** A stored preference or late write never grants a capability the account lacks. */
    @Test fun capabilitiesRevalidatePendingSelection() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount(setOf(Role.ENTRANT, Role.OWNER)))
            val preferences =
                FakeLocalAppPreferences().apply {
                    progress.value = LocalAccountProgress(workspace = Role.ADMIN)
                }
            val model = AppViewModel(FakeIdentityProvider(), accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                assertEquals(Role.ENTRANT, (model.state.value as SessionState.Ready).workspace)
                model.selectWorkspace(Role.ADMIN)
                runCurrent()
                assertEquals(0, preferences.workspaceWrites)
                preferences.writeGate = CompletableDeferred()
                model.selectWorkspace(Role.OWNER)
                runCurrent()
                accounts.account.value = sampleAccount(setOf(Role.ENTRANT))
                runCurrent()
                preferences.writeGate!!.complete(Unit)
                runCurrent()
                assertEquals(Role.ENTRANT, (model.state.value as SessionState.Ready).workspace)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Clearing the owner cancels account subscriptions and pending work. */
    @Test fun clearingStopsObservation() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount())
            val model = AppViewModel(FakeIdentityProvider(), accounts, FakeLocalAppPreferences())
            val store = ViewModelStore().apply { put("app", model) }
            runCurrent()
            assertEquals(1, accounts.activeObservers)
            store.clear()
            runCurrent()
            assertEquals(0, accounts.activeObservers)
            assertFalse(model.state.value is SessionState.Failure)
        }

    /** Clearing during a held write cancels it without publishing a late result or failure. */
    @Test fun clearingCancelsPendingWorkspaceWrite() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount(setOf(Role.ENTRANT, Role.OWNER)))
            val preferences = FakeLocalAppPreferences()
            val model = AppViewModel(FakeIdentityProvider(), accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                model.selectWorkspace(Role.ENTRANT)
                runCurrent()
                assertEquals(0, preferences.workspaceWrites)
                preferences.writeGate = CompletableDeferred()
                model.selectWorkspace(Role.OWNER)
                runCurrent()
                val beforeClear = model.state.value
                assertEquals(1, preferences.workspaceWrites)
                assertTrue((beforeClear as SessionState.Ready).isSelectingWorkspace)
                store.clear()
                runCurrent()
                preferences.writeGate!!.complete(Unit)
                runCurrent()
                assertEquals(beforeClear, model.state.value)
                assertEquals(null, preferences.progress.value.workspace)
                assertEquals(0, accounts.activeObservers)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** A stream failure cancels a pending choice and a retry starts one fresh observation. */
    @Test fun streamFailureCannotBeOverwrittenByPendingSelection() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount(setOf(Role.ENTRANT, Role.OWNER)))
            val preferences = FakeLocalAppPreferences()
            val identity = FakeIdentityProvider()
            val model = AppViewModel(identity, accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                preferences.writeGate = CompletableDeferred()
                model.selectWorkspace(Role.OWNER)
                runCurrent()
                assertEquals(1, preferences.workspaceWrites)
                assertTrue((model.state.value as SessionState.Ready).isSelectingWorkspace)
                accounts.streamFailure.value = IOException("listener disconnected")
                runCurrent()
                assertEquals(SessionState.Failure, model.state.value)
                preferences.writeGate!!.complete(Unit)
                runCurrent()
                assertEquals(SessionState.Failure, model.state.value)
                assertEquals(null, preferences.progress.value.workspace)
                assertEquals(0, accounts.activeObservers)
                accounts.streamFailure.value = null
                model.retry()
                runCurrent()
                assertEquals(Role.ENTRANT, (model.state.value as SessionState.Ready).workspace)
                assertEquals(1, accounts.activeObservers)
                assertEquals(2, identity.calls)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Interrupted setup retains live accessibility preferences without completing the wizard. */
    @Test fun interruptedSetupRetainsUpdatedAccountPreferences() =
        runTest {
            val identity = FakeIdentityProvider()
            val accounts = FakeAccountRepository(sampleAccount())
            val preferences =
                FakeLocalAppPreferences().apply {
                    progress.value = LocalAccountProgress(SetupProgress.IN_PROGRESS)
                }
            val model = AppViewModel(identity, accounts, preferences)
            val store = ViewModelStore().apply { put("app", model) }
            try {
                runCurrent()
                val settings = AccountSettings(fontScale = 1.5, controlScale = 1.25)
                accounts.account.value = sampleAccount().copy(settings = settings)
                runCurrent()
                val setup = model.state.value as SessionState.SetupRequired
                assertEquals("account-1", setup.accountId)
                assertEquals(settings, setup.account?.settings)
                assertEquals(1, identity.calls)
                assertEquals(0, accounts.writes)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

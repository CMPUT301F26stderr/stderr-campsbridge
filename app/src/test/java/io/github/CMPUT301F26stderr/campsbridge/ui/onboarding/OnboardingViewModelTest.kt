package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Behavior contracts agreed before onboarding implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** First launch is read-only and cannot proceed without an explicit public intent. */
    @Test fun firstLaunchAndIntentSelection() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                assertEquals(OnboardingStep.WELCOME, host.model.state.value.step)
                assertEquals(0, host.accounts.writes)
                host.model.next()
                host.model.next()
                assertEquals(OnboardingStep.INTENT, host.model.state.value.step)
                host.model.chooseIntent(Role.ADMIN)
                assertNull(host.model.state.value.draft.intent)
                host.model.chooseIntent(Role.ENTRANT)
                host.model.next()
                assertEquals(OnboardingStep.NAME, host.model.state.value.step)
            } finally {
                host.close()
            }
        }

    /** Name input is validated without repository writes and Back keeps the exact draft. */
    @Test fun validationAndBackPreserveInput() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                host.toName(Role.ENTRANT)
                host.model.editName("   ")
                host.model.next()
                assertTrue(host.model.state.value.nameRequired)
                assertEquals(0, host.accounts.createCalls)
                host.model.editName("  Alex  Chen  ")
                host.model.back()
                host.model.next()
                assertEquals("  Alex  Chen  ", host.model.state.value.draft.name)
                host.model.next()
                runCurrent()
                assertEquals(
                    "Alex  Chen",
                    host.accounts.account.value!!
                        .name,
                )
                assertEquals(OnboardingStep.INTERESTS, host.model.state.value.step)
            } finally {
                host.close()
            }
        }

    /** Profile emissions during creation must not bypass the remaining onboarding steps. */
    @Test fun accountCreationDoesNotCompleteTheRootSession() =
        runTest {
            val host = Host()
            val app = AppViewModel(FakeIdentityProvider(), host.accounts, host.local)
            host.store.put("app", app)
            try {
                runCurrent()
                host.toName(Role.ENTRANT)
                host.model.editName("Alex")
                host.accounts.createGate = CompletableDeferred()
                host.model.next()
                host.model.next()
                host.model.back()
                host.model.editName("Ignored pending edit")
                runCurrent()
                assertTrue(host.model.state.value.isSubmitting)
                assertEquals(1, host.accounts.createCalls)
                assertEquals(SetupProgress.IN_PROGRESS, host.local.progress.value.setup)
                assertEquals("Alex", host.model.state.value.draft.name)
                host.accounts.createGate!!.complete(Unit)
                runCurrent()
                assertTrue(app.state.value is SessionState.SetupRequired)
                assertEquals(OnboardingStep.INTERESTS, host.model.state.value.step)
            } finally {
                host.close()
            }
        }

    /** Failed and uncertain acknowledgements retain input and retry the same identity. */
    @Test fun failedAndUncertainCreationCanRetry() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                host.toName(Role.ENTRANT)
                host.model.editName("Alex")
                host.accounts.createFailure = IOException("offline")
                host.model.next()
                runCurrent()
                assertTrue(host.model.state.value.failed)
                assertEquals("Alex", host.model.state.value.draft.name)
                assertEquals(OnboardingStep.NAME, host.model.state.value.step)
                host.accounts.createFailure = null
                host.accounts.createAcknowledgementFailure = IOException("lost acknowledgement")
                host.model.next()
                runCurrent()
                val created = host.accounts.account.value!!
                assertTrue(host.model.state.value.failed)
                host.accounts.createAcknowledgementFailure = null
                host.model.next()
                runCurrent()
                assertEquals(created, host.accounts.account.value)
                assertEquals(
                    listOf("account-1", "account-1", "account-1"),
                    host.accounts.createdIds,
                )
                assertEquals(OnboardingStep.INTERESTS, host.model.state.value.step)
            } finally {
                host.close()
            }
        }

    /** Reopening name input updates the existing profile without losing its contact fields. */
    @Test fun editingSubmittedNamePreservesExistingProfileFacts() =
        runTest {
            val host = Host(existing = true)
            try {
                runCurrent()
                assertEquals(OnboardingStep.INTERESTS, host.model.state.value.step)
                host.model.back()
                host.model.editName("Taylor")
                host.model.next()
                runCurrent()
                val record = host.accounts.account.value!!
                assertEquals("Taylor", record.name)
                assertEquals(sampleAccount().email, record.email)
                assertEquals(0, host.accounts.createCalls)
                assertEquals(1, host.accounts.profileWrites)
            } finally {
                host.close()
            }
        }

    /** Saving optional interests completes setup only after their repository write succeeds. */
    @Test fun preferenceFailureRetainsChoicesAndRetryCompletes() =
        runTest {
            val host = Host(existing = true)
            try {
                runCurrent()
                val interests = Preferences(tags = setOf("lakeside", "stargazing"))
                host.model.editPreferences(interests)
                host.accounts.preferenceFailure = IOException("offline")
                host.model.next()
                runCurrent()
                assertFalse(host.model.state.value.completed)
                assertTrue(host.model.state.value.failed)
                assertEquals(interests, host.model.state.value.draft.preferences)
                assertEquals(SetupProgress.IN_PROGRESS, host.local.progress.value.setup)
                host.accounts.preferenceFailure = null
                host.model.next()
                runCurrent()
                assertEquals(
                    interests,
                    host.accounts.account.value!!
                        .preferences,
                )
                assertEquals(SetupProgress.COMPLETED, host.local.progress.value.setup)
                assertEquals(Role.ENTRANT, host.local.progress.value.workspace)
                assertTrue(host.model.state.value.completed)
            } finally {
                host.close()
            }
        }

    /** Skip discards unsubmitted optional choices while retaining existing persisted interests. */
    @Test fun skipDoesNotSaveDraftPreferences() =
        runTest {
            val host = Host(existing = true)
            try {
                runCurrent()
                val original =
                    host.accounts.account.value!!
                        .preferences
                host.model.editPreferences(Preferences(tags = setOf("tent")))
                host.model.skipInterests()
                runCurrent()
                assertEquals(
                    original,
                    host.accounts.account.value!!
                        .preferences,
                )
                assertEquals(0, host.accounts.preferenceWrites)
                assertTrue(host.model.state.value.completed)
            } finally {
                host.close()
            }
        }

    /** Owner choice grants no capability until the explicit hosting completion succeeds. */
    @Test fun ownerActivationAndRetry() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                host.toName(Role.OWNER)
                host.model.editName("Host")
                host.model.next()
                runCurrent()
                assertEquals(OnboardingStep.OWNER, host.model.state.value.step)
                assertEquals(
                    setOf(Role.ENTRANT),
                    host.accounts.account.value!!
                        .roles,
                )
                host.accounts.ownerFailure = IOException("offline")
                host.model.next()
                runCurrent()
                assertTrue(host.model.state.value.failed)
                assertEquals(SetupProgress.IN_PROGRESS, host.local.progress.value.setup)
                host.accounts.ownerFailure = null
                host.accounts.ownerGate = CompletableDeferred()
                host.model.next()
                runCurrent()
                assertFalse(host.model.state.value.completed)
                host.accounts.ownerGate!!.complete(Unit)
                runCurrent()
                assertEquals(
                    setOf(Role.ENTRANT, Role.OWNER),
                    host.accounts.account.value!!
                        .roles,
                )
                assertEquals(Role.OWNER, host.local.progress.value.workspace)
                assertTrue(host.model.state.value.completed)
            } finally {
                host.close()
            }
        }

    /** A local completion failure never reports success and can be retried. */
    @Test fun completionFailureCanRetry() =
        runTest {
            val host = Host(existing = true)
            try {
                runCurrent()
                host.local.setupFailureOn = SetupProgress.COMPLETED
                host.local.setupFailure = IOException("disk full")
                host.model.skipInterests()
                runCurrent()
                assertFalse(host.model.state.value.completed)
                assertTrue(host.model.state.value.failed)
                host.local.setupFailure = null
                host.model.skipInterests()
                runCurrent()
                assertTrue(host.model.state.value.completed)
            } finally {
                host.close()
            }
        }

    /** Failed initialization is retried as a read without creating any profile. */
    @Test fun loadFailureAndRetry() =
        runTest {
            val accounts = FakeAccountRepository().apply { readFailure = IOException("offline") }
            val host = Host(accounts = accounts)
            try {
                runCurrent()
                assertTrue(host.model.state.value.failed)
                accounts.readFailure = null
                host.model.retryLoad()
                runCurrent()
                assertFalse(host.model.state.value.failed)
                assertFalse(host.model.state.value.isLoading)
                assertEquals(0, accounts.writes)
            } finally {
                host.close()
            }
        }

    /** Saved-state recreation restores the current step and unsubmitted field values. */
    @Test fun savedStateRestoresDraftWithoutReplayingWrites() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                host.toName(Role.OWNER)
                host.model.editName("  Unsubmitted name  ")
                val restored =
                    SavedStateHandle(host.saved.keys().associateWith { host.saved.get<Any?>(it) })
                host.store.clear()
                val model =
                    OnboardingViewModel(
                        "account-1",
                        host.accounts,
                        host.local,
                        restored,
                        ::fixtureName,
                    )
                host.store.put("restored", model)
                runCurrent()
                assertEquals(OnboardingStep.NAME, model.state.value.step)
                assertEquals(Role.OWNER, model.state.value.draft.intent)
                assertEquals("  Unsubmitted name  ", model.state.value.draft.name)
                assertEquals(0, host.accounts.writes)
            } finally {
                host.close()
            }
        }

    /** A cold restart can reconstruct owner setup from confirmed profile and local intent. */
    @Test fun coldRestartResumesOwnerWithoutCreatingAnotherProfile() =
        runTest {
            val host = Host(existing = true, intent = Role.OWNER)
            try {
                runCurrent()
                assertEquals(OnboardingStep.OWNER, host.model.state.value.step)
                assertEquals(sampleAccount().name, host.model.state.value.draft.name)
                host.model.next()
                runCurrent()
                assertEquals(0, host.accounts.createCalls)
                assertTrue(host.model.state.value.completed)
            } finally {
                host.close()
            }
        }

    /** Clearing during a suspended action propagates cancellation without finishing setup. */
    @Test fun clearingCancelsPendingCreation() =
        runTest {
            val host = Host()
            runCurrent()
            host.toName(Role.ENTRANT)
            host.model.editName("Alex")
            host.accounts.createGate = CompletableDeferred()
            host.model.next()
            runCurrent()
            host.store.clear()
            host.accounts.createGate!!.complete(Unit)
            runCurrent()
            assertNull(host.accounts.account.value)
            assertEquals(SetupProgress.IN_PROGRESS, host.local.progress.value.setup)
            assertFalse(host.model.state.value.failed)
            assertFalse(host.model.state.value.completed)
        }

    /** Missing reserved validation leaves the action pending without saving a profile. */
    @Test fun missingValidatorNeverPretendsToAcceptAName() =
        runTest {
            val accounts = FakeAccountRepository()
            val store = ViewModelStore()
            val model =
                OnboardingViewModel(
                    "account-1",
                    accounts,
                    FakeLocalAppPreferences(),
                    SavedStateHandle(),
                )
            store.put("onboarding", model)
            try {
                runCurrent()
                model.next()
                model.chooseIntent(Role.ENTRANT)
                model.next()
                model.editName("Alex")
                model.next()
                runCurrent()
                assertTrue(model.state.value.nameValidationPending)
                assertEquals(OnboardingStep.NAME, model.state.value.step)
                assertEquals(0, accounts.writes)
            } finally {
                store.clear()
            }
        }

    /** Both preference sets survive saved-state restoration without a persistence operation. */
    @Test fun savedStateRestoresInterestsWithoutWriting() =
        runTest {
            val host = Host(existing = true)
            try {
                runCurrent()
                val draft = Preferences(setOf("lakeside", "quiet"), setOf("water"))
                host.model.editPreferences(draft)
                val restored =
                    SavedStateHandle(host.saved.keys().associateWith { host.saved.get<Any?>(it) })
                host.store.clear()
                val model =
                    OnboardingViewModel(
                        "account-1",
                        host.accounts,
                        host.local,
                        restored,
                        ::fixtureName,
                    )
                host.store.put("restored", model)
                runCurrent()
                assertEquals(OnboardingStep.INTERESTS, model.state.value.step)
                assertEquals(draft, model.state.value.draft.preferences)
                assertEquals(0, host.accounts.writes)
            } finally {
                host.close()
            }
        }

    /** A failed setup-marker write prevents profile creation and premature readiness. */
    @Test fun progressWriteMustSucceedBeforeProfileCreation() =
        runTest {
            val host = Host()
            try {
                runCurrent()
                host.toName(Role.ENTRANT)
                host.model.editName("Alex")
                host.local.setupFailureOn = SetupProgress.IN_PROGRESS
                host.local.setupFailure = IOException("disk full")
                host.model.next()
                runCurrent()
                assertEquals(0, host.accounts.createCalls)
                assertEquals(OnboardingStep.NAME, host.model.state.value.step)
                assertTrue(host.model.state.value.failed)
                assertEquals(SetupProgress.NOT_STARTED, host.local.progress.value.setup)
            } finally {
                host.close()
            }
        }

    /** Owns injected dependencies and explicitly releases all test ViewModels.
     * @param existing Whether a confirmed profile already exists.
     * @param intent Previously selected starting intent.
     * @param accounts Controlled repository override.
     */
    private class Host(
        existing: Boolean = false,
        intent: Role = Role.ENTRANT,
        val accounts: FakeAccountRepository =
            FakeAccountRepository(if (existing) sampleAccount() else null),
    ) : AutoCloseable {
        val local =
            FakeLocalAppPreferences().apply {
                if (existing) {
                    progress.value =
                        LocalAccountProgress(SetupProgress.IN_PROGRESS, intent)
                }
            }
        val saved = SavedStateHandle()
        val store = ViewModelStore()
        val model = OnboardingViewModel("account-1", accounts, local, saved, ::fixtureName)

        init {
            accounts.allowWrites = true
            store.put("onboarding", model)
        }

        /** Follows only public actions to the name step.
         * @param role Requested starting workspace.
         */
        fun toName(role: Role) {
            model.next()
            model.chooseIntent(role)
            model.next()
        }

        /** Cancels owned work after every test. */
        override fun close() {
            store.clear()
        }
    }
}

/** Supplies only the explicit name fixtures used by controller tests, not a production parser.
 * @param input Exact fixture input.
 */
private fun fixtureName(input: String): DisplayNameInput =
    when (input) {
        "   " -> DisplayNameInput.Required
        "  Alex  Chen  " -> DisplayNameInput.Valid("Alex  Chen")
        "Alex" -> DisplayNameInput.Valid("Alex")
        "Taylor" -> DisplayNameInput.Valid("Taylor")
        "Host" -> DisplayNameInput.Valid("Host")
        else -> error("Add the explicit fixture result for: $input")
    }

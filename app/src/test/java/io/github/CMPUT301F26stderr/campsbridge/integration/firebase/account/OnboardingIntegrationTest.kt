package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.time.Duration.Companion.seconds

/** Verifies new onboarding orchestration against actual account transactions and listeners. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingIntegrationTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    @get:Rule val main = MainDispatcherRule()

    /** Profile creation cannot exit setup; owner completion awaits the persisted capability. */
    @Test fun ownerSetupConfirmsCapabilityBeforeEnteringWorkspace() =
        runTest(timeout = 60.seconds) {
            val actor = firebase.actor()
            val accounts = actor.container.accounts
            val local = FakeLocalAppPreferences()
            val identity = FakeIdentityProvider().apply { pending = CompletableDeferred(actor.id) }
            val app = AppViewModel(identity, accounts, local)
            val model =
                OnboardingViewModel(actor.id, accounts, local, SavedStateHandle()) {
                    DisplayNameInput.Valid("Host")
                }
            val store =
                ViewModelStore().apply {
                    put("app", app)
                    put("onboarding", model)
                }
            try {
                model.state.first { !it.isLoading }
                model.next()
                model.chooseIntent(Role.OWNER)
                model.next()
                model.editName("Host")
                model.next()
                model.state.first { it.step == OnboardingStep.OWNER && !it.isSubmitting }
                app.state.first { it is SessionState.SetupRequired && it.account != null }
                assertEquals(SetupProgress.IN_PROGRESS, local.observe(actor.id).first().setup)
                assertEquals(setOf(Role.ENTRANT), accounts.get(actor.id)!!.roles)
                model.next()
                model.state.first { it.completed }
                val ready = app.state.first { it is SessionState.Ready } as SessionState.Ready
                assertEquals(Role.OWNER, ready.workspace)
                assertTrue(Role.OWNER in ready.account.roles)
                assertEquals(Role.OWNER, local.observe(actor.id).first().workspace)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Entrant setup stores interests without changing the account creation facts. */
    @Test fun entrantInterestsAreStoredUnderTheEstablishedIdentity() =
        runTest(timeout = 60.seconds) {
            val actor = firebase.actor()
            val accounts = actor.container.accounts
            val local = FakeLocalAppPreferences()
            val model =
                OnboardingViewModel(actor.id, accounts, local, SavedStateHandle()) {
                    DisplayNameInput.Valid("Alex")
                }
            val store = ViewModelStore().apply { put("onboarding", model) }
            try {
                model.state.first { !it.isLoading }
                model.next()
                model.chooseIntent(Role.ENTRANT)
                model.next()
                model.editName("Alex")
                model.next()
                model.state.first { it.step == OnboardingStep.INTERESTS && !it.isSubmitting }
                val created = accounts.get(actor.id)!!
                val interests = Preferences(tags = setOf("lakeside", "stargazing"))
                model.editPreferences(interests)
                model.next()
                model.state.first { it.completed }
                assertEquals(created.copy(preferences = interests), accounts.get(actor.id))
                assertEquals(SetupProgress.COMPLETED, local.observe(actor.id).first().setup)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

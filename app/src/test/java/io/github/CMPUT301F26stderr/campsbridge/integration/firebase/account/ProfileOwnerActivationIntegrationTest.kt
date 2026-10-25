package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.account

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.time.Duration.Companion.seconds

/** Verifies hosting activation through the actual authenticated account repository and listener. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileOwnerActivationIntegrationTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    @get:Rule val main = MainDispatcherRule()

    /** The confirmed capability changes roles only and does not create a replacement account. */
    @Test fun existingEntrantBecomesOwnerWithEveryOtherFieldPreserved() =
        runTest(timeout = 60.seconds) {
            val actor = firebase.registeredActor(Role.ENTRANT)
            val accounts = actor.container.accounts
            val original = accounts.get(actor.id)!!
            val model = ProfileViewModel(actor.id, accounts, actor.container.images)
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                model.state.first { it is ProfileUiState.Content }
                model.enableOwner()
                model.ownerWorkspaceRequested.first { it }
                assertEquals(
                    original.copy(roles = original.roles + Role.OWNER),
                    accounts.get(actor.id),
                )
                assertNull(model.completion.value)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

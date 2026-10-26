package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Independent account observers may confirm owner capability at different times. */
class OwnerWorkspaceHandoffTest {
    @get:Rule val compose = createComposeRule()
    private val original = sampleAccount()
    private val account = original.copy(roles = original.roles + Role.OWNER)
    private val accounts = FakeAccountRepository(account)
    private val store = ViewModelStore()

    /** Releases the profile/image observers created by this actual route. */
    @After fun close() {
        compose.runOnIdle { store.clear() }
    }

    /** The automatic selection remains unconsumed until the root observer confirms the capability. */
    @Test fun profileConfirmationWaitsForRootCapabilityBeforeSelection() {
        val model = ProfileViewModel(original.id, accounts, FakeImageRepository())
        store.put("profile", model)
        var session by mutableStateOf(SessionState.Ready(original, Role.ENTRANT))
        var finished by mutableStateOf(false)
        var selections = 0
        var navigations = 0
        compose.setContent {
            CampsbridgeTheme {
                if (!finished) {
                    OwnerIntroductionRoute(model, session, { selections++ }, {
                        navigations++
                        finished =
                            true
                    }, {})
                }
            }
        }
        compose.onNode(hasText("Start hosting") and hasClickAction()).performClick()
        compose.onNode(hasText("Start hosting") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(0, selections)
            assertEquals(0, accounts.writes)
            session = session.copy(account = account)
        }
        compose.waitUntil(5_000) { selections == 1 }
        compose.runOnIdle { session = session.copy(workspace = Role.OWNER) }
        compose.waitUntil(5_000) { finished }
        compose.runOnIdle {
            assertEquals(1, selections)
            assertEquals(1, navigations)
            assertFalse(model.ownerWorkspaceRequested.value)
        }
    }
}

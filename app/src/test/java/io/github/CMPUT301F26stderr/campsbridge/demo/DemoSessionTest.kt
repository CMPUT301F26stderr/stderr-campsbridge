package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verifies the actual root controller against isolated offline scenario dependencies. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DemoSessionTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Normal debug startup opens a populated profile rather than an authentication failure. */
    @Test fun defaultScenarioStartsReadyWithoutExternalServices() =
        runTest {
            val host = DemoHostViewModel(files.root)
            val store = ViewModelStore().apply { put("host", host) }
            try {
                runCurrent()
                val active = host.session.filterNotNull().first()
                val ready = active.model.state.first { it is SessionState.Ready } as SessionState.Ready
                assertEquals("Alex Chen", ready.account.name)
                assertEquals(Role.ENTRANT, ready.workspace)
                assertNull(
                    active.services.repositories.images.get(
                        ImageTarget(
                            ImageTargetType.ACCOUNT,
                            "demo-account",
                        ),
                    ),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Reset discards local edits and an owner starts in the confirmed owner workspace. */
    @Test fun resetCreatesCleanGraphsAndNewAccountHasNoProfile() =
        runTest {
            val host = DemoHostViewModel(files.root)
            val store = ViewModelStore().apply { put("host", host) }
            try {
                runCurrent()
                val initial = host.session.filterNotNull().first()
                initial.services.repositories.accounts.updateProfile(
                    "demo-account",
                    ProfileUpdate("Edited", null, null),
                )
                runCurrent()
                host.reset()
                runCurrent()
                val reset = host.session.filterNotNull().first { it !== initial }
                assertNotSame(initial, reset)
                assertEquals(
                    "Alex Chen",
                    (reset.model.state.first { it is SessionState.Ready } as SessionState.Ready).account.name,
                )
                host.select(DemoScenario.OWNER)
                runCurrent()
                val hosting = host.session.filterNotNull().first { it.scenario == DemoScenario.OWNER }
                val owner = hosting.model.state.first { it is SessionState.Ready } as SessionState.Ready
                assertEquals(Role.OWNER, owner.workspace)
                assertTrue(Role.ENTRANT in owner.account.roles)
                assertTrue(Role.OWNER in owner.account.roles)
                host.select(DemoScenario.NEW_ACCOUNT)
                runCurrent()
                assertEquals(
                    SessionState.SetupRequired("demo-new"),
                    host.session
                        .filterNotNull()
                        .first {
                            it.scenario == DemoScenario.NEW_ACCOUNT
                        }.model.state
                        .first { it is SessionState.SetupRequired },
                )
                assertNull(
                    host.session.value!!
                        .services.repositories.accounts
                        .get("demo-new"),
                )
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

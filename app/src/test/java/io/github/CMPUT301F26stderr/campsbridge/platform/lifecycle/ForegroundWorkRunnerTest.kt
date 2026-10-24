package io.github.CMPUT301F26stderr.campsbridge.platform.lifecycle

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.app.workSession
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Foreground/account ownership, without depending on a real Android lifecycle clock. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundWorkRunnerTest {
    /** Account/capability changes replace work; background and incomplete sessions cancel it. */
    @Test fun oneSessionOwnsForegroundWork() =
        runTest {
            val work = RecordingWork()
            val account = WorkSession("first", setOf(Role.ENTRANT))
            val sessions = MutableStateFlow<WorkSession?>(null)
            val foreground = MutableStateFlow(false)
            val runner =
                backgroundScope.launch {
                    ForegroundWorkRunner(work) { throw it }.run(sessions, foreground)
                }
            runCurrent()
            sessions.value = account
            runCurrent()
            assertEquals(emptyList<WorkSession>(), work.started)
            foreground.value = true
            runCurrent()
            assertEquals(listOf(account), work.active)
            sessions.value = account.copy()
            runCurrent()
            assertEquals(1, work.started.size)
            val owner = account.copy(roles = setOf(Role.ENTRANT, Role.OWNER))
            sessions.value = owner
            runCurrent()
            assertEquals(listOf(owner), work.active)
            foreground.value = false
            runCurrent()
            assertEquals(emptyList<WorkSession>(), work.active)
            foreground.value = true
            runCurrent()
            val other = WorkSession("second", setOf(Role.ENTRANT))
            sessions.value = other
            runCurrent()
            assertEquals(listOf(other), work.active)
            assertEquals(1, work.maximumActive)
            sessions.value = null
            runCurrent()
            assertEquals(emptyList<WorkSession>(), work.active)
            runner.cancelAndJoin()
        }

    /** Only Ready starts work, and workspace changes retain all account capabilities. */
    @Test fun workSessionUsesReadyAccountCapabilities() {
        val account = CampingFixtures.account().copy(roles = setOf(Role.ENTRANT, Role.OWNER))
        listOf(
            SessionState.Initializing,
            SessionState.Failure,
            SessionState.Unavailable,
            SessionState.SetupRequired(account.id),
            SessionState.SetupRequired(account.id, account),
        ).forEach { assertNull(it.workSession()) }
        val expected = WorkSession(account.id, account.roles)
        assertEquals(expected, SessionState.Ready(account, Role.ENTRANT).workSession())
        assertEquals(expected, SessionState.Ready(account, Role.OWNER).workSession())
    }
}

/** Long-running fake that records ownership and observes cancellation. */
private class RecordingWork : PendingWork {
    val started = mutableListOf<WorkSession>()
    val active = mutableListOf<WorkSession>()
    var maximumActive = 0

    /** Holds ownership until the runner cancels this account.
     * @param session Active account/capabilities.
     * @param onFailure Unused because this fake has no persistence failures.
     */
    override suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    ) {
        started.add(session)
        active.add(session)
        maximumActive = maxOf(maximumActive, active.size)
        try {
            awaitCancellation()
        } finally {
            active.remove(session)
        }
    }
}

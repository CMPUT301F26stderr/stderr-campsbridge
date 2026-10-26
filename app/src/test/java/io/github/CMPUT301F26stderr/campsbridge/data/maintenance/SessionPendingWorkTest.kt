package io.github.CMPUT301F26stderr.campsbridge.data.maintenance

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Fixed contracts for due boundaries, serial passes, retry isolation and cancellation. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionPendingWorkTest {
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val session = WorkSession("owner", setOf(Role.ENTRANT, Role.OWNER))

    /** Future work runs at, rather than before, its deadline and receives the full session. */
    @Test fun deadlineIsExactAndDiscoveryIsSessionScoped() =
        runTest {
            val clock = MutableClock(now)
            val operation = PendingOperation.Draw("due", now.plusSeconds(10))
            val source = FakeWorkSource(listOf(operation))
            val worker =
                backgroundScope.launch {
                    SessionPendingWork(
                        source,
                        clock,
                    ).run(session) { throw it }
                }
            runCurrent()
            assertEquals(session, source.session)
            assertTrue(source.calls.isEmpty())
            advance(clock, 9999)
            assertTrue(source.calls.isEmpty())
            advance(clock, 1)
            assertEquals(listOf(operation), source.calls)
            worker.cancelAndJoin()
            assertEquals(0, source.observers)
        }

    /** A burst replaces pending snapshots while active execution remains serial and uncancelled. */
    @Test fun changesConflateWhileOnePassIsInFlight() =
        runTest {
            val clock = MutableClock(now)
            val first = PendingOperation.Draw("first", now)
            val latest = PendingOperation.Draw("20", now)
            val source = FakeWorkSource(listOf(first))
            val release = CompletableDeferred<Unit>()
            source.action = { operation ->
                if (operation == first) release.await()
                source.remove(operation)
            }
            val worker =
                backgroundScope.launch {
                    SessionPendingWork(
                        source,
                        clock,
                    ).run(session) { throw it }
                }
            runCurrent()
            for (id in 1..20) {
                source.pending.value = listOf(PendingOperation.Draw(id.toString(), now))
                runCurrent()
            }
            assertEquals(listOf(first), source.calls)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(first, latest), source.calls)
            assertEquals(1, source.maximumActive)
            worker.cancelAndJoin()
        }

    /** Failed work does not block another job, and repeated failures back off up to one minute. */
    @Test fun failuresAreIndependentAndRetriesAreBounded() =
        runTest {
            val clock = MutableClock(now)
            val bad = PendingOperation.Draw("bad", now)
            val good = PendingOperation.Draw("good", now)
            val source = FakeWorkSource(listOf(bad, good))
            val attempts = mutableListOf<Long>()
            val failures = mutableListOf<Exception>()
            source.action = { operation ->
                if (operation == bad) {
                    attempts.add(testScheduler.currentTime)
                    throw IOException("offline")
                }
                source.remove(operation)
            }
            val worker =
                backgroundScope.launch {
                    SessionPendingWork(source, clock).run(session, failures::add)
                }
            runCurrent()
            assertEquals(listOf(bad, good), source.calls)
            advance(clock, 999)
            assertEquals(listOf(0L), attempts)
            advance(clock, 1)
            for (millis in listOf(2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)) {
                advance(clock, millis)
            }
            assertEquals(
                listOf(0L, 1000L, 3000L, 7000L, 15000L, 31000L, 63000L, 123000L, 183000L),
                attempts,
            )
            assertEquals(attempts.size, failures.size)
            assertTrue(failures.all { it is IOException && it.message == "offline" })
            assertEquals(1, source.calls.count { it == good })
            worker.cancelAndJoin()
        }

    /** Discovery failure is reported and retried without a tight loop or lost future work. */
    @Test fun failedDiscoveryResubscribesWithBackoff() =
        runTest {
            val clock = MutableClock(now)
            val source = FakeWorkSource(listOf(PendingOperation.Draw("ready", now)))
            source.failObservations = 1
            val failures = mutableListOf<Exception>()
            val worker =
                backgroundScope.launch {
                    SessionPendingWork(source, clock).run(session, failures::add)
                }
            runCurrent()
            assertEquals(1, failures.size)
            assertTrue(source.calls.isEmpty())
            advance(clock, 999)
            assertEquals(1, source.starts)
            advance(clock, 1)
            assertEquals(2, source.starts)
            assertEquals(1, source.calls.size)
            worker.cancelAndJoin()
            assertEquals(0, source.observers)
        }

    /** Cancellation interrupts execution, releases subscriptions and preserves durable work. */
    @Test fun cancellationReleasesExecutionAndDiscovery() =
        runTest {
            val clock = MutableClock(now)
            val operation = PendingOperation.Draw("pending", now)
            val source = FakeWorkSource(listOf(operation))
            var cancelled = false
            val failures = mutableListOf<Exception>()
            source.action = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val worker =
                backgroundScope.launch {
                    SessionPendingWork(source, clock).run(session, failures::add)
                }
            runCurrent()
            assertEquals(1, source.active)
            worker.cancelAndJoin()
            assertTrue(cancelled)
            assertEquals(0, source.active)
            assertEquals(0, source.observers)
            assertEquals(listOf(operation), source.pending.value)
            assertTrue(failures.isEmpty())
        }

    /** Advances both application and coroutine time by the same deterministic interval.
     * @param clock Application deadline clock.
     * @param millis Elapsed duration.
     */
    private fun TestScope.advance(
        clock: MutableClock,
        millis: Long,
    ) {
        clock.advance(Duration.ofMillis(millis))
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }
}

/** Narrow durable-work fake; unrelated repositories are unnecessary for orchestration tests.
 * @param initial Initial discovered operations.
 */
private class FakeWorkSource(
    initial: List<PendingOperation>,
) : WorkSource {
    val pending = MutableStateFlow(initial)
    val calls = mutableListOf<PendingOperation>()
    var action: (suspend (PendingOperation) -> Unit)? = null
    var session: WorkSession? = null
    var observers = 0
    var starts = 0
    var active = 0
    var maximumActive = 0
    var failObservations = 0

    /** Tracks active discovery and supports a controlled subscription failure.
     * @param session Account passed to the coordinator.
     */
    override fun observe(session: WorkSession) =
        flow {
            this@FakeWorkSource.session = session
            starts++
            observers++
            try {
                if (failObservations > 0) {
                    failObservations--
                    throw IOException("discovery offline")
                }
                emitAll(pending)
            } finally {
                observers--
            }
        }

    /** Tracks concurrency and executes the controlled persistence action.
     * @param operation Discovered operation being attempted.
     */
    override suspend fun execute(operation: PendingOperation) {
        calls.add(operation)
        active++
        maximumActive = maxOf(maximumActive, active)
        try {
            val execute = action
            if (execute == null) remove(operation) else execute(operation)
        } finally {
            active--
        }
    }

    /** Simulates the durable record disappearing on successful processing.
     * @param operation Completed work identity.
     */
    fun remove(operation: PendingOperation) {
        pending.value = pending.value.filter { it.key != operation.key }
    }
}

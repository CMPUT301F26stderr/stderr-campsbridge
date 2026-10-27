package io.github.CMPUT301F26stderr.campsbridge.ui.common.time

import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Deadline waits use controlled clocks and coroutine time without real sleeps or writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeUpdatesTest {
    private val start = Instant.parse("2027-05-01T12:00:00Z")

    /** Future deadlines refresh at the actual clock boundary, not one millisecond before it. */
    @Test fun waitsUntilTheInclusiveDeadline() =
        runTest {
            val clock = MutableClock(start)
            var refreshed = false
            launch {
                awaitDeadline(clock, start.plusSeconds(1))
                refreshed = true
            }
            runCurrent()
            assertFalse(refreshed)
            clock.advance(Duration.ofMillis(999))
            advanceTimeBy(999)
            runCurrent()
            assertFalse(refreshed)
            clock.advance(Duration.ofMillis(1))
            advanceTimeBy(1)
            runCurrent()
            assertTrue(refreshed)
        }

    /** Already reached and past deadlines do not wait for another timer tick. */
    @Test fun reachedDeadlinesReturnImmediately() =
        runTest {
            val clock = MutableClock(start)
            var refreshed = false
            launch {
                awaitDeadline(clock, start.minusNanos(1))
                awaitDeadline(clock, start)
                refreshed = true
            }
            runCurrent()
            assertTrue(refreshed)
        }

    /** Backward clock changes require another bounded wait instead of publishing early state. */
    @Test fun clockRollbackRechecksTheActualDeadline() =
        runTest {
            val clock = MutableClock(start)
            var refreshed = false
            launch {
                awaitDeadline(clock, start.plusSeconds(1))
                refreshed = true
            }
            runCurrent()
            clock.advance(Duration.ofSeconds(-2))
            advanceTimeBy(1000)
            runCurrent()
            assertFalse(refreshed)
            clock.advance(Duration.ofSeconds(3))
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(refreshed)
        }

    /** Submillisecond differences suspend without a zero-delay loop. */
    @Test fun submillisecondDeadlineDoesNotRefreshEarly() =
        runTest {
            val clock = MutableClock(start)
            var refreshed = false
            launch {
                awaitDeadline(clock, start.plusNanos(1))
                refreshed = true
            }
            runCurrent()
            assertFalse(refreshed)
            clock.advance(Duration.ofNanos(1))
            advanceTimeBy(1)
            runCurrent()
            assertTrue(refreshed)
        }

    /** Retiring the owner cancels a pending wait and prevents its later refresh. */
    @Test fun cancellationStopsThePendingRefresh() =
        runTest {
            val clock = MutableClock(start)
            var refreshed = false
            val pending =
                launch {
                    awaitDeadline(clock, start.plusSeconds(1))
                    refreshed = true
                }
            runCurrent()
            pending.cancel()
            clock.advance(Duration.ofSeconds(2))
            advanceTimeBy(2000)
            runCurrent()
            assertFalse(refreshed)
        }
}

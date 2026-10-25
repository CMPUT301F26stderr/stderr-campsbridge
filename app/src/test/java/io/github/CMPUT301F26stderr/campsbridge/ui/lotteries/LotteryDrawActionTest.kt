package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
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
import java.time.Duration

/** Owner fallback uses the existing draw operation; observed parent completion owns results. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDrawActionTest {
    @get:Rule val main = MainDispatcherRule()
    private val parent = CampingFixtures.lottery()
    private val clock = MutableClock(parent.configuration.registrationClosesAt)
    private val lotteries = FakeLotteryRepository(listOf(parent))
    private val store = ViewModelStore()

    /** Rechecks the exact inclusive close at the action boundary without a prior display refresh. */
    @Test fun manualDrawStartsAtExactClose() =
        withOwner {
            clock.advance(Duration.ofNanos(-1))
            val model = model()
            runCurrent()
            assertFalse(content(model).canDraw)
            model.draw()
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
            clock.advance(Duration.ofNanos(1))
            model.draw()
            assertTrue(content(model).isDrawing)
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
        }

    /** Only an active owned published undrawn parent is actionable. */
    @Test fun draftCancelledAndCompletedParentsRejectDraw() =
        withOwner {
            for (record in listOf(
                parent.copy(publishedAt = null),
                parent.copy(cancellation = Cancellation(clock.instant(), "owner")),
                parent.copy(drawnAt = clock.instant()),
            )) {
                lotteries.records.value = listOf(record)
                val model = model()
                runCurrent()
                assertFalse(content(model).canDraw)
                model.draw()
                runCurrent()
                assertTrue(lotteries.events.isEmpty())
                store.clear()
                runCurrent()
            }
        }

    /** Explicit entrant context cannot invoke owner fallback even for a matching account ID. */
    @Test fun entrantCannotInvokeTheOwnerCommand() =
        withOwner {
            val model = model(LotteryContext.ENTRANT)
            runCurrent()
            assertFalse(content(model).canDraw)
            model.draw()
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
        }

    /** One synchronous guard covers repeated clicks while the repository acknowledgement is held. */
    @Test fun heldCommandUsesTheFixedSavedIdentityOnce() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { gate.await() }
            val model = model()
            runCurrent()
            model.draw()
            model.draw()
            assertTrue(content(model).isDrawing)
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            model.setParticipantsVisible(true)
            model.setParticipantsVisible(false)
            assertTrue(content(model).isDrawing)
            gate.complete(DrawResult.COMPLETED)
            runCurrent()
            assertFalse(content(model).isDrawing)
        }

    /** Acknowledgement never supplies a synthetic drawnAt or completed lifecycle. */
    @Test fun acknowledgedCommandStillAwaitsObservedCompletion() =
        withOwner {
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertEquals(null, content(model).lottery.drawnAt)
            assertEquals(LotteryLifecycle.DRAW_PENDING, content(model).lifecycle)
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            assertEquals(LotteryLifecycle.BOOKING_OPEN, content(model).lifecycle)
            assertFalse(content(model).canDraw)
        }

    /** Already-completed acknowledgement follows the same observed-parent boundary. */
    @Test fun alreadyDrawnResponseDoesNotInventCompletion() =
        withOwner {
            lotteries.drawHandler = { DrawResult.ALREADY_DRAWN }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            assertEquals(null, content(model).lottery.drawnAt)
            assertEquals(LotteryLifecycle.DRAW_PENDING, content(model).lifecycle)
            assertFalse(content(model).drawFailed)
        }

    /** Completion can arrive before acknowledgement without hiding real command progress. */
    @Test fun completionBeforeAcknowledgementRetainsTheActualPendingCall() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { gate.await() }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            assertEquals(LotteryLifecycle.BOOKING_OPEN, content(model).lifecycle)
            assertTrue(content(model).isDrawing)
            assertFalse(content(model).canDraw)
            model.draw()
            runCurrent()
            assertEquals(1, lotteries.events.size)
            gate.complete(DrawResult.ALREADY_DRAWN)
            runCurrent()
            assertFalse(content(model).isDrawing)
        }

    /** Foreground work completes the same parent without any manual command or result copy. */
    @Test fun independentForegroundCompletionNeedsNoManualCall() =
        withOwner {
            val model = model()
            runCurrent()
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            model.refreshTime()
            runCurrent()
            assertFalse(content(model).canDraw)
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Failure retains parent/allocations and requires another explicit eligible request. */
    @Test fun failedDrawCanBeExplicitlyRetriedWithoutLosingContext() =
        withOwner {
            lotteries.drawHandler = { throw IOException("draw offline") }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertTrue(content(model).drawFailed)
            assertFalse(content(model).isDrawing)
            assertEquals(parent, content(model).lottery)
            assertTrue(content(model).offers is LotteryOffersState.Content)
            assertEquals(1, lotteries.events.size)
            lotteries.drawHandler = { DrawResult.COMPLETED }
            model.draw()
            assertFalse(content(model).drawFailed)
            runCurrent()
            assertEquals(2, lotteries.events.size)
            assertEquals(null, content(model).lottery.drawnAt)
        }

    /** A late manual failure cannot contradict confirmed completion by another caller. */
    @Test fun lateFailureAfterObservedCompletionDoesNotReportAnUnfinishedDraw() =
        withOwner {
            val gate = CompletableDeferred<Unit>()
            lotteries.drawHandler = {
                gate.await()
                throw IOException("lost response")
            }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertEquals(LotteryLifecycle.BOOKING_OPEN, content(model).lifecycle)
        }

    /** Parent failure retires command presentation and retry reads without replaying the write. */
    @Test fun failedParentRecoveryDoesNotReplayAManualDraw() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { gate.await() }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            lotteries.recordFailure.value = IOException("parent offline")
            runCurrent()
            assertEquals(LotteryDetailUiState.Failure, model.state.value)
            lotteries.recordFailure.value = null
            model.retry()
            runCurrent()
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertEquals(1, lotteries.events.size)
            gate.complete(DrawResult.COMPLETED)
        }

    /** Confirmed parent cancellation retires a pending manual call without a failure banner. */
    @Test fun cancellationRemovesThePendingCommand() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { gate.await() }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            lotteries.records.value =
                listOf(parent.copy(cancellation = Cancellation(clock.instant(), "owner")))
            runCurrent()
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertFalse(content(model).canDraw)
            gate.complete(DrawResult.COMPLETED)
        }

    /** Clearing the graph ignores noncooperative late failures rather than publishing an error. */
    @Test fun retiredOwnerIgnoresLateNoncancellableResult() =
        withOwner {
            val gate = CompletableDeferred<Unit>()
            lotteries.drawHandler = {
                withContext(NonCancellable) { gate.await() }
                throw IOException("late failure")
            }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            store.clear()
            val retired = model.state.value
            gate.complete(Unit)
            runCurrent()
            assertEquals(retired, model.state.value)
            assertEquals(0, lotteries.activeRecordObservers)
        }

    /** A new graph reads current completion without restoring or replaying a pending command. */
    @Test fun reconstructedOwnerReadsCurrentFactsWithoutAnotherWrite() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { gate.await() }
            val first = model()
            runCurrent()
            first.draw()
            runCurrent()
            store.clear()
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            val restored = model()
            runCurrent()
            assertEquals(LotteryLifecycle.BOOKING_OPEN, content(restored).lifecycle)
            assertFalse(content(restored).isDrawing)
            assertFalse(content(restored).canDraw)
            assertEquals(listOf("draw:draw"), lotteries.events)
            gate.complete(DrawResult.COMPLETED)
        }

    /** Removing a parent retires a held command and rejects its noncooperative late outcome. */
    @Test fun removedParentStaysUnavailableAfterLateDrawCompletion() =
        withOwner {
            val gate = CompletableDeferred<DrawResult>()
            lotteries.drawHandler = { withContext(NonCancellable) { gate.await() } }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertTrue(content(model).isDrawing)
            lotteries.records.value = emptyList()
            runCurrent()
            assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
            gate.complete(DrawResult.COMPLETED)
            runCurrent()
            assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
            assertEquals(listOf("draw:draw"), lotteries.events)
        }

    /** A cancelled repository call ends actual progress without creating a failure banner. */
    @Test fun cancelledRepositoryCallReleasesTransientProgress() =
        withOwner {
            lotteries.drawHandler = { throw CancellationException("cancelled request") }
            val model = model()
            runCurrent()
            model.draw()
            runCurrent()
            assertEquals(listOf("draw:draw"), lotteries.events)
            assertFalse(content(model).isDrawing)
            assertFalse(content(model).drawFailed)
            assertEquals(parent, content(model).lottery)
        }

    /** Constructs the actual saved parent with no participant filter implementation.
     * @param context Explicit caller context.
     */
    private fun model(context: LotteryContext = LotteryContext.OWNER) =
        LotteryDetailViewModel(
            "owner",
            "draw",
            lotteries,
            FakeCampsiteRepository(),
            clock,
            FakeBookingRepository(),
            context,
            FakeAccountRepository(),
        ).also { store.put("detail", it) }

    /** Requires confirmed content before examining the command projection.
     * @param model Current saved parent.
     */
    private fun content(model: LotteryDetailViewModel): LotteryDetailUiState.Content {
        assertTrue(model.state.value is LotteryDetailUiState.Content)
        return model.state.value as LotteryDetailUiState.Content
    }

    /** Supplies explicit operation acknowledgement and releases graph jobs before virtual draining.
     * @param body Test contract.
     */
    private fun withOwner(body: suspend TestScope.() -> Unit) =
        runTest {
            lotteries.drawHandler = { DrawResult.COMPLETED }
            try {
                body()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

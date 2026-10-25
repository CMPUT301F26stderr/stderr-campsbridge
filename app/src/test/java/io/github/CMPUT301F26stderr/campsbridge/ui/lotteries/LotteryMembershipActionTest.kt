package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration

/** Contract-first membership actions use current facts, one command and observed confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryMembershipActionTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = CampingFixtures.now
    private val parent = CampingFixtures.lottery("draw", "owner")
    private val clock = MutableClock(now.plusSeconds(90))
    private val lotteries = FakeLotteryRepository(listOf(parent))
    private val sites = FakeCampsiteRepository()
    private val bookings = FakeBookingRepository()

    /** Only absent/withdrawn participation can join; only waiting participation can leave. */
    @Test fun membershipStatesSupplyOnlyTheirEligibleAction() =
        withModel { model, _ ->
            runCurrent()
            assertTrue(content(model).participation.canJoin)
            assertFalse(content(model).participation.canLeave)
            for (state in EntryState.entries) {
                lotteries.entries.value = listOf(entry(state))
                runCurrent()
                assertEquals(state == EntryState.WITHDRAWN, content(model).participation.canJoin)
                assertEquals(state == EntryState.WAITING, content(model).participation.canLeave)
            }
            assertTrue(lotteries.events.isEmpty())
        }

    /** An owner cannot acquire entrant action behavior from a readable owned parent. */
    @Test fun ownerContextRejectsAllMembershipCommands() =
        withModel(LotteryContext.OWNER) { model, _ ->
            runCurrent()
            model.join()
            model.requestLeave()
            model.confirmLeave()
            runCurrent()
            assertFalse(content(model).participation.canJoin)
            assertFalse(content(model).participation.canLeave)
            assertFalse(content(model).participation.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(lotteries.entrantRequests.isEmpty())
        }

    /** Public actions read the clock again: opening is inclusive and closing is exclusive. */
    @Test fun exactRegistrationBoundariesAreRecheckedBeforeLaunch() =
        withModel { model, _ ->
            clock.advance(Duration.ofSeconds(-31))
            runCurrent()
            model.join()
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
            clock.advance(Duration.ofSeconds(1))
            lotteries.joinHandler = { _, _ -> }
            model.join()
            runCurrent()
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
        }

    /** A close between rendering and Join cannot write with stale enabled state. */
    @Test fun staleOpenPresentationCannotJoinAtExactClose() =
        withModel { model, _ ->
            runCurrent()
            assertTrue(content(model).participation.canJoin)
            clock.advance(Duration.ofSeconds(30))
            model.join()
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
            assertFalse(content(model).participation.canJoin)
        }

    /** Unknown or failed membership cannot be treated as confirmed absence. */
    @Test fun membershipLoadingAndFailureBlockWrites() =
        withModel { model, _ ->
            model.join()
            model.confirmLeave()
            assertTrue(lotteries.events.isEmpty())
            runCurrent()
            lotteries.entrantFailure.value = IOException("offline")
            runCurrent()
            model.join()
            model.requestLeave()
            model.confirmLeave()
            runCurrent()
            assertFalse(content(model).participation.canJoin)
            assertFalse(content(model).participation.canLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Join claims its guard synchronously and waits for observed membership. */
    @Test fun joinGuardsDuplicateCallsUntilAckAndObservation() =
        withModel { model, _ ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> gate.await() }
                model.join()
                assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
                model.join()
                model.requestLeave()
                model.confirmLeave()
                runCurrent()
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
                assertEquals(LotteryMembershipStatus.NOT_JOINED, membership(model))
                gate.complete(Unit)
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
                model.join()
                runCurrent()
                assertEquals(1, lotteries.events.size)
                lotteries.entries.value = listOf(entry(EntryState.WAITING))
                runCurrent()
                assertNull(content(model).participation.action)
                assertEquals(LotteryMembershipStatus.WAITING, membership(model))
                assertTrue(content(model).participation.canLeave)
            } finally {
                gate.complete(Unit)
            }
        }

    /** An early observation cannot permit the opposite command before acknowledgement arrives. */
    @Test fun earlyMembershipDoesNotReleaseAnUnacknowledgedJoin() =
        withModel { model, _ ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> gate.await() }
                model.join()
                runCurrent()
                lotteries.entries.value = listOf(entry(EntryState.WAITING))
                runCurrent()
                model.requestLeave()
                model.confirmLeave()
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
                assertFalse(content(model).participation.isConfirmingLeave)
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
                gate.complete(Unit)
                runCurrent()
                assertNull(content(model).participation.action)
                model.requestLeave()
                assertTrue(content(model).participation.isConfirmingLeave)
            } finally {
                gate.complete(Unit)
            }
        }

    /** A later withdrawal cannot erase evidence that the held Join already took effect. */
    @Test fun supersededJoinObservationStillReleasesItsAcknowledgedCommand() =
        withModel { model, _ ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> gate.await() }
                model.join()
                runCurrent()
                lotteries.entries.value = listOf(entry(EntryState.WAITING))
                runCurrent()
                lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
                gate.complete(Unit)
                runCurrent()
                assertNull(content(model).participation.action)
                assertEquals(LotteryMembershipStatus.WITHDRAWN, membership(model))
                assertTrue(content(model).participation.canJoin)
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
            } finally {
                gate.complete(Unit)
            }
        }

    /** A later rejoin cannot erase the observed effect of a held withdrawal. */
    @Test fun supersededLeaveObservationStillReleasesItsAcknowledgedCommand() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.withdrawHandler = { _, _ -> gate.await() }
                model.requestLeave()
                model.confirmLeave()
                runCurrent()
                lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
                runCurrent()
                lotteries.entries.value = listOf(entry(EntryState.WAITING))
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertNull(content(model).participation.action)
                assertFalse(content(model).participation.isConfirmingLeave)
                assertEquals(LotteryMembershipStatus.WAITING, membership(model))
                assertTrue(content(model).participation.canLeave)
                assertEquals(listOf("withdraw:draw:entrant"), lotteries.events)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Opening and dismissing Leave has no persistence effect. */
    @Test fun leaveRequiresAnExplicitLiveConfirmation() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            model.confirmLeave()
            model.requestLeave()
            assertTrue(content(model).participation.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
            model.dismissLeave()
            model.confirmLeave()
            runCurrent()
            assertFalse(content(model).participation.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Confirmed withdrawal shares the guard and never edits membership/count locally. */
    @Test fun leaveGuardsConfirmAndDismissUntilAckAndObservation() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.withdrawHandler = { _, _ -> gate.await() }
                model.requestLeave()
                model.confirmLeave()
                model.confirmLeave()
                model.dismissLeave()
                model.join()
                runCurrent()
                assertEquals(listOf("withdraw:draw:entrant"), lotteries.events)
                assertTrue(content(model).participation.isConfirmingLeave)
                assertEquals(LotteryMembershipStatus.WAITING, membership(model))
                gate.complete(Unit)
                runCurrent()
                assertEquals(LotteryMembershipAction.LEAVE, content(model).participation.action)
                lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
                runCurrent()
                assertNull(content(model).participation.action)
                assertFalse(content(model).participation.isConfirmingLeave)
                assertEquals(LotteryMembershipStatus.WITHDRAWN, membership(model))
                assertTrue(content(model).participation.canJoin)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Closing registration or a changed membership invalidates a stale unsubmitted dialog. */
    @Test fun staleLeaveConfirmationCannotWithdraw() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            model.requestLeave()
            clock.advance(Duration.ofSeconds(30))
            model.confirmLeave()
            runCurrent()
            assertFalse(content(model).participation.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Existing typed failures supply feedback while preserving the last observed membership. */
    @Test fun fullClosedAndTransportFailuresRetainContextAndAllowExplicitRetry() =
        withModel { model, _ ->
            runCurrent()
            val failures =
                listOf(
                    PersistenceFailure(PersistenceFailure.Reason.CAPACITY_REACHED, "full") to
                        LotteryMembershipActionError.FULL,
                    PersistenceFailure(PersistenceFailure.Reason.WINDOW_CLOSED, "closed") to
                        LotteryMembershipActionError.CLOSED,
                    IOException("offline") to LotteryMembershipActionError.FAILED,
                )
            for ((failure, error) in failures) {
                lotteries.joinHandler = { _, _ -> throw failure }
                model.join()
                assertNull(content(model).participation.error)
                runCurrent()
                assertEquals(parent, content(model).lottery)
                assertEquals(LotteryMembershipStatus.NOT_JOINED, membership(model))
                assertEquals(error, content(model).participation.error)
                assertNull(content(model).participation.action)
            }
            assertEquals(3, lotteries.events.count { it == "join:draw:entrant" })
            lotteries.joinHandler =
                { _, _ -> lotteries.entries.value = listOf(entry(EntryState.WAITING)) }
            model.join()
            runCurrent()
            assertNull(content(model).participation.error)
            assertNull(content(model).participation.action)
            assertEquals(LotteryMembershipStatus.WAITING, membership(model))
        }

    /** Failed withdrawal retains the waiting entry and permits a new confirmation. */
    @Test fun failedWithdrawalPreservesWaitingAndCanBeConfirmedAgain() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            lotteries.withdrawHandler = { _, _ -> throw IOException("offline") }
            model.requestLeave()
            model.confirmLeave()
            runCurrent()
            assertEquals(LotteryMembershipStatus.WAITING, membership(model))
            assertEquals(LotteryMembershipActionError.FAILED, content(model).participation.error)
            assertFalse(content(model).participation.isConfirmingLeave)
            model.requestLeave()
            assertTrue(content(model).participation.isConfirmingLeave)
            assertNull(content(model).participation.error)
        }

    /** Draw completion removes actions without erasing historical facts. */
    @Test fun completedParentBlocksCommandsAndClosesUnsubmittedLeave() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            model.requestLeave()
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            assertFalse(content(model).participation.isConfirmingLeave)
            model.confirmLeave()
            model.join()
            runCurrent()
            assertFalse(content(model).participation.canJoin)
            assertFalse(content(model).participation.canLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Cancellation preserves readable history while unpublished records stay unavailable. */
    @Test fun cancelledAndDraftParentsCannotMutateMembership() =
        withModel { model, _ ->
            runCurrent()
            lotteries.records.value =
                listOf(parent.copy(cancellation = Cancellation(clock.instant(), "owner")))
            runCurrent()
            model.join()
            model.requestLeave()
            model.confirmLeave()
            runCurrent()
            assertFalse(content(model).participation.canJoin)
            assertFalse(content(model).participation.canLeave)
            lotteries.records.value = listOf(parent.copy(publishedAt = null))
            runCurrent()
            model.join()
            model.confirmLeave()
            runCurrent()
            assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
            assertTrue(lotteries.events.isEmpty())
        }

    /** A changed entry closes an unsubmitted confirmation instead of submitting obsolete intent. */
    @Test fun changedOwnEntryInvalidatesAnUnsubmittedLeaveDialog() =
        withModel { model, _ ->
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            model.requestLeave()
            assertTrue(content(model).participation.isConfirmingLeave)
            lotteries.entries.value = listOf(entry(EntryState.WITHDRAWN))
            runCurrent()
            assertFalse(content(model).participation.isConfirmingLeave)
            model.confirmLeave()
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
        }

    /** A failed observation cannot release an acknowledged command for an automatic duplicate. */
    @Test fun acknowledgedJoinWaitsThroughMembershipRetry() =
        withModel { model, _ ->
            runCurrent()
            lotteries.joinHandler = { _, _ -> }
            model.join()
            runCurrent()
            lotteries.entrantFailure.value = IOException("entry offline")
            runCurrent()
            assertEquals(LotteryMembershipRead.Failure, content(model).membership)
            assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
            lotteries.entrantFailure.value = null
            model.retryMembership()
            runCurrent()
            model.join()
            runCurrent()
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            assertNull(content(model).participation.action)
        }

    /** A quick draw may replace WAITING before observation without leaving Join stuck pending. */
    @Test fun laterDrawOutcomeAlsoConfirmsAnAcknowledgedJoin() =
        withModel { model, _ ->
            runCurrent()
            lotteries.joinHandler = { _, _ -> }
            model.join()
            runCurrent()
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            runCurrent()
            assertNull(content(model).participation.action)
            assertEquals(LotteryMembershipStatus.RESULTS_PENDING, membership(model))
            assertTrue(bookings.opportunityRequests.isEmpty())
            lotteries.records.value = listOf(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            assertEquals(LotteryMembershipStatus.WON, membership(model))
        }

    /** Parent disappearance rejects late command results without restoring stale content. */
    @Test fun removedParentRejectsLateCommandCompletion() =
        withModel { model, _ ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> withContext(NonCancellable) { gate.await() } }
                model.join()
                runCurrent()
                lotteries.records.value = emptyList()
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Graph retirement cancels its command and ignores late results. */
    @Test fun clearedGraphRejectsLateCompletion() =
        withModel { model, store ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> withContext(NonCancellable) { gate.await() } }
                model.join()
                runCurrent()
                val before = model.state.value
                store.clear()
                gate.complete(Unit)
                runCurrent()
                assertEquals(before, model.state.value)
                assertEquals(0, lotteries.activeEntrantObservers)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Runs one command owner and closes it before virtual-time draining.
     * @param context Explicit presentation; the account matches its capability.
     * @param block Controlled actions and observable assertions.
     */
    private fun withModel(
        context: LotteryContext = LotteryContext.ENTRANT,
        block: suspend TestScope.(LotteryDetailViewModel, ViewModelStore) -> Unit,
    ) =
        runTest {
            val store = ViewModelStore()
            try {
                val model =
                    LotteryDetailViewModel(
                        if (context == LotteryContext.OWNER) "owner" else "entrant",
                        "draw",
                        lotteries,
                        sites,
                        clock,
                        bookings,
                        context,
                        accounts = FakeAccountRepository(),
                    )
                store.put("detail", model)
                block(model, store)
            } finally {
                store.clear()
                runCurrent()
                assertEquals(0, lotteries.activeRecordObservers)
                assertEquals(0, lotteries.activeEntrantObservers)
            }
        }

    /** Requires confirmed parent content before checking independent mutation feedback.
     * @param model Real command owner.
     */
    private fun content(model: LotteryDetailViewModel): LotteryDetailUiState.Content =
        model.state.value as LotteryDetailUiState.Content

    /** Reads the display-safe membership without inspecting private entries.
     * @param model Real command owner.
     */
    private fun membership(model: LotteryDetailViewModel): LotteryMembershipStatus =
        (content(model).membership as LotteryMembershipRead.Content).status

    /** Cancelled repository work releases the real parent action without a failed-save notice. */
    @Test fun cancelledRequestDoesNotLeaveTheParentSubmitting() =
        withModel { model, _ ->
            runCurrent()
            lotteries.joinHandler = { _, _ -> throw CancellationException("cancelled request") }
            model.join()
            runCurrent()
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
            assertNull(content(model).participation.action)
            assertNull(content(model).participation.error)
            assertTrue(content(model).participation.canJoin)
            assertEquals(parent, content(model).lottery)
        }

    /** Creates one own entry for this fixed opportunity.
     * @param state Exact persisted membership state supplied by the test.
     */
    private fun entry(state: EntryState): LotteryEntry =
        LotteryEntry(
            "draw",
            "entrant",
            state,
            now.plusSeconds(60),
            clock.instant(),
        )
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
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

/**
 * Shared commands consume supplied scoped facts and leave source/lifecycle ownership to callers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryMembershipControllerTest {
    @get:Rule val main = MainDispatcherRule()
    private val parent = CampingFixtures.lottery()
    private val clock = MutableClock(CampingFixtures.now.plusSeconds(90))
    private val lotteries = FakeLotteryRepository()
    private var changes = 0

    /** Supplying identical current facts cannot loop a parent's synchronous projection callback. */
    @Test fun changedFeedbackCallsItsOwnerWithoutStartingAQuery() =
        withReader { reader ->
            reader.updateFacts(parent, null, true)
            assertTrue(reader.state.value.canJoin)
            assertEquals(1, changes)
            reader.updateFacts(parent, null, true)
            assertEquals(1, changes)
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertTrue(lotteries.entriesRequests.isEmpty())
            assertTrue(lotteries.observedRecordIds.isEmpty())
        }

    /** Current exact own-entry facts determine actions; unknown membership is never absence. */
    @Test fun suppliedMembershipControlsEligibilityWithoutRewritingItsState() =
        withReader { reader ->
            reader.updateFacts(parent, null, true)
            assertTrue(reader.state.value.canJoin)
            for (state in EntryState.entries) {
                reader.updateFacts(parent, entry(state), true)
                assertEquals(state == EntryState.WITHDRAWN, reader.state.value.canJoin)
                assertEquals(state == EntryState.WAITING, reader.state.value.canLeave)
            }
            reader.updateFacts(parent, null, false)
            reader.join()
            assertFalse(reader.state.value.canJoin)
            assertFalse(reader.state.value.canLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** One fixed command remains pending after acknowledgement until its own effect is observed. */
    @Test fun heldJoinUsesTheFixedTupleAndWaitsForObservedMembership() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> gate.await() }
                reader.updateFacts(parent, null, true)
                reader.join()
                reader.join()
                assertEquals(LotteryMembershipAction.JOIN, reader.state.value.action)
                runCurrent()
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
                gate.complete(Unit)
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, reader.state.value.action)
                reader.updateFacts(parent, entry(EntryState.WAITING), true)
                assertNull(reader.state.value.action)
                assertTrue(reader.state.value.canLeave)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Early matching evidence survives a later unknown source until acknowledgement arrives. */
    @Test fun matchingEvidenceIsLatchedAcrossUnknownFacts() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> gate.await() }
                reader.updateFacts(parent, null, true)
                reader.join()
                runCurrent()
                reader.updateFacts(parent, entry(EntryState.WAITING), true)
                reader.updateFacts(parent, null, false)
                gate.complete(Unit)
                runCurrent()
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
                assertNull(reader.state.value.action)
                assertNull(reader.state.value.error)
                assertFalse(reader.state.value.canJoin)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Dismissal is read-only and Confirm rechecks the actual clock instead of a stale button. */
    @Test fun withdrawalConfirmationIsExplicitAndRechecksTheWindow() =
        withReader { reader ->
            reader.updateFacts(parent, entry(EntryState.WAITING), true)
            reader.requestLeave()
            assertTrue(reader.state.value.isConfirmingLeave)
            reader.dismissLeave()
            assertFalse(reader.state.value.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
            reader.requestLeave()
            clock.advance(Duration.ofSeconds(30))
            reader.confirmLeave()
            assertFalse(reader.state.value.isConfirmingLeave)
            assertFalse(reader.state.value.canLeave)
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
        }

    /**
     * Expected repository categories stay typed and retry clears old feedback before suspension.
     */
    @Test fun typedFailuresPermitOnlyAnotherExplicitEligibleAction() =
        withReader { reader ->
            reader.updateFacts(parent, null, true)
            val failures =
                listOf(
                    PersistenceFailure(PersistenceFailure.Reason.CAPACITY_REACHED, "full") to
                        LotteryMembershipActionError.FULL,
                    PersistenceFailure(PersistenceFailure.Reason.WINDOW_CLOSED, "closed") to
                        LotteryMembershipActionError.CLOSED,
                    IOException("offline") to LotteryMembershipActionError.FAILED,
                )
            for ((failure, feedback) in failures) {
                lotteries.joinHandler = { _, _ -> throw failure }
                reader.join()
                assertNull(reader.state.value.error)
                runCurrent()
                assertEquals(feedback, reader.state.value.error)
                assertNull(reader.state.value.action)
                assertTrue(reader.state.value.canJoin)
            }
            assertEquals(3, lotteries.events.size)
        }

    /** Another entry or parent cannot authorize a write for this controller's fixed tuple. */
    @Test fun mismatchedFactsCannotAuthorizeTheFixedTarget() =
        withReader { reader ->
            reader.updateFacts(parent.copy(id = "other"), null, true)
            reader.join()
            reader.updateFacts(parent, entry(EntryState.WAITING).copy(accountId = "other"), true)
            reader.requestLeave()
            reader.confirmLeave()
            reader.join()
            reader.updateFacts(parent, entry(EntryState.WAITING).copy(lotteryId = "other"), true)
            reader.requestLeave()
            reader.confirmLeave()
            runCurrent()
            assertFalse(reader.state.value.canJoin)
            assertFalse(reader.state.value.canLeave)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Parent retirement invalidates a noncooperative acknowledgement and never restores its UI. */
    @Test fun retiredFactsRejectLateNoncancellableAcknowledgement() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                lotteries.joinHandler = { _, _ -> withContext(NonCancellable) { gate.await() } }
                reader.updateFacts(parent, null, true)
                reader.join()
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, reader.state.value.action)
                reader.updateFacts(null, null, false)
                assertEquals(LotteryMembershipUiState(), reader.state.value)
                gate.complete(Unit)
                runCurrent()
                assertEquals(LotteryMembershipUiState(), reader.state.value)
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
            } finally {
                gate.complete(Unit)
            }
        }

    /** A closed owner cannot be revived by later facts or a delayed screen callback. */
    @Test fun closedControllerNeverRestartsACommand() =
        withReader { reader ->
            reader.updateFacts(parent, null, true)
            assertTrue(reader.state.value.canJoin)
            reader.close()
            reader.updateFacts(parent, null, true)
            reader.join()
            reader.requestLeave()
            reader.confirmLeave()
            runCurrent()
            assertEquals(LotteryMembershipUiState(), reader.state.value)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Repository cancellation ends transient command feedback without becoming an action error. */
    @Test fun cancelledRequestReleasesTheCommandWithoutAFailure() =
        withReader { reader ->
            lotteries.joinHandler = { _, _ -> throw CancellationException("cancelled request") }
            reader.updateFacts(parent, null, true)
            reader.join()
            runCurrent()
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
            assertNull(reader.state.value.action)
            assertNull(reader.state.value.error)
            assertTrue(reader.state.value.canJoin)
        }

    /** Supplies exact immutable own-entry facts.
     * @param state Current saved entry state.
     */
    private fun entry(state: EntryState): LotteryEntry =
        LotteryEntry(
            "draw",
            "entrant",
            state,
            CampingFixtures.now,
            CampingFixtures.now,
        )

    /** Closes child work before the test scope drains virtual coroutines.
     * @param body Contract executed with supplied facts and explicit operation outcomes.
     */
    private fun withReader(body: suspend TestScope.(LotteryMembershipController) -> Unit) =
        runTest {
            val reader =
                LotteryMembershipController(
                    "entrant",
                    "draw",
                    lotteries,
                    clock,
                    backgroundScope,
                    onChanged = { changes++ },
                )
            try {
                body(reader)
            } finally {
                reader.close()
                runCurrent()
            }
        }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration

/** Saved-parent contracts keep participant reads scoped, restorable and independent. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryParticipantsParentTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val parent = CampingFixtures.lottery()
    private val clock = MutableClock(CampingFixtures.now.plusSeconds(90))
    private val lotteries = FakeLotteryRepository(listOf(parent))
    private val accounts =
        FakeAccountRepository().also {
            it.getHandler = { id -> CampingFixtures.account(id) }
        }
    private val waiting =
        LotteryEntry("draw", "first", EntryState.WAITING, CampingFixtures.now, CampingFixtures.now)

    /** Overview and child share one parent; hidden child performs no participant query. */
    @Test fun childStartsOnlyWhileVisibleAndBackKeepsParent() =
        withOwner {
            val model = model()
            runCurrent()
            assertTrue(lotteries.entriesRequests.isEmpty())
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(listOf("draw"), lotteries.entriesRequests)
            assertEquals(1, lotteries.activeRecordObservers)
            assertEquals(1, rows(model).size)
            model.setParticipantsVisible(false)
            runCurrent()
            assertEquals(0, lotteries.activeEntriesObservers)
            assertTrue(model.state.value is LotteryDetailUiState.Content)
        }

    /** Independent failed entries never erase the overview or restart its successful sources. */
    @Test fun sourceRetryBelongsOnlyToTheParticipantChild() =
        withOwner {
            lotteries.entriesFailure.value = IOException("entries offline")
            val model = model()
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(ParticipantListUiState.Failure, model.participants.value)
            assertTrue(model.state.value is LotteryDetailUiState.Content)
            lotteries.entriesFailure.value = null
            model.retryParticipants()
            model.retryParticipants()
            runCurrent()
            assertEquals(1, rows(model).size)
            assertEquals(2, lotteries.entriesRequests.size)
            assertEquals(1, lotteries.observedRecordIds.size)
            assertEquals(1, lotteries.activeOfferObservers)
        }

    /**
     * A failed parent retires child reads; its explicit recovery resumes the same visible child.
     */
    @Test fun parentRecoveryRebindsTheVisibleChild() =
        withOwner {
            val model = model()
            model.setParticipantsVisible(true)
            runCurrent()
            lotteries.recordFailure.value = IOException("parent offline")
            runCurrent()
            assertEquals(ParticipantListUiState.Unavailable, model.participants.value)
            assertEquals(0, lotteries.activeEntriesObservers)
            lotteries.recordFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(1, rows(model).size)
            assertEquals(1, lotteries.activeEntriesObservers)
            assertEquals(1, lotteries.activeRecordObservers)
        }

    /**
     * Entrant presentation never obtains owner entries even when the current ID owns the record.
     */
    @Test fun entrantContextCannotStartTheOwnerSource() =
        withOwner {
            val model = model(context = LotteryContext.ENTRANT)
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(ParticipantListUiState.Unavailable, model.participants.value)
            assertTrue(lotteries.entriesRequests.isEmpty())
            assertEquals(listOf("owner"), lotteries.entrantRequests)
        }

    /** Small saved state restores an exact filter into a new saved-parent owner. */
    @Test fun filterSelectionSurvivesNewParentConstruction() =
        withOwner {
            val saved = SavedStateHandle()
            val model = model(saved)
            model.setParticipantsVisible(true)
            runCurrent()
            model.selectParticipantState(EntryState.WAITING)
            runCurrent()
            assertEquals("WAITING", saved.get<String>("participantState"))
            assertEquals(EntryState.WAITING, model.selectedParticipantState.value)
            val restored =
                model(
                    SavedStateHandle(
                        mapOf("participantState" to saved.get<String>("participantState")),
                    ),
                )
            restored.setParticipantsVisible(true)
            runCurrent()
            assertEquals(EntryState.WAITING, restored.selectedParticipantState.value)
            assertEquals(listOf("first"), rows(restored).map { it.accountId })
        }

    /**
     * Restored outcome filters are cleared before an incomplete draw can expose partial results.
     */
    @Test fun unsafeRestoredFilterIsAlsoClearedFromSavedState() =
        withOwner {
            val saved = SavedStateHandle(mapOf("participantState" to "OFFERED"))
            val model = model(saved)
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(null, model.selectedParticipantState.value)
            assertEquals(null, saved.get<String>("participantState"))
            assertEquals(1, rows(model).size)
        }

    /**
     * The parent's existing deadline refreshes participant labels without requerying or drawing.
     */
    @Test fun parentDeadlineReprojectsTheSameRows() =
        withOwner {
            val model = model()
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(LotteryMembershipStatus.WAITING, rows(model).single().status)
            val delay =
                Duration
                    .between(
                        clock.instant(),
                        parent.configuration.registrationClosesAt,
                    ).toMillis()
            clock.advance(Duration.ofMillis(delay))
            advanceTimeBy(delay)
            runCurrent()
            assertEquals(LotteryMembershipStatus.RESULTS_PENDING, rows(model).single().status)
            assertEquals(1, lotteries.entriesRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Clearing the saved graph releases the child along with the parent subscription. */
    @Test fun graphRemovalReleasesAllParticipantReads() =
        withOwner {
            val model = model()
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(1, lotteries.activeEntriesObservers)
            store.clear()
            runCurrent()
            assertEquals(0, lotteries.activeEntriesObservers)
            assertEquals(ParticipantListUiState.Unavailable, model.participants.value)
            model.setParticipantsVisible(true)
            runCurrent()
            assertEquals(0, lotteries.activeEntriesObservers)
        }

    /** Constructs the real parent with a fixed known selector outcome, not its reserved algorithm.
     * @param saved Restorable small filter state.
     * @param context Explicit caller context.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        context: LotteryContext = LotteryContext.OWNER,
    ): LotteryDetailViewModel =
        LotteryDetailViewModel(
            "owner",
            "draw",
            lotteries,
            FakeCampsiteRepository(),
            clock,
            FakeBookingRepository(),
            context,
            accounts,
            saved,
            filterParticipants = { _, _ -> listOf(waiting) },
        ).also { store.put("parent", it) }

    /** Requires confirmed child content before reading its display-safe rows.
     * @param model Current saved parent.
     */
    private fun rows(model: LotteryDetailViewModel): List<ParticipantRow> {
        assertTrue(model.participants.value is ParticipantListUiState.Content)
        return (model.participants.value as ParticipantListUiState.Content).rows
    }

    /** Supplies realistic entries and closes every parent before virtual-time draining.
     * @param body Contract executed against the real parent.
     */
    private fun withOwner(body: suspend TestScope.() -> Unit) =
        runTest {
            lotteries.entries.value = listOf(waiting)
            try {
                body()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

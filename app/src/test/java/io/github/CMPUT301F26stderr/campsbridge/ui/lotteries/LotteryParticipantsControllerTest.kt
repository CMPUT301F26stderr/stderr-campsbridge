package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
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

/**
 * Contract-first owner child reads keep entries, profile labels and result filtering independent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryParticipantsControllerTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = CampingFixtures.now
    private val parent = CampingFixtures.lottery("draw", "owner")
    private val clock = MutableClock(now.plusSeconds(90))
    private val lotteries = FakeLotteryRepository()
    private val accounts =
        FakeAccountRepository().also { repository ->
            repository.getHandler = { CampingFixtures.account(it) }
        }
    private val waiting = entry("first", EntryState.WAITING)
    private val withdrawn = entry("second", EntryState.WITHDRAWN)

    /** A published owned parent alone does not start a hidden child's source. */
    @Test fun childVisibilityOwnsItsScopedQuery() =
        withReader { reader ->
            reader.updateParent(parent)
            runCurrent()
            assertTrue(lotteries.entriesRequests.isEmpty())
            assertTrue(accounts.readIds.isEmpty())
            reader.setVisible(true)
            runCurrent()
            assertEquals(listOf("draw"), lotteries.entriesRequests)
            assertEquals(emptyList<ParticipantRow>(), content(reader).rows)
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertTrue(lotteries.ownerRequests.isEmpty())
            reader.setVisible(false)
            runCurrent()
            assertEquals(0, lotteries.activeEntriesObservers)
            assertEquals(ParticipantListUiState.Unavailable, reader.state.value)
        }

    /** Foreign, unpublished and missing parents cannot trigger owner participant reads. */
    @Test fun onlyTheActualOwnedPublishedParentCanStartTheChild() =
        withReader { reader ->
            reader.setVisible(true)
            for (value in listOf(
                null,
                parent.copy(ownerId = "other"),
                parent.copy(publishedAt = null),
                parent.copy(id = "other"),
            )) {
                reader.updateParent(value)
                runCurrent()
                assertEquals(ParticipantListUiState.Unavailable, reader.state.value)
                assertTrue(lotteries.entriesRequests.isEmpty())
            }
            reader.updateParent(parent)
            runCurrent()
            assertEquals(1, lotteries.activeEntriesObservers)
        }

    /** Missing profiles retain their canonical entry and do not hide other participant facts. */
    @Test fun absentProfileRemainsAVisibleEntry() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting, withdrawn)
            accounts.getHandler =
                { id -> if (id == "second") null else CampingFixtures.account(id) }
            open(reader)
            runCurrent()
            val rows = content(reader).rows
            assertEquals(listOf("first", "second"), rows.map { it.accountId })
            assertEquals(ParticipantName.Available("Camper"), rows[0].name)
            assertEquals(ParticipantName.Unavailable, rows[1].name)
            assertEquals(LotteryMembershipStatus.WAITING, rows[0].status)
            assertEquals(LotteryMembershipStatus.WITHDRAWN, rows[1].status)
            assertEquals(waiting.joinedAt, rows[0].joinedAt)
            assertEquals(listOf("first", "second"), accounts.readIds)
        }

    /** An unrelated account returned by a bad source cannot label another entrant's row. */
    @Test fun mismatchedProfileIdentityStaysUnavailable() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting)
            accounts.getHandler = { CampingFixtures.account("unrelated") }
            open(reader)
            runCurrent()
            assertEquals(ParticipantName.Unavailable, content(reader).rows.single().name)
        }

    /** Label retry reads only failures and never restarts the successful entry source. */
    @Test fun failedLabelRetriesWithoutHidingOrReloadingOtherRows() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting, withdrawn)
            accounts.getHandler = { id ->
                if (id == "second") throw IOException("profile offline")
                CampingFixtures.account(id)
            }
            open(reader)
            runCurrent()
            assertEquals(ParticipantName.Failure, content(reader).rows[1].name)
            accounts.getHandler = { CampingFixtures.account(it) }
            reader.retryNames()
            reader.retryNames()
            runCurrent()
            assertEquals(listOf("first", "second", "second"), accounts.readIds)
            assertEquals(listOf("draw"), lotteries.entriesRequests)
            assertEquals(ParticipantName.Available("Camper"), content(reader).rows[1].name)
        }

    /** Failed entries are distinct from empty data and retry without replaying profile reads. */
    @Test fun entryFailureRetriesItsSourceOnly() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting)
            open(reader)
            runCurrent()
            lotteries.entriesFailure.value = IOException("entries offline")
            runCurrent()
            assertEquals(ParticipantListUiState.Failure, reader.state.value)
            lotteries.entriesFailure.value = null
            reader.retryEntries()
            reader.retryEntries()
            runCurrent()
            assertEquals(listOf("draw", "draw"), lotteries.entriesRequests)
            assertEquals(listOf("first"), accounts.readIds)
            assertEquals(listOf("first"), content(reader).rows.map { it.accountId })
        }

    /** All works without the unfinished helper; a requested filter stays explicitly pending. */
    @Test fun absentFilterBlocksOnlyRequestedFiltering() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting, withdrawn)
            open(reader)
            runCurrent()
            assertEquals(2, content(reader).rows.size)
            assertFalse(content(reader).filterPending)
            reader.selectState(EntryState.WITHDRAWN)
            runCurrent()
            assertTrue(content(reader).filterPending)
            assertTrue(content(reader).rows.isEmpty())
            reader.selectState(null)
            runCurrent()
            assertFalse(content(reader).filterPending)
            assertEquals(listOf("first", "second"), content(reader).rows.map { it.accountId })
        }

    /**
     * Partial outcomes cannot leak through filtering or public row state before draw completion.
     */
    @Test fun provisionalOutcomesAreNormalizedBeforeTheReservedFilter() =
        withReader(
            filter = { entries, selected ->
                assertEquals(EntryState.WAITING, selected)
                assertEquals(List(3) { EntryState.WAITING }, entries.map { it.state })
                listOf(entries[0], entries[1], entries[2])
            },
        ) { reader ->
            clock.advance(Duration.ofSeconds(60))
            lotteries.entries.value =
                listOf(
                    waiting,
                    entry("offered", EntryState.OFFERED),
                    entry("lost", EntryState.LOST),
                )
            open(reader)
            reader.selectState(EntryState.WAITING)
            runCurrent()
            assertEquals(
                List(3) {
                    LotteryMembershipStatus.RESULTS_PENDING
                },
                content(reader).rows.map { it.status },
            )
            assertFalse(EntryState.OFFERED in content(reader).allowedStates)
            assertFalse(EntryState.LOST in content(reader).allowedStates)
            reader.selectState(EntryState.OFFERED)
            runCurrent()
            assertEquals(EntryState.WAITING, reader.selectedState.value)
            assertEquals(3, content(reader).rows.size)
        }

    /** Completed parent facts reproject existing outcomes without reopening entries or profiles. */
    @Test fun completedDrawRevealsFinalRowsUsingTheSameReaders() =
        withReader { reader ->
            clock.advance(Duration.ofSeconds(60))
            lotteries.entries.value =
                listOf(entry("first", EntryState.OFFERED), entry("second", EntryState.LOST))
            open(reader)
            runCurrent()
            assertEquals(
                List(2) {
                    LotteryMembershipStatus.RESULTS_PENDING
                },
                content(reader).rows.map { it.status },
            )
            reader.updateParent(parent.copy(drawnAt = clock.instant()))
            runCurrent()
            assertEquals(
                listOf(
                    LotteryMembershipStatus.WON,
                    LotteryMembershipStatus.LOST,
                ),
                content(reader).rows.map {
                    it.status
                },
            )
            assertTrue(EntryState.OFFERED in content(reader).allowedStates)
            assertTrue(EntryState.LOST in content(reader).allowedStates)
            assertEquals(listOf("draw"), lotteries.entriesRequests)
            assertEquals(listOf("first", "second"), accounts.readIds)
        }

    /** Exact filtering delegates the supplied outcome and preserves its selected row order. */
    @Test fun completedStateSelectionUsesTheSuppliedFunction() =
        withReader(
            filter = { entries, selected ->
                assertEquals(EntryState.LOST, selected)
                listOf(entries[2], entries[0])
            },
        ) { reader ->
            lotteries.entries.value =
                listOf(
                    entry("first", EntryState.LOST),
                    entry("second", EntryState.OFFERED),
                    entry("third", EntryState.LOST),
                )
            reader.updateParent(parent.copy(drawnAt = clock.instant()))
            reader.setVisible(true)
            reader.selectState(EntryState.LOST)
            runCurrent()
            assertEquals(listOf("third", "first"), content(reader).rows.map { it.accountId })
        }

    /**
     * A restored final-state filter is cleared when the current parent has not completed its draw.
     */
    @Test fun unfinishedParentCannotRestoreAFinalOutcomeFilter() =
        withReader(
            initialFilter = EntryState.OFFERED,
        ) { reader ->
            lotteries.entries.value = listOf(waiting)
            open(reader)
            runCurrent()
            assertEquals(null, reader.selectedState.value)
            assertFalse(content(reader).filterPending)
            assertEquals(1, content(reader).rows.size)
        }

    /** Clock refresh changes waitlist labels at close without writes or another query. */
    @Test fun exactRegistrationCloseReprojectsPendingLabels() =
        withReader { reader ->
            lotteries.entries.value = listOf(waiting)
            open(reader)
            runCurrent()
            assertEquals(LotteryMembershipStatus.WAITING, content(reader).rows.single().status)
            clock.advance(Duration.ofSeconds(30))
            reader.refreshTime()
            runCurrent()
            assertEquals(
                LotteryMembershipStatus.RESULTS_PENDING,
                content(reader).rows.single().status,
            )
            assertEquals(listOf("draw"), lotteries.entriesRequests)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Removing/readding an identity rejects a late noncooperative older profile response. */
    @Test fun replacedProfileReadCannotOverwriteTheNewIdentityGeneration() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                accounts.getHandler = { id ->
                    withContext(NonCancellable) { gate.await() }
                    CampingFixtures.account(id).copy(name = "Old response")
                }
                lotteries.entries.value = listOf(waiting)
                open(reader)
                runCurrent()
                assertEquals(ParticipantName.Loading, content(reader).rows.single().name)
                lotteries.entries.value = emptyList()
                runCurrent()
                accounts.getHandler = { CampingFixtures.account(it).copy(name = "New response") }
                lotteries.entries.value = listOf(waiting)
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertEquals(
                    ParticipantName.Available("New response"),
                    content(reader).rows.single().name,
                )
            } finally {
                gate.complete(Unit)
            }
        }

    /** Hiding the child releases jobs and rejects late labels; reopening starts a fresh scope. */
    @Test fun hiddenChildCannotBeRevivedByLateLabelCompletion() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                accounts.getHandler = { id ->
                    withContext(NonCancellable) { gate.await() }
                    CampingFixtures.account(id)
                }
                lotteries.entries.value = listOf(waiting)
                open(reader)
                runCurrent()
                reader.setVisible(false)
                gate.complete(Unit)
                runCurrent()
                assertEquals(ParticipantListUiState.Unavailable, reader.state.value)
                assertEquals(0, lotteries.activeEntriesObservers)
                accounts.getHandler = { CampingFixtures.account(it) }
                reader.setVisible(true)
                runCurrent()
                assertEquals(1, content(reader).rows.size)
                assertEquals(listOf("draw", "draw"), lotteries.entriesRequests)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Runs one bounded child and closes its jobs before virtual-time draining.
     * @param initialFilter Restored small selection.
     * @param filter Explicit known selector result; no production fallback is installed.
     * @param block Controlled read and projection assertions.
     */
    private fun withReader(
        initialFilter: EntryState? = null,
        filter: ((List<LotteryEntry>, EntryState?) -> List<LotteryEntry>)? = null,
        block: suspend TestScope.(LotteryParticipantsController) -> Unit,
    ) =
        runTest {
            val reader =
                LotteryParticipantsController(
                    "owner",
                    "draw",
                    lotteries,
                    accounts,
                    clock,
                    backgroundScope,
                    initialFilter,
                    filter,
                )
            try {
                block(reader)
            } finally {
                reader.close()
                runCurrent()
                assertEquals(0, lotteries.activeEntriesObservers)
            }
        }

    /** Shows the child with its confirmed owned parent.
     * @param reader Actual child owner.
     */
    private fun open(reader: LotteryParticipantsController) {
        reader.updateParent(parent)
        reader.setVisible(true)
    }

    /** Requires confirmed entry content before inspecting independent labels/filter state.
     * @param reader Actual child owner.
     */
    private fun content(reader: LotteryParticipantsController): ParticipantListUiState.Content {
        assertTrue(
            "Expected participant content, got ${reader.state.value}",
            reader.state.value is ParticipantListUiState.Content,
        )
        return reader.state.value as ParticipantListUiState.Content
    }

    /** Supplies one canonical entry without exposing raw outcomes through UI state.
     * @param accountId Stable referenced profile identity.
     * @param state Controlled stored membership.
     */
    private fun entry(
        accountId: String,
        state: EntryState,
    ): LotteryEntry =
        LotteryEntry(
            "draw",
            accountId,
            state,
            now.plusSeconds(60),
            now.plusSeconds(90),
        )
}

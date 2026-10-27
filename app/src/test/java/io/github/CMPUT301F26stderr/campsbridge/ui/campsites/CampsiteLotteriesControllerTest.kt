package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipAction
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipActionError
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipRead
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

/** Actual campsite associations scope shared participation and visible allocation readers. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteLotteriesControllerTest {
    @get:Rule val main = MainDispatcherRule()
    private val site = CampingFixtures.campsite()
    private val first = CampingFixtures.lottery()
    private val second = first.copy(id = "second")
    private val clock = MutableClock(CampingFixtures.now.plusSeconds(90))
    private val lotteries = FakeLotteryRepository()

    /** Only the published entrant context observes associations; an owned preview cannot join. */
    @Test fun otherContextsNeverReadOrWriteEntrantParticipation() =
        runTest {
            for (context in CampsiteDetailContext.entries.filter {
                it != CampsiteDetailContext.VIEW
            }) {
                val reader = reader(context)
                try {
                    reader.updateCampsite(site.copy(ownerId = "entrant"))
                    reader.setChoicesVisible(true)
                    reader.join(first.id)
                    runCurrent()
                    assertEquals(CampsiteLotteriesUiState.Unavailable, reader.state.value)
                } finally {
                    reader.close()
                }
            }
            assertTrue(lotteries.associationRequests.isEmpty())
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Matching published parents start exactly one scoped query and a sole own-account read. */
    @Test fun actualParentStartsOneScopedReader() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site.copy(publishedAt = null))
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Unavailable, reader.state.value)
            reader.updateCampsite(site.copy(id = "other"))
            runCurrent()
            assertTrue(lotteries.associationRequests.isEmpty())
            reader.updateCampsite(site)
            assertEquals(CampsiteLotteriesUiState.Loading, reader.state.value)
            runCurrent()
            reader.updateCampsite(site.copy(details = site.details.copy(name = "Updated")))
            runCurrent()
            assertEquals(listOf(site.id), lotteries.associationRequests)
            assertEquals(listOf("entrant"), lotteries.entrantRequests)
            assertEquals(listOf(first.id), ids(reader))
            assertTrue(content(reader).participation.canJoin)
            assertTrue(lotteries.observedRecordIds.isEmpty())
            assertTrue(lotteries.entriesRequests.isEmpty())
        }

    /** Zero confirmed choices is distinct from both a failed and an unread query. */
    @Test fun failedAssociationRetriesWithoutClaimingAnEmptyResult() =
        withReader { reader ->
            lotteries.associationFailure.value = IOException("offline")
            reader.updateCampsite(site)
            assertEquals(CampsiteLotteriesUiState.Loading, reader.state.value)
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Failure, reader.state.value)
            reader.join(first.id)
            lotteries.associationFailure.value = null
            reader.retry()
            runCurrent()
            assertTrue(content(reader).choices.isEmpty())
            assertEquals(2, lotteries.associationRequests.size)
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Actual registration boundaries wake without restarting the association query or writing. */
    @Test fun deadlineRefreshUsesInclusiveOpenAndExclusiveClose() =
        withReader { reader ->
            clock.advance(Duration.ofSeconds(-31))
            associated(first)
            reader.updateCampsite(site)
            runCurrent()
            assertTrue(content(reader).choices.isEmpty())
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf(first.id), ids(reader))
            clock.advance(Duration.ofSeconds(60))
            advanceTimeBy(60_000)
            runCurrent()
            assertTrue(content(reader).choices.isEmpty())
            assertEquals(1, lotteries.associationRequests.size)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Only current open records appear, with deterministic close/identity ordering. */
    @Test fun multipleChoicesDoNotReadMembershipAndIgnoreInactiveRecords() =
        withReader { reader ->
            val later =
                first.copy(
                    id = "later",
                    configuration =
                        first.configuration.copy(
                            registrationClosesAt =
                                first.configuration.registrationClosesAt
                                    .plusSeconds(
                                        1,
                                    ),
                            bookingOpensAt = first.configuration.bookingOpensAt.plusSeconds(1),
                        ),
                )
            associated(
                later,
                second,
                first,
                first.copy(id = "draft", publishedAt = null),
                first.copy(id = "drawn", drawnAt = clock.instant()),
            )
            reader.updateCampsite(site)
            runCurrent()
            assertEquals(listOf(first.id, second.id, later.id), ids(reader))
            assertEquals(LotteryMembershipRead.NotApplicable, content(reader).membership)
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertEquals(0, lotteries.activeOfferObservers)
        }

    /** Unknown membership blocks joining; foreign tuples cannot supply this sole target's state. */
    @Test fun soleChoiceRequiresConfirmedOwnMembership() =
        runTest {
            val source = MutableSharedFlow<List<LotteryEntry>>()
            val repository =
                object : LotteryRepository by lotteries {
                    /** Supplies held own-entry facts for this contract.
                     * @param accountId Requested fixed identity.
                     */
                    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
                        source
                }
            val reader = reader(repository = repository)
            try {
                associated(first)
                reader.updateCampsite(site)
                runCurrent()
                assertEquals(LotteryMembershipRead.Loading, content(reader).membership)
                reader.join(first.id)
                runCurrent()
                assertTrue(lotteries.events.isEmpty())
                source.emit(
                    listOf(
                        entry(first, EntryState.WAITING).copy(accountId = "other"),
                        entry(second, EntryState.WAITING),
                    ),
                )
                runCurrent()
                assertTrue(content(reader).participation.canJoin)
                assertEquals(
                    LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                    content(reader).membership,
                )
            } finally {
                reader.close()
            }
        }

    /** A delayed tap from a replaced sole choice cannot join the newly displayed lottery. */
    @Test fun staleDisplayedIdentityNeverTargetsItsReplacement() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            runCurrent()
            associated(second)
            runCurrent()
            reader.join(first.id)
            reader.requestLeave(first.id)
            reader.confirmLeave(first.id)
            runCurrent()
            assertTrue(lotteries.events.isEmpty())
            lotteries.joinHandler = { _, _ -> }
            reader.join(second.id)
            runCurrent()
            assertEquals(listOf("join:second:entrant"), lotteries.events)
        }

    /** Shared guards remain pending after acknowledgement until membership confirms the write. */
    @Test fun acknowledgedJoinWaitsForItsObservedEffect() =
        withReader { reader ->
            associated(first)
            lotteries.joinHandler = { _, _ -> }
            reader.updateCampsite(site)
            runCurrent()
            reader.join(first.id)
            reader.join(first.id)
            runCurrent()
            assertEquals(LotteryMembershipAction.JOIN, content(reader).participation.action)
            reader.join(first.id)
            lotteries.entries.value = listOf(entry(first, EntryState.WAITING))
            runCurrent()
            assertNull(content(reader).participation.action)
            assertTrue(content(reader).participation.canLeave)
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
        }

    /** Observation before acknowledgement retains the same sole target and pending command. */
    @Test fun heldJoinKeepsItsGuardAcrossEarlyObservation() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                associated(first)
                lotteries.joinHandler = { _, _ -> gate.await() }
                reader.updateCampsite(site)
                runCurrent()
                reader.join(first.id)
                runCurrent()
                lotteries.entries.value = listOf(entry(first, EntryState.WAITING))
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, content(reader).participation.action)
                reader.requestLeave(first.id)
                reader.confirmLeave(first.id)
                gate.complete(Unit)
                runCurrent()
                assertNull(content(reader).participation.action)
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Action-time refresh closes the registration lane even before its timer fires. */
    @Test fun closedWindowCannotJoinFromAStaleButton() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            runCurrent()
            assertTrue(content(reader).participation.canJoin)
            clock.advance(Duration.ofSeconds(30))
            reader.join(first.id)
            runCurrent()
            assertTrue(content(reader).choices.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /**
     * Window expiry retires held command presentation without turning its late return into
     * success.
     */
    @Test fun closingDuringJoinRetiresTheSoleCommand() =
        withReader { reader ->
            val gate = CompletableDeferred<Unit>()
            try {
                associated(first)
                lotteries.joinHandler = { _, _ -> withContext(NonCancellable) { gate.await() } }
                reader.updateCampsite(site)
                runCurrent()
                reader.join(first.id)
                runCurrent()
                assertEquals(LotteryMembershipAction.JOIN, content(reader).participation.action)
                clock.advance(Duration.ofSeconds(30))
                reader.refreshTime()
                runCurrent()
                assertTrue(content(reader).choices.isEmpty())
                assertNull(content(reader).participation.action)
                gate.complete(Unit)
                runCurrent()
                assertTrue(content(reader).choices.isEmpty())
                assertEquals(listOf("join:draw:entrant"), lotteries.events)
            } finally {
                gate.complete(Unit)
            }
        }

    /** A foreground return recomputes current choices without replaying a query or mutation. */
    @Test fun foregroundRefreshReconcilesChoicesWithoutWrites() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            runCurrent()
            clock.advance(Duration.ofSeconds(30))
            reader.refreshTime()
            runCurrent()
            assertTrue(content(reader).choices.isEmpty())
            assertEquals(1, lotteries.associationRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Losing association knowledge disables and retires dependent reads before explicit retry. */
    @Test fun failedLiveAssociationRetiresItsMembershipAndVisibleOffers() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            reader.setChoicesVisible(true)
            runCurrent()
            lotteries.associationFailure.value = IOException("association offline")
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Failure, reader.state.value)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertEquals(0, lotteries.activeOfferObservers)
            reader.join(first.id)
            lotteries.associationFailure.value = null
            reader.retry()
            runCurrent()
            assertEquals(listOf(first.id), ids(reader))
            assertEquals(1, lotteries.activeEntrantObservers)
            assertEquals(1, lotteries.activeOfferObservers)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Real failed commands remain explicit and can retry without reloading the campsite source. */
    @Test fun failedJoinKeepsItsSourceAndRetriesExplicitly() =
        withReader { reader ->
            associated(first)
            lotteries.joinHandler = { _, _ -> throw IOException("offline") }
            reader.updateCampsite(site)
            runCurrent()
            reader.join(first.id)
            runCurrent()
            assertEquals(LotteryMembershipActionError.FAILED, content(reader).participation.error)
            assertEquals(listOf(first.id), ids(reader))
            lotteries.joinHandler = { _, _ -> }
            reader.join(first.id)
            runCurrent()
            assertEquals(2, lotteries.events.size)
            assertEquals(1, lotteries.associationRequests.size)
        }

    /** Submitted withdrawal exposes failed-source retry while retaining the no-resubmit guard. */
    @Test fun withdrawalAndMembershipRetryUseTheSameSharedCommand() =
        withReader { reader ->
            associated(first)
            lotteries.entries.value = listOf(entry(first, EntryState.WAITING))
            lotteries.withdrawHandler = { _, _ -> }
            reader.updateCampsite(site)
            runCurrent()
            reader.requestLeave(first.id)
            reader.dismissLeave(first.id)
            assertFalse(content(reader).participation.isConfirmingLeave)
            assertTrue(lotteries.events.isEmpty())
            reader.requestLeave(first.id)
            reader.confirmLeave(first.id)
            runCurrent()
            lotteries.entrantFailure.value = IOException("offline")
            runCurrent()
            assertEquals(LotteryMembershipRead.Failure, content(reader).membership)
            assertFalse(content(reader).participation.isConfirmingLeave)
            assertEquals(LotteryMembershipAction.LEAVE, content(reader).participation.action)
            reader.confirmLeave(first.id)
            lotteries.entries.value = listOf(entry(first, EntryState.WITHDRAWN))
            lotteries.entrantFailure.value = null
            reader.retryMembership()
            runCurrent()
            assertTrue(content(reader).participation.canJoin)
            assertNull(content(reader).participation.action)
            assertEquals(listOf("withdraw:draw:entrant"), lotteries.events)
            assertEquals(1, lotteries.associationRequests.size)
        }

    /** Only the visible choices child owns scoped allocation reads, preserving independent rows. */
    @Test fun offersAreVisibleScopedAndDoNotDropMissingAllocations() =
        withReader { reader ->
            associated(first, second)
            val offer = LotteryOffer(site.id, listOf(CampingFixtures.arrival))
            lotteries.liveOffers.value =
                mapOf(
                    first.id to
                        listOf(
                            LotteryOffer(
                                "foreign-site",
                                listOf(CampingFixtures.arrival.plusDays(2)),
                            ),
                            offer,
                        ),
                    second.id to
                        listOf(
                            LotteryOffer(
                                "foreign-site",
                                listOf(CampingFixtures.arrival.plusDays(1)),
                            ),
                        ),
                )
            reader.updateCampsite(site)
            runCurrent()
            assertEquals(0, lotteries.activeOfferObservers)
            reader.setChoicesVisible(true)
            runCurrent()
            assertEquals(2, lotteries.activeOfferObservers)
            assertEquals(CampsiteLotteryOfferRead.Ready(offer), content(reader).choices[0].offer)
            assertEquals(CampsiteLotteryOfferRead.Unavailable, content(reader).choices[1].offer)
            reader.setChoicesVisible(false)
            runCurrent()
            assertEquals(0, lotteries.activeOfferObservers)
            assertEquals(1, lotteries.activeAssociationObservers)
            assertEquals(listOf(first.id, second.id), ids(reader))
        }

    /** A failed allocation retries only that row; the association and other row remain live. */
    @Test fun offerFailureIsIndependentAndRetryIsScoped() =
        withReader { reader ->
            associated(first, second)
            lotteries.offerFailures.value =
                mapOf(second.id to IOException("one allocation offline"))
            reader.updateCampsite(site)
            reader.setChoicesVisible(true)
            runCurrent()
            assertEquals(CampsiteLotteryOfferRead.Unavailable, content(reader).choices[0].offer)
            assertEquals(CampsiteLotteryOfferRead.Failure, content(reader).choices[1].offer)
            lotteries.offerFailures.value = emptyMap()
            reader.retryOffer(second.id)
            runCurrent()
            assertEquals(CampsiteLotteryOfferRead.Unavailable, content(reader).choices[1].offer)
            assertEquals(1, lotteries.observedOfferIds.count { it == first.id })
            assertEquals(2, lotteries.observedOfferIds.count { it == second.id })
            assertEquals(1, lotteries.associationRequests.size)
        }

    /** Retiring a parent releases every dependent source and allows fresh read-only recovery. */
    @Test fun parentRetirementClosesChildrenWithoutReplayingAWrite() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            reader.setChoicesVisible(true)
            runCurrent()
            reader.updateCampsite(null)
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Unavailable, reader.state.value)
            assertEquals(0, lotteries.activeAssociationObservers)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertEquals(0, lotteries.activeOfferObservers)
            reader.join(first.id)
            reader.updateCampsite(site)
            runCurrent()
            assertEquals(listOf(first.id), ids(reader))
            assertEquals(2, lotteries.associationRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** A noncooperative retired association cannot overwrite a later parent incarnation. */
    @Test fun lateRetiredAssociationCannotReplaceCurrentChoices() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            val repository =
                object : LotteryRepository by lotteries {
                    /** Holds only the first incarnation outside cancellation.
                     * @param campsiteId Fixed saved identity.
                     */
                    override fun observePublishedForCampsite(
                        campsiteId: String,
                    ): Flow<List<Lottery>> =
                        flow {
                            calls++
                            if (calls == 1) {
                                withContext(NonCancellable) { gate.await() }
                                emit(listOf(first))
                            } else {
                                emit(listOf(second))
                            }
                        }
                }
            val reader = reader(repository = repository)
            try {
                reader.updateCampsite(site)
                runCurrent()
                reader.updateCampsite(null)
                reader.updateCampsite(site)
                runCurrent()
                assertEquals(listOf(second.id), ids(reader))
                gate.complete(Unit)
                runCurrent()
                assertEquals(listOf(second.id), ids(reader))
            } finally {
                gate.complete(Unit)
                reader.close()
            }
        }

    /** Graph closure cannot be undone by stale route callbacks. */
    @Test fun closedControllerCannotRestartAnySource() =
        withReader { reader ->
            associated(first)
            reader.updateCampsite(site)
            runCurrent()
            reader.close()
            reader.updateCampsite(site)
            reader.setChoicesVisible(true)
            reader.retry()
            reader.retryMembership()
            reader.refreshTime()
            reader.join(first.id)
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Unavailable, reader.state.value)
            assertEquals(0, lotteries.activeAssociationObservers)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Supplies associations without implementing their persistence query.
     * @param rows Explicit scoped server facts.
     */
    private fun associated(vararg rows: Lottery) {
        lotteries.associations.value = mapOf(site.id to rows.toList())
    }

    /** Builds exact own-entry facts for the saved target.
     * @param lottery Actual referenced parent.
     * @param state Persisted membership state.
     */
    private fun entry(
        lottery: Lottery,
        state: EntryState,
    ): LotteryEntry =
        LotteryEntry(
            lottery.id,
            "entrant",
            state,
            CampingFixtures.now,
            CampingFixtures.now,
        )

    /** Requires a confirmed association outcome.
     * @param reader Actual scoped owner under test.
     */
    private fun content(reader: CampsiteLotteriesController): CampsiteLotteriesUiState.Content {
        val state = reader.state.value
        assertTrue(
            "Expected confirmed campsite lotteries, got $state",
            state is CampsiteLotteriesUiState.Content,
        )
        return state as CampsiteLotteriesUiState.Content
    }

    /** Reads actual displayed identities in their preserved order.
     * @param reader Actual scoped owner under test.
     */
    private fun ids(reader: CampsiteLotteriesController): List<String> =
        content(reader).choices.map { it.lottery.id }

    /** Constructs a child whose work is bounded by this test's parent scope.
     * @param context Explicit saved caller capability.
     * @param repository Controlled query dependency.
     */
    private fun TestScope.reader(
        context: CampsiteDetailContext = CampsiteDetailContext.VIEW,
        repository: LotteryRepository = lotteries,
    ): CampsiteLotteriesController =
        CampsiteLotteriesController(
            "entrant",
            site.id,
            context,
            repository,
            clock,
            backgroundScope,
        )

    /** Always retires child jobs before the virtual test scope drains.
     * @param body Observable behavior contract.
     */
    private fun withReader(body: suspend TestScope.(CampsiteLotteriesController) -> Unit) =
        runTest {
            val reader = reader()
            try {
                body(reader)
            } finally {
                reader.close()
                runCurrent()
            }
        }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.YearMonth

/** The saved campsite retains one parent while its lottery child owns independent work. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteLotteryEntryTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = CampingFixtures.campsite()
    private val lottery = CampingFixtures.lottery()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val images = FakeImageRepository()
    private val lotteries =
        FakeLotteryRepository().apply {
            associations.value = mapOf(site.id to listOf(lottery))
        }
    private val clock = MutableClock(CampingFixtures.now.plusSeconds(90))

    /** Retires the real graph owner's child jobs after each test. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** The registered entrant parent supplies the actual listing before any lottery query. */
    @Test fun parentCreatesOnlyScopedParticipationReaders() =
        withScope {
            val model = model()
            assertEquals(CampsiteLotteriesUiState.Unavailable, model.lotteryState.value)
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            assertTrue(content(model).participation.canJoin)
            assertEquals(listOf(site.id), lotteries.associationRequests)
            assertEquals(listOf("entrant"), lotteries.entrantRequests)
            assertEquals(listOf(site.id), sites.observedIds)
        }

    /** Section navigation retains listing/photo/month and the same membership command owner. */
    @Test fun sectionReturnKeepsTheSavedParentAndPendingCommand() =
        withScope {
            val model = model()
            lotteries.joinHandler = { _, _ -> }
            runCurrent()
            model.joinLottery(lottery.id)
            runCurrent()
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            val month = YearMonth.of(2027, 8)
            model.changeMonth(month)
            model.selectSection(CampsiteDetailSection.OVERVIEW)
            runCurrent()
            assertEquals(month, model.month.value)
            assertEquals(LotteryMembershipAction.JOIN, content(model).participation.action)
            model.joinLottery(lottery.id)
            runCurrent()
            assertEquals(1, sites.observedIds.size)
            assertEquals(1, images.observedTargets.size)
            assertEquals(1, lotteries.associationRequests.size)
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
        }

    /** A sectional lottery failure does not hide listing/image facts or retry the parent. */
    @Test fun lotteryRetryDoesNotReplaceTheCampsiteReader() =
        withScope {
            lotteries.associationFailure.value = IOException("lottery offline")
            val model = model()
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Failure, model.lotteryState.value)
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            lotteries.associationFailure.value = null
            model.retryLotteries()
            runCurrent()
            assertTrue(content(model).participation.canJoin)
            assertEquals(1, sites.observedIds.size)
            assertEquals(1, images.observedTargets.size)
            assertEquals(2, lotteries.associationRequests.size)
        }

    /** Parent source failure retires the child; explicit parent retry restores visible rows. */
    @Test fun parentFailureReleasesAndRecoversAllLotteryChildren() =
        withScope {
            lotteries.liveOffers.value =
                mapOf(
                    lottery.id to
                        listOf(
                            LotteryOffer(
                                site.id,
                                listOf(CampingFixtures.arrival),
                            ),
                        ),
                )
            val model = model()
            model.setLotteryChoicesVisible(true)
            runCurrent()
            assertEquals(1, lotteries.activeOfferObservers)
            sites.streamFailure.value = IOException("site offline")
            runCurrent()
            assertEquals(CampsiteDetailUiState.Failure, model.state.value)
            assertEquals(CampsiteLotteriesUiState.Unavailable, model.lotteryState.value)
            assertEquals(0, lotteries.activeAssociationObservers)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertEquals(0, lotteries.activeOfferObservers)
            sites.streamFailure.value = null
            model.retry()
            runCurrent()
            assertTrue(content(model).choices.isNotEmpty())
            assertEquals(1, lotteries.activeOfferObservers)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Owner preview never acquires registration behavior even when ownership matches. */
    @Test fun previewRemainsReadOnly() =
        withScope {
            sites.records.value = listOf(site.copy(ownerId = "entrant"))
            val model = model(CampsiteDetailContext.PREVIEW)
            runCurrent()
            model.joinLottery(lottery.id)
            model.requestLeaveLottery(lottery.id)
            model.confirmLeaveLottery(lottery.id)
            model.setLotteryChoicesVisible(true)
            runCurrent()
            assertEquals(CampsiteLotteriesUiState.Unavailable, model.lotteryState.value)
            assertTrue(lotteries.associationRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Foreground refresh changes current eligibility without rebuilding the saved parent. */
    @Test fun foregroundRefreshUsesTheCurrentRegistrationWindow() =
        withScope {
            val model = model()
            runCurrent()
            clock.advance(Duration.ofSeconds(30))
            model.refreshTime()
            runCurrent()
            assertTrue(content(model).choices.isEmpty())
            assertEquals(1, sites.observedIds.size)
            assertEquals(1, lotteries.associationRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Clearing the saved graph releases queries and cannot replay an acknowledged join. */
    @Test fun graphClearRetiresItsLotteryReadersAndCommands() =
        withScope {
            val model = model()
            lotteries.joinHandler = { _, _ -> }
            runCurrent()
            model.joinLottery(lottery.id)
            runCurrent()
            lotteries.entries.value =
                listOf(
                    LotteryEntry(
                        lottery.id,
                        "entrant",
                        EntryState.WAITING,
                        clock.instant(),
                        clock.instant(),
                    ),
                )
            runCurrent()
            store.clear()
            model.joinLottery(lottery.id)
            runCurrent()
            assertEquals(0, lotteries.activeAssociationObservers)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertEquals(listOf("join:draw:entrant"), lotteries.events)
        }

    /** Requires the actual association success from the parent's exposed state.
     * @param model Saved graph owner.
     */
    private fun content(model: CampsiteDetailViewModel): CampsiteLotteriesUiState.Content {
        val state = model.lotteryState.value
        assertTrue(
            "Expected confirmed campsite lotteries, got $state",
            state is CampsiteLotteriesUiState.Content,
        )
        return state as CampsiteLotteriesUiState.Content
    }

    /** Retires the saved parent before virtual time drains its registration wait.
     * @param body Actual saved-parent contract.
     */
    private fun withScope(body: suspend TestScope.() -> Unit) =
        runTest {
            try {
                body()
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Constructs a tracked production owner with independently controlled sources.
     * @param context Actual saved graph presentation.
     */
    private fun model(
        context: CampsiteDetailContext = CampsiteDetailContext.VIEW,
    ): CampsiteDetailViewModel =
        CampsiteDetailViewModel(
            "entrant",
            site.id,
            context,
            sites,
            images,
            FakeAccountRepository(),
            FakeReviewRepository(),
            SavedStateHandle(),
            clock,
            lotteries,
        ).also { store.put("detail", it) }
}

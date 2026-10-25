package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** First saved-owner reader contracts; later lifecycle operations extend the same owner. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val clock = MutableClock(CampingFixtures.now)
    private val record = CampingFixtures.lottery("saved", "account-1").copy(publishedAt = null)
    private val site = sampleCampsite("A")
    private val sites = FakeCampsiteRepository(listOf(site))
    private val lotteries = FakeLotteryRepository(listOf(record))

    /** Cancels every live reader created by the test. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /**
     * Draft continuation uses current owner/lifecycle facts, and confirmed empty offers are
     * content.
     */
    @Test fun savedDraftIsReadableAndPublicationRemovesContinuation() =
        runTest {
            try {
                val model = model()
                runCurrent()
                assertTrue(model.state.value is LotteryDetailUiState.Content)
                val draft = model.state.value as LotteryDetailUiState.Content
                assertEquals(record, draft.lottery)
                assertTrue(draft.canContinue)
                assertEquals(LotteryOffersState.Content(emptyList()), draft.offers)
                lotteries.records.value = listOf(record.copy(publishedAt = Instant.EPOCH))
                runCurrent()
                assertFalse((model.state.value as LotteryDetailUiState.Content).canContinue)
                assertTrue(lotteries.events.isEmpty())
                store.clear()
            } finally {
                store.clear()
            }
        }

    /** All saved allocations remain present even when a referenced campsite is missing. */
    @Test fun offersKeepTheirIdentityAndActualLabels() =
        runTest {
            try {
                val first =
                    io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer(
                        "A",
                        listOf(CampingFixtures.arrival),
                    )
                val missing = first.copy(campsiteId = "missing")
                lotteries.liveOffers.value = mapOf("saved" to listOf(missing, first))
                val model = model()
                runCurrent()
                assertTrue(model.state.value is LotteryDetailUiState.Content)
                assertEquals(
                    LotteryOffersState.Content(
                        listOf(
                            LotteryOfferSummary(first, site.details.name, site.details.timeZone),
                            LotteryOfferSummary(missing, null, null),
                        ),
                    ),
                    (model.state.value as LotteryDetailUiState.Content).offers,
                )
            } finally {
                store.clear()
            }
        }

    /** Label-source failure stays sectional and does not discard or replace the parent record. */
    @Test fun failedOffersCanBeRetriedWithoutLosingTheSavedParent() =
        runTest {
            try {
                lotteries.liveOffers.value =
                    mapOf(
                        "saved" to
                            listOf(
                                io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer(
                                    "A",
                                    listOf(CampingFixtures.arrival),
                                ),
                            ),
                    )
                sites.readFailure = IOException("labels offline")
                val model = model()
                runCurrent()
                assertTrue(model.state.value is LotteryDetailUiState.Content)
                assertEquals(
                    LotteryOffersState.Failure,
                    (model.state.value as LotteryDetailUiState.Content).offers,
                )
                sites.readFailure = null
                model.retryOffers()
                model.retryOffers()
                runCurrent()
                assertTrue(
                    (model.state.value as LotteryDetailUiState.Content).offers is
                        LotteryOffersState.Content,
                )
                assertEquals(1, lotteries.activeRecordObservers)
                assertEquals(1, lotteries.activeOfferObservers)
                store.clear()
                runCurrent()
                assertEquals(0, lotteries.activeRecordObservers)
                assertEquals(0, lotteries.activeOfferObservers)
            } finally {
                store.clear()
            }
        }

    /** Missing and foreign records stay unavailable; removal retires child observations. */
    @Test fun unavailableParentRetiresTheOfferReader() =
        runTest {
            try {
                val model = model()
                runCurrent()
                lotteries.records.value = listOf(record.copy(ownerId = "foreign"))
                runCurrent()
                assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
                assertEquals(0, lotteries.activeOfferObservers)
                lotteries.records.value = emptyList()
                runCurrent()
                assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
            } finally {
                store.clear()
            }
        }

    /**
     * Parent observation errors are explicit and retry replaces the failed listener exactly
     * once.
     */
    @Test fun failedParentObservationCanBeRetried() =
        runTest {
            try {
                lotteries.recordFailure.value = IOException("offline")
                val model = model()
                runCurrent()
                assertEquals(LotteryDetailUiState.Failure, model.state.value)
                assertEquals(0, lotteries.activeOfferObservers)
                lotteries.recordFailure.value = null
                model.retry()
                model.retry()
                runCurrent()
                assertTrue(model.state.value is LotteryDetailUiState.Content)
                assertEquals(1, lotteries.activeRecordObservers)
                assertEquals(1, lotteries.activeOfferObservers)
            } finally {
                store.clear()
            }
        }

    /** Window wakeups recalculate UI without repository writes or fabricated draw completion. */
    @Test fun deadlineWakeupsUseCurrentClockAndConfirmedDrawFacts() =
        runTest {
            try {
                val published = record.copy(publishedAt = record.createdAt)
                lotteries.records.value = listOf(published)
                val model = model()
                runCurrent()
                assertEquals(
                    LotteryLifecycle.UPCOMING,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                clock.advance(Duration.ofSeconds(60))
                advanceTimeBy(60000)
                runCurrent()
                assertEquals(
                    LotteryLifecycle.REGISTRATION_OPEN,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                clock.advance(Duration.ofSeconds(60))
                advanceTimeBy(60000)
                runCurrent()
                assertEquals(
                    LotteryLifecycle.DRAW_PENDING,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                lotteries.records.value = listOf(published.copy(drawnAt = clock.instant()))
                runCurrent()
                assertEquals(
                    LotteryLifecycle.BOOKING_OPEN,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                clock.advance(Duration.ofSeconds(120))
                advanceTimeBy(120000)
                runCurrent()
                assertEquals(
                    LotteryLifecycle.CLOSED,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                assertTrue(lotteries.events.isEmpty())
            } finally {
                store.clear()
            }
        }

    /**
     * Returning to foreground immediately re-evaluates a changed clock without an artificial
     * draw.
     */
    @Test fun foregroundRefreshDoesNotWaitForTheOldTimer() =
        runTest {
            try {
                lotteries.records.value = listOf(record.copy(publishedAt = record.createdAt))
                val model = model()
                runCurrent()
                clock.advance(Duration.ofSeconds(500))
                model.refreshTime()
                assertEquals(
                    LotteryLifecycle.DRAW_PENDING,
                    (model.state.value as LotteryDetailUiState.Content).lifecycle,
                )
                assertTrue(lotteries.events.isEmpty())
            } finally {
                store.clear()
            }
        }

    /** Removing or retiring the saved owner cancels its scheduled refresh as well as listeners. */
    @Test fun retiredParentCannotBeReplacedByALateDeadline() =
        runTest {
            try {
                lotteries.records.value = listOf(record.copy(publishedAt = record.createdAt))
                val model = model()
                runCurrent()
                lotteries.records.value = emptyList()
                runCurrent()
                clock.advance(Duration.ofSeconds(500))
                advanceTimeBy(500000)
                runCurrent()
                assertEquals(LotteryDetailUiState.Unavailable, model.state.value)
                assertEquals(0, lotteries.activeOfferObservers)
                store.clear()
                runCurrent()
                assertTrue(lotteries.events.isEmpty())
            } finally {
                store.clear()
            }
        }

    /** Creates the actual saved-ID reader with controlled source boundaries. */
    private fun model(): LotteryDetailViewModel =
        LotteryDetailViewModel("account-1", "saved", lotteries, sites, clock)
            .also { store.put("detail", it) }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Read-only child contracts extend the saved listing reader without duplicating persistence. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteDetailSectionsTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val accounts = FakeAccountRepository(sampleAccount())
    private val images = FakeImageRepository()
    private val reviews = FakeReviewRepository()
    private val clock = Clock.fixed(Instant.parse("2027-05-01T00:30:00Z"), ZoneOffset.UTC)
    private val april = YearMonth.of(2027, 4)

    /** Releases the real graph-owned subscriptions after each contract. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** Supplemental failures are independent and their retries never restart the saved listing. */
    @Test fun optionalFailuresRetryIndependently() =
        runTest {
            accounts.streamFailure.value = IOException("owner offline")
            reviews.failure(site.id).value = IOException("ratings offline")
            val model = model()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            assertEquals(CampsiteOwnerState.Failure, model.ownerState.value)
            assertEquals(RatingState.Failure, model.ratingState.value)
            accounts.streamFailure.value = null
            model.retryOwner()
            model.retryOwner()
            runCurrent()
            assertEquals(CampsiteOwnerState.Ready("Alex Chen"), model.ownerState.value)
            assertEquals(RatingState.Failure, model.ratingState.value)
            reviews.failure(site.id).value = null
            model.retryRatings()
            model.retryRatings()
            runCurrent()
            assertEquals(RatingState.Ready(RatingSummary(0, null)), model.ratingState.value)
            assertEquals(listOf(site.id), sites.observedIds)
            assertEquals(1, accounts.activeObservers)
            assertEquals(1, reviews.activeObservers)
            assertEquals(
                0,
                sites.writeCalls + accounts.writes + images.replaceCalls + images.deleteCalls,
            )
        }

    /** The summary follows actual rating emissions and never uses a fixed example score. */
    @Test fun actualRatingsAndOwnerUpdatesRemainLive() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(RatingState.Ready(RatingSummary(0, null)), model.ratingState.value)
            reviews.ratings(site.id).value = listOf(review("a", 2), review("b", 5))
            accounts.account.value = sampleAccount().copy(name = "Updated owner")
            sites.records.value = listOf(site.copy(details = site.details.copy(name = "New name")))
            runCurrent()
            assertEquals(RatingState.Ready(RatingSummary(2, 3.5)), model.ratingState.value)
            assertEquals(CampsiteOwnerState.Ready("Updated owner"), model.ownerState.value)
            assertEquals(1, accounts.observations)
            assertEquals(listOf(site.id), reviews.observedIds)
        }

    /** Absent, removed and mismatched profiles stay explicit without hiding the campsite. */
    @Test fun missingOwnerIsNotAFailedOrFabricatedName() =
        runTest {
            val model = model()
            runCurrent()
            for (owner in listOf(
                null,
                sampleAccount().copy(id = "wrong"),
                sampleAccount().copy(removal = Removal(Instant.EPOCH, "admin")),
            )) {
                accounts.account.value = owner
                runCurrent()
                assertEquals(CampsiteOwnerState.Unavailable, model.ownerState.value)
                assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            }
            accounts.account.value = sampleAccount()
            runCurrent()
            assertEquals(CampsiteOwnerState.Ready("Alex Chen"), model.ownerState.value)
        }

    /** Ownership changes replace only the old owner reader and reject its mismatched label. */
    @Test fun changedOwnerReplacesOnlyItsSupplementalReader() =
        runTest {
            val model = model()
            runCurrent()
            sites.records.value = listOf(site.copy(ownerId = "owner-2"))
            runCurrent()
            assertEquals(CampsiteOwnerState.Unavailable, model.ownerState.value)
            accounts.account.value = sampleAccount().copy(id = "owner-2", name = "Second owner")
            runCurrent()
            assertEquals(CampsiteOwnerState.Ready("Second owner"), model.ownerState.value)
            assertEquals(1, accounts.activeObservers)
            assertEquals(listOf(site.id), reviews.observedIds)
            assertEquals(1, images.observedTargets.size)
        }

    /**
     * Calendar work starts only for Availability and uses the campsite zone at the clock
     * boundary.
     */
    @Test fun onlyTheVisibleAvailabilityMonthIsObserved() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(april, model.month.value)
            assertTrue(sites.observedCalendars.isEmpty())
            model.selectSection(CampsiteDetailSection.ABOUT)
            runCurrent()
            assertTrue(sites.observedCalendars.isEmpty())
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(
                listOf(Triple(site.id, april.atDay(1), april.plusMonths(1).atDay(1))),
                sites.observedCalendars,
            )
            assertEquals(CalendarInventoryRead.Content(emptyList()), model.inventory.value)
            val may = april.plusMonths(1)
            model.changeMonth(may)
            model.changeMonth(may)
            runCurrent()
            assertEquals(2, sites.observedCalendars.size)
            assertEquals(1, sites.activeCalendarObservers)
            model.selectSection(CampsiteDetailSection.MAP)
            runCurrent()
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(CalendarInventoryRead.Loading, model.inventory.value)
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(may, model.month.value)
            assertEquals(3, sites.observedCalendars.size)
        }

    /** Unknown and failed nights never become an all-unavailable successful calendar. */
    @Test fun calendarFailureAndUnresolvedReadRemainDistinct() =
        runTest {
            sites.calendarHandler = { _, _, _ -> flow { awaitCancellation() } }
            val model = model()
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(CalendarInventoryRead.Loading, model.inventory.value)
            model.selectSection(CampsiteDetailSection.OVERVIEW)
            runCurrent()
            sites.calendarHandler = null
            sites.calendarFailure.value = IOException("calendar offline")
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(CalendarInventoryRead.Failure, model.inventory.value)
            assertEquals(CampsiteDetailUiState.Content(site), model.state.value)
            sites.calendarFailure.value = null
            sites.calendarNights.value = listOf(CalendarNight(april.atDay(3), true))
            model.retryInventory()
            model.retryInventory()
            runCurrent()
            assertEquals(
                CalendarInventoryRead.Content(sites.calendarNights.value),
                model.inventory.value,
            )
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Primitive restoration retains the chosen month, including across source retries. */
    @Test fun monthSurvivesRecreationAndListingRetry() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(saved = saved)
            first.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            val chosen = YearMonth.of(2028, 2)
            first.changeMonth(chosen)
            runCurrent()
            store.clear()
            runCurrent()
            val restored = model(saved = saved)
            restored.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(chosen, restored.month.value)
            sites.streamFailure.value = IOException("parent offline")
            runCurrent()
            assertEquals(0, sites.activeCalendarObservers)
            sites.streamFailure.value = null
            restored.retry()
            runCurrent()
            assertEquals(chosen, restored.month.value)
            assertEquals(
                Triple(site.id, chosen.atDay(1), chosen.plusMonths(1).atDay(1)),
                sites.observedCalendars.last(),
            )
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Parent removal clears every supplemental source; a later valid record starts one of each. */
    @Test fun unavailableParentReleasesAndRestartsChildren() =
        runTest {
            val model = model()
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(1, accounts.activeObservers)
            assertEquals(1, reviews.activeObservers)
            assertEquals(1, sites.activeCalendarObservers)
            sites.records.value = emptyList()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Unavailable, model.state.value)
            assertEquals(
                0,
                accounts.activeObservers + reviews.activeObservers +
                    sites.activeCalendarObservers + images.activeObservers,
            )
            sites.records.value = listOf(site)
            runCurrent()
            assertEquals(1, accounts.activeObservers)
            assertEquals(1, reviews.activeObservers)
            assertEquals(1, sites.activeCalendarObservers)
            store.clear()
            runCurrent()
            assertEquals(
                0,
                accounts.activeObservers + reviews.activeObservers +
                    sites.activeCalendarObservers + images.activeObservers + sites.activeObservers,
            )
        }

    /**
     * Management avoids public supplemental reads; preview accepts owned drafts and
     * inspection history.
     */
    @Test fun contextsKeepTheirOwnReadOnlyPresentationRules() =
        runTest {
            val manage = model(CampsiteDetailContext.MANAGE)
            manage.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertEquals(
                0,
                accounts.observations + reviews.observedIds.size +
                    sites.observedCalendars.size,
            )
            store.clear()
            runCurrent()
            val draft = site.copy(publishedAt = null)
            sites.records.value = listOf(draft)
            val preview = model(CampsiteDetailContext.PREVIEW)
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(draft), preview.state.value)
            store.clear()
            runCurrent()
            val entrant = model()
            runCurrent()
            assertEquals(CampsiteDetailUiState.Unavailable, entrant.state.value)
            store.clear()
            runCurrent()
            val removed = draft.copy(removal = Removal(Instant.EPOCH, "admin"))
            sites.records.value = listOf(removed)
            val inspect = model(CampsiteDetailContext.INSPECT)
            runCurrent()
            assertEquals(CampsiteDetailUiState.Content(removed), inspect.state.value)
            assertEquals(0, sites.writeCalls + accounts.writes)
        }

    /**
     * Retired month callbacks cannot overwrite the active range or repopulate a departed
     * section.
     */
    @Test fun retiredCalendarCallbacksCannotReplaceCurrentInventory() =
        runTest {
            var retired: FlowCollector<List<CalendarNight>>? = null
            val current =
                MutableStateFlow(listOf(CalendarNight(april.plusMonths(1).atDay(2), true)))
            val repository =
                object : CampsiteRepository by sites {
                    /** Retains the old raw callback to model a late SDK emission.
                     * @param id Actual listing identity.
                     * @param start Inclusive first night.
                     * @param endExclusive Exclusive range endpoint.
                     */
                    override fun observeCalendar(
                        id: String,
                        start: LocalDate,
                        endExclusive: LocalDate,
                    ): Flow<List<CalendarNight>> =
                        object : Flow<List<CalendarNight>> {
                            /**
                             * Keeps the first callback after
                             * cancellation for the stale-emission
                             * assertion.
                             * @param collector Detail's active range collector.
                             */
                            override suspend fun collect(
                                collector: FlowCollector<List<CalendarNight>>,
                            ) {
                                if (start == april.atDay(1)) {
                                    retired = collector
                                    collector.emit(listOf(CalendarNight(start, true)))
                                    awaitCancellation()
                                } else {
                                    current.collect(collector)
                                }
                            }
                        }
                }
            val model = model(repository = repository)
            model.selectSection(CampsiteDetailSection.AVAILABILITY)
            runCurrent()
            assertNotNull("Availability must subscribe to its first month", retired)
            model.changeMonth(april.plusMonths(1))
            runCurrent()
            checkNotNull(retired).emit(emptyList())
            assertEquals(CalendarInventoryRead.Content(current.value), model.inventory.value)
            model.selectSection(CampsiteDetailSection.RULES)
            runCurrent()
            checkNotNull(retired).emit(emptyList())
            assertEquals(CalendarInventoryRead.Loading, model.inventory.value)
        }

    /** Creates a supplied rating fact without an editor or persistence simulation.
     * @param author Stable review identity.
     * @param stars Actual stars in the confirmed source.
     */
    private fun review(
        author: String,
        stars: Long,
    ): Review =
        Review(site.id, author, stars, null, Instant.EPOCH, Instant.EPOCH)

    /** Creates one tracked saved reader; contexts never share ViewModel instances.
     * @param context Fixed caller presentation.
     * @param saved Primitive state carried across recreation.
     * @param repository Actual or adversarial listing/calendar boundary.
     */
    private fun model(
        context: CampsiteDetailContext = CampsiteDetailContext.VIEW,
        saved: SavedStateHandle = SavedStateHandle(),
        repository: CampsiteRepository = sites,
    ): CampsiteDetailViewModel =
        CampsiteDetailViewModel(
            "account-1",
            site.id,
            context,
            repository,
            images,
            accounts,
            reviews,
            saved,
            clock,
            FakeLotteryRepository(),
        ).also { store.put("detail", it) }
}

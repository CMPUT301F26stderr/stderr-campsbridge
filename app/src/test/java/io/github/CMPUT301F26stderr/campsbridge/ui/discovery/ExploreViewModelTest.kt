package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** Submitted/working state and listener ownership use fixed reserved-helper outcomes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val a =
        sampleCampsite("A").let {
            it.copy(details = it.details.copy(name = "River camp", locationLabel = "Jasper"))
        }
    private val b =
        sampleCampsite("B").let {
            it.copy(details = it.details.copy(name = "Forest camp", locationLabel = "Banff"))
        }
    private val campsites = FakeCampsiteRepository(listOf(a, b))
    private val images = FakeImageRepository()
    private val reviews = FakeReviewRepository()
    private val store = ViewModelStore()
    private var created = 0
    private val arrival = LocalDate.of(2027, 5, 14)
    private val textResults =
        mapOf(
            ("A" to "") to true,
            ("B" to "") to true,
            ("A" to "river") to true,
            ("B" to "river") to false,
        )

    /** Releases every read and verifies no operation escaped feature ownership. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, campsites.activeObservers)
        assertEquals(0, campsites.activeCalendarObservers)
        assertEquals(0, images.activeObservers)
        assertEquals(0, reviews.activeObservers)
        assertEquals(0, campsites.writeCalls)
    }

    /** Query typing never applies criteria or duplicates the published source. */
    @Test fun searchAppliesOnlyOnExplicitSubmission() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(listOf("A", "B"), ids(model))
            model.editQuery("river")
            runCurrent()
            assertEquals("", model.state.value.submittedQuery)
            assertEquals(listOf("A", "B"), ids(model))
            model.submitSearch()
            runCurrent()
            assertEquals("river", model.state.value.submittedQuery)
            assertEquals(listOf("A"), ids(model))
            assertEquals(1, campsites.publishedObservations)
            assertEquals(0, campsites.allObservations)
            assertTrue(content(model).orderingPending)
        }

    /** Cancel and Reset touch only the working draft; Apply commits validated values. */
    @Test fun filterDraftHasAnIndependentApplyCancelAndResetLifetime() =
        runTest {
            val model = model()
            runCurrent()
            model.beginFilters()
            model.editLocation(" Jasper ")
            assertEquals(listOf("A", "B"), ids(model))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals("Jasper", model.state.value.filters.locationText)
            assertNull(model.state.value.filterDraft)
            assertEquals(listOf("A"), ids(model))
            model.beginFilters()
            model.editLocation("Banff")
            model.resetFilters()
            assertEquals(FilterDraft(), model.state.value.filterDraft)
            model.cancelFilters()
            runCurrent()
            assertEquals("Jasper", model.state.value.filters.locationText)
            model.beginFilters()
            model.resetFilters()
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(DiscoveryFilters(), model.state.value.filters)
            assertEquals(listOf("A", "B"), ids(model))
        }

    /** Invalid Apply keeps applied facts and editable raw input, with zero range reads. */
    @Test fun invalidCriteriaNeverStartCalendarReads() =
        runTest {
            val model = model()
            runCurrent()
            model.beginFilters()
            model.selectStayDates(arrival, null)
            model.editGuests("0")
            assertFalse(model.applyFilters())
            assertEquals(
                FilterErrors(StayFilterError.MISSING_ENDPOINT, true),
                model.state.value.filterErrors,
            )
            assertEquals(
                "0",
                model.state.value.filterDraft!!
                    .guestText,
            )
            assertEquals(DiscoveryFilters(), model.state.value.filters)
            assertTrue(campsites.observedCalendars.isEmpty())
        }

    /** Primitive state restores both toolbar/applied criteria and an unfinished working draft. */
    @Test fun restorationDoesNotApplyTheWorkingDraft() =
        runTest {
            val saved = SavedStateHandle()
            val model = model(saved)
            runCurrent()
            model.editQuery("river")
            model.submitSearch()
            model.beginFilters()
            model.editLocation("Jasper")
            assertTrue(model.applyFilters())
            model.editQuery("unsent")
            model.beginFilters()
            model.editLocation("Banff")
            val restoredState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            store.clear()
            runCurrent()
            val restored = model(restoredState)
            runCurrent()
            assertEquals("unsent", restored.state.value.queryText)
            assertEquals("river", restored.state.value.submittedQuery)
            assertEquals("Jasper", restored.state.value.filters.locationText)
            assertEquals(
                "Banff",
                restored.state.value.filterDraft!!
                    .locationText,
            )
            assertEquals(listOf("A"), ids(restored))
            restored.cancelFilters()
            assertEquals("Jasper", restored.state.value.filters.locationText)
        }

    /** Only matched candidates read the exact occupied range, and booked nights exclude a result. */
    @Test fun requiredCalendarReadsFollowAppliedCandidatesAndRange() =
        runTest {
            val nights =
                MutableStateFlow(
                    listOf(CalendarNight(arrival, true), CalendarNight(arrival.plusDays(1), true)),
                )
            campsites.calendarHandler = { _, _, _ -> nights }
            val model = model()
            runCurrent()
            model.beginFilters()
            model.editLocation("Jasper")
            model.selectStayDates(arrival, arrival.plusDays(2))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(
                listOf(Triple("A", arrival, arrival.plusDays(2))),
                campsites.observedCalendars,
            )
            assertEquals(listOf("A"), ids(model))
            nights.value =
                listOf(
                    CalendarNight(arrival, true),
                    CalendarNight(arrival.plusDays(1), true, "claimed"),
                )
            runCurrent()
            assertTrue(ids(model).isEmpty())
            model.beginFilters()
            model.selectStayDates(arrival.plusDays(2), arrival.plusDays(3))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(1, campsites.activeCalendarObservers)
            assertEquals(
                Triple("A", arrival.plusDays(2), arrival.plusDays(3)),
                campsites.observedCalendars.last(),
            )
            model.beginFilters()
            model.resetFilters()
            model.applyFilters()
            runCurrent()
            assertEquals(0, campsites.activeCalendarObservers)
        }

    /** A retired repository callback cannot replace the current range or reopen a removed candidate. */
    @Test fun retiredCalendarEmissionCannotOverwriteCurrentResults() =
        runTest {
            var oldCollector: FlowCollector<List<CalendarNight>>? = null
            val current = MutableStateFlow(listOf(CalendarNight(arrival.plusDays(2), true)))
            val repository =
                object : CampsiteRepository by campsites {
                    /** Returns a raw controlled flow so the test can deliver a late repository callback.
                     * @param id Actual candidate identity.
                     * @param start Inclusive occupied date.
                     * @param endExclusive Exclusive checkout.
                     */
                    override fun observeCalendar(
                        id: String,
                        start: LocalDate,
                        endExclusive: LocalDate,
                    ): Flow<List<CalendarNight>> =
                        object : Flow<List<CalendarNight>> {
                            /** Tracks subscription cleanup while retaining the first retired callback.
                             * @param collector Downstream reader under test.
                             */
                            override suspend fun collect(
                                collector: FlowCollector<List<CalendarNight>>,
                            ) {
                                campsites.observedCalendars.add(Triple(id, start, endExclusive))
                                campsites.activeCalendarObservers++
                                try {
                                    if (start == arrival) {
                                        oldCollector = collector
                                        collector.emit(listOf(CalendarNight(arrival, true)))
                                        awaitCancellation()
                                    } else {
                                        current.collect(collector)
                                    }
                                } finally {
                                    campsites.activeCalendarObservers--
                                }
                            }
                        }
                }
            val model = model(repository = repository)
            runCurrent()
            model.beginFilters()
            model.editLocation("Jasper")
            model.selectStayDates(arrival, arrival.plusDays(1))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(listOf("A"), ids(model))
            val retired = checkNotNull(oldCollector)
            model.beginFilters()
            model.selectStayDates(arrival.plusDays(2), arrival.plusDays(3))
            assertTrue(model.applyFilters())
            runCurrent()
            retired.emit(emptyList())
            runCurrent()
            assertEquals(listOf("A"), ids(model))
            assertEquals(2, campsites.observedCalendars.size)
            campsites.records.value = listOf(b)
            runCurrent()
            retired.emit(listOf(CalendarNight(arrival, true)))
            runCurrent()
            assertTrue(ids(model).isEmpty())
            assertEquals(0, campsites.activeCalendarObservers)
        }

    /** An unresolved required range is loading while toolbar input remains independently editable. */
    @Test fun unresolvedRangeDoesNotClaimNoResults() =
        runTest {
            val nights = MutableSharedFlow<List<CalendarNight>>()
            campsites.calendarHandler = { _, _, _ -> nights }
            val model = model()
            runCurrent()
            model.beginFilters()
            model.editLocation("Jasper")
            model.selectStayDates(arrival, arrival.plusDays(1))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(ExploreResult.Loading, model.state.value.result)
            model.editQuery("unsent")
            nights.emit(listOf(CalendarNight(arrival, true)))
            runCurrent()
            assertEquals(listOf("A"), ids(model))
            assertEquals("unsent", model.state.value.queryText)
            assertEquals("", model.state.value.submittedQuery)
        }

    /** Required failure remains retryable and does not erase input or claim an empty result. */
    @Test fun sourceAndCalendarFailuresRetainCriteria() =
        runTest {
            val model = model()
            runCurrent()
            model.editQuery("river")
            model.submitSearch()
            campsites.streamFailure.value = IOException("offline")
            runCurrent()
            assertEquals(ExploreResult.Failure, model.state.value.result)
            assertEquals("river", model.state.value.queryText)
            campsites.streamFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(listOf("A"), ids(model))
            assertEquals(1, campsites.activeObservers)
            model.beginFilters()
            model.selectStayDates(arrival, arrival.plusDays(1))
            model.applyFilters()
            campsites.calendarFailure.value = IOException("calendar")
            runCurrent()
            assertEquals(ExploreResult.Failure, model.state.value.result)
            campsites.calendarFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(1, campsites.activeCalendarObservers)
            assertTrue(ids(model).isEmpty())
        }

    /** Optional reads follow visible result IDs; failure retains rows and independent retry. */
    @Test fun visibilityBoundsPhotoAndRatingReads() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(0, images.activeObservers)
            assertEquals(0, reviews.activeObservers)
            model.setVisibleCampsites(setOf("A", "unknown"))
            runCurrent()
            assertEquals(1, images.activeObservers)
            assertEquals(1, reviews.activeObservers)
            assertEquals(RatingState.Ready(RatingSummary(0, null)), content(model).ratings["A"])
            val time = a.createdAt
            reviews.ratings("A").value = listOf(Review("A", "entrant", 4, null, time, time))
            runCurrent()
            assertEquals(RatingState.Ready(RatingSummary(1, 4.0)), content(model).ratings["A"])
            reviews.failure("A").value = IOException("ratings")
            runCurrent()
            assertEquals(RatingState.Failure, content(model).ratings["A"])
            assertEquals(listOf("A", "B"), ids(model))
            reviews.failure("A").value = null
            model.retryRow("A")
            runCurrent()
            assertEquals(1, reviews.activeObservers)
            model.setVisibleCampsites(setOf("B"))
            runCurrent()
            assertEquals(listOf("A", "A", "B"), reviews.observedIds)
            assertEquals(1, reviews.activeObservers)
            assertEquals(1, images.activeObservers)
            campsites.records.value = listOf(a)
            runCurrent()
            assertEquals(0, reviews.activeObservers)
            assertEquals(0, images.activeObservers)
        }

    /** Pending helper dependencies never get a duplicate matching implementation. */
    @Test fun missingPredicatesAreExplicitlyPendingWithoutRangeReads() =
        runTest {
            val model = ExploreViewModel(campsites, images, reviews)
            store.put("pending", model)
            runCurrent()
            assertEquals(ExploreResult.HelperPending, model.state.value.result)
            model.beginFilters()
            model.editLocation("Jasper")
            model.selectStayDates(arrival, arrival.plusDays(1))
            assertTrue(model.applyFilters())
            runCurrent()
            assertEquals(ExploreResult.HelperPending, model.state.value.result)
            assertTrue(campsites.observedCalendars.isEmpty())
        }

    /** Caller-supplied matching and ordering results are consumed rather than recomputed. */
    @Test fun suppliedCapacityAndOrderingDecideResults() =
        runTest {
            val model =
                ExploreViewModel(
                    campsites,
                    images,
                    reviews,
                    matchesText = { _, _ -> true },
                    matchesCapacity = { _, requested -> requested == null },
                    sortCampsites = { rows -> if (rows.isEmpty()) emptyList() else listOf(b, a) },
                )
            store.put("supplied", model)
            runCurrent()
            assertEquals(listOf("B", "A"), ids(model))
            assertFalse(content(model).orderingPending)
            model.beginFilters()
            model.editGuests("3")
            model.applyFilters()
            runCurrent()
            assertTrue(ids(model).isEmpty())
        }

    /** Supplies fixed known text decisions; no reserved search/capacity algorithm is copied.
     * @param saved Primitive restoration store.
     * @param repository Controlled source, replaceable for adversarial cancellation cases.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        repository: CampsiteRepository = campsites,
    ): ExploreViewModel =
        ExploreViewModel(
            repository,
            images,
            reviews,
            saved,
            matchesText = { site, query -> checkNotNull(textResults[site.id to query]) },
            matchesCapacity = {
                _,
                requested,
                ->
                check(requested == null)
                true
            },
        ).also { store.put("model-${created++}", it) }

    /** Reads a confirmed result outcome.
     * @param model Tested owner.
     */
    private fun content(model: ExploreViewModel): ExploreResult.Content {
        val result = model.state.value.result
        assertTrue("Expected confirmed result, received $result", result is ExploreResult.Content)
        return result as ExploreResult.Content
    }

    /** Returns confirmed identities for outcome assertions.
     * @param model Tested owner.
     */
    private fun ids(model: ExploreViewModel) =
        content(model).campsites.map { it.id }
}

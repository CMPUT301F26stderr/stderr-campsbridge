package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Local setup contracts precede persistence; fixed F13 outcomes never implement its ordering
 * rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotterySetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val a = sampleCampsite("A")
    private val b =
        sampleCampsite("B").let {
            it.copy(
                details = it.details.copy(name = "Coastal camp", timeZone = "America/Vancouver"),
            )
        }
    private val sites = FakeCampsiteRepository(listOf(a, b))
    private val clock = Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC)
    private val may = YearMonth.of(2027, 5)
    private val day = may.atDay(14)

    /** Cancels every choice/range subscription after each contract. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /**
     * Unselected native dates and allocation initialize from the site zone at a UTC month
     * boundary.
     */
    @Test fun initialCalendarMonthUsesTheCampsiteZone() =
        runTest {
            val boundary = Clock.fixed(Instant.parse("2027-05-01T00:30:00Z"), ZoneOffset.UTC)
            val model =
                LotterySetupViewModel(
                    "account-1",
                    sites,
                    FakeLotteryRepository(),
                    boundary,
                    SavedStateHandle(),
                ).also { store.put("lottery", it) }
            runCurrent()
            assertNull(model.currentScheduleMonth())
            toSite(model)
            model.chooseCampsite(a.id)
            assertEquals(YearMonth.of(2027, 4), model.currentScheduleMonth())
            assertTrue(model.beginAllocation())
            assertEquals(
                YearMonth.of(2027, 4),
                model.state.value.allocation!!
                    .month,
            )
        }

    /**
     * Choices are active published records from the actual owner source; form input never
     * writes.
     */
    @Test fun choicesAreScopedAndRetainInputAcrossFailure() =
        runTest {
            sites.records.value =
                listOf(
                    a,
                    b,
                    sampleCampsite("draft", published = false),
                    a.copy(id = "removed", removal = Removal(Instant.EPOCH, "admin")),
                    sampleCampsite("foreign", ownerId = "other"),
                )
            val model = model()
            model.editTitle("  May weekends  ")
            model.editDescription("First line\n第二行".repeat(30))
            runCurrent()
            assertEquals(LotteryCampsiteChoices.Content(listOf(a, b)), model.state.value.choices)
            assertEquals(listOf("account-1"), sites.observedOwners)
            model.chooseCampsite("foreign")
            assertNull(model.state.value.draft.campsiteId)
            sites.streamFailure.value = IOException("offline")
            runCurrent()
            assertEquals(LotteryCampsiteChoices.Failure, model.state.value.choices)
            assertEquals("  May weekends  ", model.state.value.draft.title)
            assertEquals("First line\n第二行".repeat(30), model.state.value.draft.description)
            sites.streamFailure.value = null
            model.retryChoices()
            model.retryChoices()
            runCurrent()
            assertEquals(1, sites.activeObservers)
            assertEquals(0, sites.writeCalls)
        }

    /**
     * Missing prerequisites are reported at the current step without moving or inventing
     * defaults.
     */
    @Test fun localStepErrorsDoNotAdvanceTheDraft() =
        runTest {
            val model = model()
            runCurrent()
            model.next()
            assertTrue(model.state.value.errors.titleRequired)
            assertEquals(LotteryStep.BASICS, model.state.value.draft.step)
            toSite(model)
            model.next()
            assertTrue(model.state.value.errors.campsiteRequired)
            model.chooseCampsite(a.id)
            model.next()
            assertTrue(model.state.value.errors.allocationRequired)
            allocate(model)
            model.next()
            assertEquals(LotteryStep.REGISTRATION, model.state.value.draft.step)
            model.next()
            assertEquals(
                LotteryScheduleError.DATE_REQUIRED,
                model.state.value.errors.schedule[LotterySchedulePoint.REGISTRATION_OPEN],
            )
            model.editScheduleDate(LotterySchedulePoint.REGISTRATION_OPEN, may.atDay(5))
            model.next()
            assertEquals(
                LotteryScheduleError.TIME_REQUIRED,
                model.state.value.errors.schedule[LotterySchedulePoint.REGISTRATION_OPEN],
            )
            assertEquals(0, sites.writeCalls)
        }

    /** The local six-step flow builds a no-cap configuration from explicit campsite-zone values. */
    @Test fun sixStepsUseTheSelectedZoneAndUnlimitedRegistration() =
        runTest {
            val arguments = mutableListOf<List<Instant>>()
            val previous = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
                val model =
                    model(validator = { ro, rc, bo, bc ->
                        arguments.add(listOf(ro, rc, bo, bc))
                        LotteryWindowInput.Valid
                    })
                toReview(model)
                val configuration = model.state.value.configuration
                assertNotNull(configuration)
                assertNull(configuration!!.waitlistCapacity)
                assertEquals(2L, configuration.winnerCount)
                assertEquals(
                    listOf(
                        Instant.parse("2027-05-05T15:00:00Z"),
                        Instant.parse("2027-05-06T18:00:00Z"),
                        Instant.parse("2027-05-07T15:00:00Z"),
                        Instant.parse("2027-05-08T18:00:00Z"),
                    ),
                    arguments.last(),
                )
                assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
                assertEquals(0, sites.writeCalls)
            } finally {
                TimeZone.setDefault(previous)
            }
        }

    /** DST gaps and overlaps keep the actual raw input and request another unambiguous time. */
    @Test fun ambiguousLocalTimesNeverBecomeShiftedInstants() =
        runTest {
            val model = model()
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            allocate(model)
            model.next()
            for ((date, time) in listOf(
                LocalDate.of(2027, 3, 14) to LocalTime.of(2, 30),
                LocalDate.of(2027, 11, 7) to LocalTime.of(1, 30),
            )) {
                model.editScheduleDate(LotterySchedulePoint.REGISTRATION_OPEN, date)
                model.editScheduleTime(LotterySchedulePoint.REGISTRATION_OPEN, time)
                model.next()
                assertEquals(
                    LotteryScheduleError.AMBIGUOUS_TIME,
                    model.state.value.errors.schedule[LotterySchedulePoint.REGISTRATION_OPEN],
                )
                assertEquals(
                    LotteryLocalDateTime(date, time),
                    model.state.value.draft.schedule[LotterySchedulePoint.REGISTRATION_OPEN],
                )
                assertEquals(LotteryStep.REGISTRATION, model.state.value.draft.step)
            }
        }

    /**
     * Missing F13 and each supplied error stay explicit without a substitute ordering
     * algorithm.
     */
    @Test fun reviewUsesTheReservedWindowOutcome() =
        runTest {
            val pending = model(validator = null)
            toReview(pending)
            assertTrue(pending.state.value.errors.windowsPending)
            assertNull(pending.state.value.configuration)
            for (error in listOf(
                LotteryWindowInput.InvalidRegistration,
                LotteryWindowInput.BookingBeforeRegistrationClose,
                LotteryWindowInput.InvalidBooking,
            )) {
                val model = model(validator = { _, _, _, _ -> error })
                toReview(model)
                assertEquals(error, model.state.value.errors.windows)
                assertFalse(model.state.value.errors.windowsPending)
                assertNull(model.state.value.configuration)
            }
        }

    /**
     * Winner parsing uses the existing positive-count contract and review edits preserve
     * other input.
     */
    @Test fun winnerFeedbackAndReviewEditsAreLocal() =
        runTest {
            val model = model()
            toReview(model)
            model.editStep(LotteryStep.WINNERS)
            for (text in listOf("", "0", "1.5", "9223372036854775808")) {
                model.editWinnerCount(text)
                model.next()
                assertEquals(LotteryStep.WINNERS, model.state.value.draft.step)
                assertNotNull(model.state.value.errors.winnerCount)
            }
            model.editWinnerCount(" 3 ")
            model.next()
            assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
            assertEquals(
                3L,
                model.state.value.configuration!!
                    .winnerCount,
            )
            model.editStep(LotteryStep.BASICS)
            model.editTitle(" Été 日本 ")
            model.editDescription("Preserve\nline breaks")
            model.next()
            assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
            assertEquals(" Été 日本 ", model.state.value.draft.title)
            assertEquals(setOf(day), model.state.value.draft.nights)
        }

    /**
     * Cancel preserves applied nights; booked/unoffered dates cannot be added as working
     * selection.
     */
    @Test fun workingAllocationAppliesOrCancelsWithoutChangingTheParentEarly() =
        runTest {
            val model = model()
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            sites.calendarNights.value =
                listOf(
                    CalendarNight(day, true),
                    CalendarNight(day.plusDays(1), true),
                    CalendarNight(day.plusDays(2), true, "booking"),
                    CalendarNight(day.plusDays(3), false),
                )
            assertTrue(model.beginAllocation())
            assertFalse(model.applyAllocation())
            runCurrent()
            for (date in listOf(
                day,
                day.plusDays(2),
                day.plusDays(3),
            )) {
                model.toggleAllocationDate(date)
            }
            assertEquals(
                setOf(day),
                model.state.value.allocation!!
                    .selected,
            )
            assertTrue(
                model.state.value.draft.nights
                    .isEmpty(),
            )
            assertTrue(model.applyAllocation())
            assertEquals(setOf(day), model.state.value.draft.nights)
            assertTrue(model.beginAllocation())
            runCurrent()
            model.toggleAllocationDate(day.plusDays(1))
            model.cancelAllocation()
            runCurrent()
            assertEquals(setOf(day), model.state.value.draft.nights)
            assertEquals(0, sites.activeCalendarObservers)
            assertTrue(model.beginAllocation())
            runCurrent()
            assertEquals(
                setOf(day),
                model.state.value.allocation!!
                    .selected,
            )
            model.toggleAllocationDate(day)
            assertFalse(model.applyAllocation())
            assertTrue(
                model.state.value.allocation!!
                    .invalidSelection,
            )
        }

    /** A selected night becoming booked is explicit, removable and never silently applied. */
    @Test fun invalidatedWorkingNightMustBeRemovedBeforeApply() =
        runTest {
            val model = model()
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            sites.calendarNights.value =
                listOf(CalendarNight(day, true), CalendarNight(day.plusDays(1), true))
            assertTrue(model.beginAllocation())
            runCurrent()
            model.toggleAllocationDate(day)
            model.toggleAllocationDate(day.plusDays(1))
            sites.calendarNights.value =
                listOf(
                    CalendarNight(day, true, "booking"),
                    CalendarNight(day.plusDays(1), true),
                )
            runCurrent()
            assertFalse(model.applyAllocation())
            assertTrue(
                model.state.value.draft.nights
                    .isEmpty(),
            )
            model.toggleAllocationDate(day)
            assertTrue(model.applyAllocation())
            assertEquals(setOf(day.plusDays(1)), model.state.value.draft.nights)
        }

    /** Range replacement covers every selected month, including offscreen nights used by Apply. */
    @Test fun allocationRangeIncludesOffscreenSelection() =
        runTest {
            val model = model()
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            sites.calendarNights.value =
                listOf(
                    CalendarNight(day, true),
                    CalendarNight(may.plusMonths(1).atDay(3), true),
                )
            assertTrue(model.beginAllocation())
            runCurrent()
            assertEquals(
                may,
                model.state.value.allocation!!
                    .month,
            )
            model.toggleAllocationDate(day)
            model.changeAllocationMonth(may.plusMonths(1))
            assertEquals(
                CampsiteInventoryUiState.Loading,
                model.state.value.allocation!!
                    .inventory,
            )
            assertFalse(model.applyAllocation())
            runCurrent()
            assertEquals(
                Triple(a.id, may.atDay(1), may.plusMonths(2).atDay(1)),
                sites.observedCalendars.last(),
            )
            model.toggleAllocationDate(may.plusMonths(1).atDay(3))
            assertTrue(model.applyAllocation())
            runCurrent()
            assertEquals(setOf(day, may.plusMonths(1).atDay(3)), model.state.value.draft.nights)
            assertEquals(0, sites.activeCalendarObservers)
        }

    /** Calendar failure is not empty inventory; retry retains working input and uses one reader. */
    @Test fun allocationReadCanRetryWithoutLosingSelection() =
        runTest {
            val model = model()
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            sites.calendarFailure.value = IOException("calendar offline")
            assertTrue(model.beginAllocation())
            runCurrent()
            assertEquals(
                CampsiteInventoryUiState.Failure,
                model.state.value.allocation!!
                    .inventory,
            )
            assertFalse(model.applyAllocation())
            sites.calendarFailure.value = null
            sites.calendarNights.value = listOf(CalendarNight(day, true))
            model.retryAllocation()
            model.retryAllocation()
            runCurrent()
            assertEquals(1, sites.activeCalendarObservers)
            model.toggleAllocationDate(day)
            assertTrue(model.applyAllocation())
        }

    /**
     * Changing sites clears old nights and requires accepting existing local times in a
     * changed zone.
     */
    @Test fun changedSiteRequiresAnExplicitScheduleZoneReview() =
        runTest {
            val model = model()
            toReview(model)
            val before = model.state.value.draft.schedule
            model.editStep(LotteryStep.SITE)
            model.chooseCampsite(b.id)
            assertTrue(
                model.state.value.draft.nights
                    .isEmpty(),
            )
            assertEquals(before, model.state.value.draft.schedule)
            allocate(model)
            model.next()
            assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
            assertTrue(model.state.value.errors.zoneConfirmationRequired)
            assertNull(model.state.value.configuration)
            model.confirmScheduleZone()
            assertFalse(model.state.value.errors.zoneConfirmationRequired)
            assertEquals(
                Instant.parse("2027-05-05T16:00:00Z"),
                model.state.value.configuration!!
                    .registrationOpensAt,
            )
        }

    /**
     * Primitive restoration retains raw form, review-return and working allocation across
     * recreation.
     */
    @Test fun recreationRestoresOneParentAndItsWorkingAllocation() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(saved = saved)
            toReview(first)
            first.editStep(LotteryStep.SITE)
            assertTrue(first.beginAllocation())
            runCurrent()
            first.toggleAllocationDate(day.plusDays(1))
            first.changeAllocationMonth(may.plusMonths(1))
            runCurrent()
            val draft = first.state.value.draft
            val working =
                first.state.value.allocation!!
                    .selected
            store.clear()
            runCurrent()
            val restored = model(saved = saved)
            assertEquals(draft, restored.state.value.draft)
            runCurrent()
            assertEquals(
                may.plusMonths(1),
                restored.state.value.allocation!!
                    .month,
            )
            assertEquals(
                working,
                restored.state.value.allocation!!
                    .selected,
            )
            restored.cancelAllocation()
            restored.back()
            runCurrent()
            assertEquals(LotteryStep.REVIEW, restored.state.value.draft.step)
            assertEquals(1, sites.activeObservers)
            assertEquals(0, sites.activeCalendarObservers)
        }

    /**
     * Schedule progression requires the current site and zone even after local fields were
     * filled.
     */
    @Test fun unavailableSelectedSiteBlocksScheduleProgression() =
        runTest {
            val model = model()
            toReview(model)
            model.editStep(LotteryStep.REGISTRATION)
            sites.records.value = emptyList()
            runCurrent()
            model.next()
            assertEquals(LotteryStep.REGISTRATION, model.state.value.draft.step)
            assertTrue(model.state.value.errors.campsiteRequired)
            assertNull(model.state.value.configuration)
            sites.records.value = listOf(a)
            runCurrent()
            model.next()
            assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
            assertNotNull(model.state.value.configuration)
        }

    /**
     * Parent invalidation stops allocation reads and failed restored sources do not trap
     * abandonment.
     */
    @Test fun sourceLossAndRestoredDiscardReleaseReaders() =
        runTest {
            val saved = SavedStateHandle()
            val first = model(saved = saved)
            runCurrent()
            toSite(first)
            first.chooseCampsite(a.id)
            assertTrue(first.beginAllocation())
            runCurrent()
            sites.records.value = emptyList()
            runCurrent()
            assertEquals(0, sites.activeCalendarObservers)
            assertFalse(first.applyAllocation())
            store.clear()
            runCurrent()
            sites.streamFailure.value = IOException("offline")
            val restored = model(saved = saved)
            runCurrent()
            restored.cancelAllocation()
            restored.requestExit()
            assertTrue(restored.state.value.discardPrompt)
            restored.dismissDiscard()
            assertFalse(restored.exitRequested.value)
            restored.requestExit()
            restored.confirmDiscard()
            runCurrent()
            assertTrue(restored.exitRequested.value)
            sites.streamFailure.value = null
            sites.records.value = listOf(a)
            restored.retryChoices()
            runCurrent()
            assertEquals(0, sites.activeObservers + sites.activeCalendarObservers)
            val reopened = model(saved = saved)
            runCurrent()
            assertTrue(reopened.exitRequested.value)
            assertEquals(0, sites.activeObservers)
        }

    /** Raw retired callbacks cannot replace a new month's data or reopen a cancelled allocation. */
    @Test fun retiredCalendarCallbacksCannotChangeCurrentAllocation() =
        runTest {
            var retired: FlowCollector<List<CalendarNight>>? = null
            val current = MutableStateFlow(listOf(CalendarNight(may.plusMonths(1).atDay(3), true)))
            val repository =
                object : CampsiteRepository by sites {
                    /** Exposes a retained raw callback to model a delayed SDK result.
                     * @param id Selected campsite identity.
                     * @param start Inclusive first required night.
                     * @param endExclusive Exclusive required endpoint.
                     */
                    override fun observeCalendar(
                        id: String,
                        start: LocalDate,
                        endExclusive: LocalDate,
                    ): Flow<List<CalendarNight>> =
                        object : Flow<List<CalendarNight>> {
                            /** Keeps one retired callback after the first range is cancelled.
                             * @param collector Active downstream allocation reader.
                             */
                            override suspend fun collect(
                                collector: FlowCollector<List<CalendarNight>>,
                            ) {
                                if (start == may.atDay(1)) {
                                    retired = collector
                                    collector.emit(listOf(CalendarNight(day, true)))
                                    awaitCancellation()
                                } else {
                                    current.collect(collector)
                                }
                            }
                        }
                }
            val model = model(repository = repository)
            runCurrent()
            toSite(model)
            model.chooseCampsite(a.id)
            assertTrue(model.beginAllocation())
            runCurrent()
            assertNotNull(retired)
            model.changeAllocationMonth(may.plusMonths(1))
            runCurrent()
            checkNotNull(retired).emit(emptyList())
            assertEquals(
                CampsiteInventoryUiState.Content(current.value),
                model.state.value.allocation!!
                    .inventory,
            )
            model.cancelAllocation()
            runCurrent()
            checkNotNull(retired).emit(emptyList())
            assertNull(model.state.value.allocation)
        }

    /** Advances through the real title action to the site step.
     * @param model Current graph owner.
     */
    private fun toSite(model: LotterySetupViewModel) {
        model.editTitle("May weekends")
        model.next()
        assertEquals(LotteryStep.SITE, model.state.value.draft.step)
    }

    /** Applies one known free night through the actual allocation actions.
     * @param model Current graph owner at the site step.
     */
    private fun allocate(model: LotterySetupViewModel) {
        sites.calendarNights.value =
            listOf(CalendarNight(day, true), CalendarNight(day.plusDays(1), true))
        assertTrue(model.beginAllocation())
        main.dispatcher.scheduler.runCurrent()
        model.toggleAllocationDate(day)
        assertTrue(model.applyAllocation())
    }

    /** Supplies four explicit local date/time values without reproducing F13's comparison logic.
     * @param model Current graph owner.
     */
    private fun fillSchedules(model: LotterySetupViewModel) {
        for ((index, point) in LotterySchedulePoint.entries.withIndex()) {
            model.editScheduleDate(point, may.atDay(5 + index))
            model.editScheduleTime(
                point,
                if (index % 2 ==
                    0
                ) {
                    LocalTime.of(9, 0)
                } else {
                    LocalTime.NOON
                },
            )
        }
    }

    /** Reaches review using real step actions and supplied fixed validator outcomes.
     * @param model Current graph owner.
     */
    private fun toReview(model: LotterySetupViewModel) {
        main.dispatcher.scheduler.runCurrent()
        toSite(model)
        model.chooseCampsite(a.id)
        allocate(model)
        model.next()
        fillSchedules(model)
        model.next()
        model.next()
        model.editWinnerCount("2")
        model.next()
        assertEquals(LotteryStep.REVIEW, model.state.value.draft.step)
    }

    /** Creates one tracked graph with explicit source and optional reserved dependency.
     * @param saved Primitive state shared only across a recreation case.
     * @param validator Fixed F13 outcome or null for its pending contract.
     * @param repository Actual controlled or deliberately adversarial campsite boundary.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        validator: LotteryWindowValidator? = { _, _, _, _ -> LotteryWindowInput.Valid },
        repository: CampsiteRepository = sites,
    ): LotterySetupViewModel =
        LotterySetupViewModel(
            "account-1",
            repository,
            FakeLotteryRepository(),
            clock,
            saved,
            validator,
        ).also { store.put("lottery", it) }
}

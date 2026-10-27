package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/** The same cell permissions govern production taps, stale callbacks and date clearing. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingDateSelectionTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private var count = 0
    private val now = CampingFixtures.now.plusSeconds(150)
    private val site = CampingFixtures.campsite()
    private val draw = CampingFixtures.lottery().copy(drawnAt = now.minusSeconds(20))
    private val month = YearMonth.of(2027, 5)
    private val start = month.atDay(14)
    private val end = start.plusDays(2)
    private val eligible = setOf(start, start.plusDays(1), month.atDay(21), month.atDay(22))
    private var eligibility: (
        Set<LocalDate>,
        List<CalendarNight>,
    ) -> Set<LocalDate> = { _, _ -> eligible }
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value = eligible.sorted().map { CalendarNight(it, true) } +
                CalendarNight(month.atDay(7), true, "someone-else")
        }
    private val lotteries =
        FakeLotteryRepository(listOf(draw)).apply {
            entries.value = listOf(LotteryEntry(draw.id, "entrant", EntryState.OFFERED, now, now))
            liveOffers.value = mapOf(draw.id to listOf(LotteryOffer(site.id, eligible.sorted())))
        }
    private val bookings = FakeBookingRepository()

    /** Actual first/second taps choose occupied dates and an unoffered excluded checkout. */
    @Test fun calendarTapsChooseStayWithoutOccupyingCheckout() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(start)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals(null, content(model).draft.checkOut)
            model.selectDate(end)
            runCurrent()
            assertEquals(end, content(model).draft.checkOut)
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            assertEquals(BookingQuote.Value(13000), content(model).quote)
            assertEquals(eligible, content(model).selectedEligibleNights)
            assertEquals(1, sites.activeCalendarObservers)
            assertTrue(bookings.observedRecordIds.isEmpty())
        }

    /** A completed selection can restart even by tapping the same original arrival. */
    @Test fun completedStayCanRestartOnItsExistingArrival() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(start)
            model.selectDate(end)
            model.selectDate(start)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals(null, content(model).draft.checkOut)
            assertEquals(null, content(model).quote)
        }

    /** An earlier eligible tap replaces an unfinished arrival without reversing dates. */
    @Test fun earlierEligibleTapRestartsPartialSelection() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(month.atDay(21))
            model.selectDate(start)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals(null, content(model).draft.checkOut)
        }

    /** Wrong-month, missing and occupied arrival callbacks cannot bypass the displayed controls. */
    @Test fun unselectableOrOutOfMonthTapsDoNotMutateTheDraft() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            val before = content(model).draft
            model.selectDate(month.atDay(7))
            model.selectDate(month.atDay(8))
            model.selectDate(month.plusMonths(1).atDay(1))
            assertEquals(before, content(model).draft)
        }

    /** A candidate endpoint still requires validation of all occupied nights. */
    @Test fun permittedEndpointCannotApproveAGapInTheOccupiedRange() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(start)
            model.selectDate(month.atDay(22))
            runCurrent()
            assertEquals(month.atDay(22), content(model).draft.checkOut)
            assertEquals(StaySelectionInput.UNAVAILABLE_NIGHTS, content(model).stayValidation)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertEquals(
                BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS),
                content(model).error,
            )
        }

    /** No helper or failed month cannot be bypassed by calling the tap action directly. */
    @Test fun pendingOrFailedDateControlsCannotSelectAnyNight() =
        runBookingTest {
            val pending = model(useHelper = false)
            pending.setDatesVisible(true)
            runCurrent()
            pending.selectDate(start)
            assertEquals(null, content(pending).draft.checkIn)
            sites.calendarFailure.value = IOException("calendar failed")
            val failed = model()
            failed.setDatesVisible(true)
            runCurrent()
            failed.selectDate(start)
            assertEquals(null, content(failed).draft.checkIn)
        }

    /** A hidden calendar has no actionable cells, even if raw selection sources remain live. */
    @Test fun hiddenDatesRejectStaleTaps() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(start)
            model.setDatesVisible(false)
            runCurrent()
            model.selectDate(end)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals(null, content(model).draft.checkOut)
        }

    /** Clear resets complete or malformed partial input, including an orphaned checkout. */
    @Test fun clearDatesRemovesAllEndpointsAndFeedback() =
        runBookingTest {
            val model = model(SavedStateHandle(mapOf("bookingCheckOut" to end.toString())))
            model.setDatesVisible(true)
            runCurrent()
            model.next(content(model).draft.step)
            model.clearDates()
            assertEquals(BookingDraft(), content(model).draft)
            assertEquals(null, content(model).error)
            model.selectDate(start)
            model.selectDate(end)
            model.clearDates()
            assertEquals(BookingDraft(), content(model).draft)
            assertEquals(null, content(model).quote)
        }

    /** Retired dates callbacks cannot clear or replace a stay while editing guests. */
    @Test fun retiredDateStepRejectsTapAndClearCallbacks() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectDate(start)
            model.selectDate(end)
            model.next(content(model).draft.step)
            runCurrent()
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            val before = content(model).draft
            model.clearDates()
            model.selectDate(month.atDay(21))
            assertEquals(before, content(model).draft)
        }

    /** Constructs the real parent with a fixed eligibility result for these known fixtures.
     * @param saved Primitive restoration inputs.
     * @param useHelper Whether date eligibility is available for this contract.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        useHelper: Boolean = true,
    ) =
        BookingViewModel(
            "entrant",
            draw.id,
            site.id,
            lotteries,
            sites,
            bookings,
            FakeImageRepository(),
            MutableClock(now),
            saved,
            if (useHelper) eligibility else null,
        ).also { store.put("dates-${count++}", it) }

    /** Asserts the outcome before returning source-backed facts.
     * @param model Parent under test.
     */
    private fun content(model: BookingViewModel): BookingUiState.Content {
        assertTrue(model.state.value is BookingUiState.Content)
        return model.state.value as BookingUiState.Content
    }

    /** Retires parent source/deadline jobs before the scheduler drains future work.
     * @param block Explicit action/outcome assertions.
     */
    private fun runBookingTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

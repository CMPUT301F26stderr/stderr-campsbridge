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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Route-facing state has explicit display/action identity and consumed navigation handoffs. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingPresentationStateTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private var count = 0
    private val now = CampingFixtures.now.plusSeconds(150)
    private val site = CampingFixtures.campsite()
    private val draw = CampingFixtures.lottery().copy(drawnAt = now.minusSeconds(20))
    private val record = CampingFixtures.reservation()
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value =
                listOf(
                    CalendarNight(record.checkInDate, true),
                    CalendarNight(record.checkInDate.plusDays(1), true),
                )
        }
    private val lotteries =
        FakeLotteryRepository(listOf(draw)).apply {
            entries.value = listOf(LotteryEntry(draw.id, "entrant", EntryState.OFFERED, now, now))
            liveOffers.value =
                mapOf(
                    draw.id to
                        listOf(
                            LotteryOffer(
                                site.id,
                                listOf(record.checkInDate, record.checkInDate.plusDays(1)),
                            ),
                        ),
                )
        }
    private val bookings = FakeBookingRepository()

    /** Queued Next actions from Dates cannot skip the following valid Guests step. */
    @Test fun nextRejectsAnActionFromThePreviouslyDisplayedStep() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.selectCheckIn(record.checkInDate)
            model.selectCheckOut(record.checkOutDate)
            runCurrent()
            model.next(BookingStep.DATES)
            model.next(BookingStep.DATES)
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            model.next(BookingStep.GUESTS)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            model.next(BookingStep.GUESTS)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertEquals(null, content(model).error)
        }

    /** Display can retain positive raw counts above a changed capacity without faking validity. */
    @Test fun displayCountPreservesRawInputAndSeparatesCapacityFeedback() =
        runBookingTest {
            val model = model()
            runCurrent()
            assertEquals(1L, content(model).guestCount)
            model.selectCheckIn(record.checkInDate)
            model.selectCheckOut(record.checkOutDate)
            runCurrent()
            model.next(content(model).draft.step)
            model.editGuests(" 03 ")
            assertEquals(3L, content(model).guestCount)
            sites.records.value = listOf(site.copy(details = site.details.copy(maxGuests = 2)))
            runCurrent()
            assertEquals(3L, content(model).guestCount)
            model.next(content(model).draft.step)
            assertEquals(
                BookingStepError.Guests(PositiveCountInput.Error.EXCEEDS_MAXIMUM),
                content(model).error,
            )
            model.editGuests("not a number")
            assertEquals(null, content(model).guestCount)
            assertEquals("not a number", content(model).draft.guestInput)
        }

    /** Known consumption opens its actual identity once, independently of later read changes. */
    @Test fun existingReservationCreatesOneAcknowledgedSavedTargetHandoff() =
        runBookingTest {
            bookings.records.value = listOf(record)
            val model = model()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), model.state.value)
            model.acknowledgeCompletion()
            assertEquals(null, model.completion.value)
            bookings.records.value = emptyList()
            model.refreshTime()
            runCurrent()
            assertEquals(null, model.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), model.state.value)
            model.requestReservationOpen()
            model.requestReservationOpen()
            assertEquals(record.id, model.completion.value)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Recreation retains an undelivered handoff and cannot replay an acknowledged one. */
    @Test fun savedTargetHandoffRestoresUntilActualNavigationAcknowledgement() =
        runBookingTest {
            bookings.records.value = listOf(record)
            val saved = SavedStateHandle()
            val first = model(saved)
            runCurrent()
            assertEquals(record.id, first.completion.value)
            val pending = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            runCurrent()
            val restoredSaved = SavedStateHandle(pending)
            val restored = model(restoredSaved)
            runCurrent()
            assertEquals(record.id, restored.completion.value)
            restored.acknowledgeCompletion()
            val acknowledged = restoredSaved.keys().associateWith { restoredSaved.get<Any?>(it) }
            store.clear()
            runCurrent()
            val consumed = model(SavedStateHandle(acknowledged))
            runCurrent()
            assertEquals(null, consumed.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), consumed.state.value)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Unused opportunities cannot request synthetic history navigation. */
    @Test fun unusedOpportunityDoesNotInventAReservationHandoff() =
        runBookingTest {
            val model = model()
            runCurrent()
            content(model)
            model.requestReservationOpen()
            model.acknowledgeCompletion()
            assertEquals(null, model.completion.value)
            assertTrue(bookings.observedRecordIds.isEmpty())
        }

    /** Retired route callbacks cannot reopen a saved target or acknowledge its pending handoff. */
    @Test fun clearedGraphRejectsStaleSavedTargetActions() =
        runBookingTest {
            bookings.records.value = listOf(record)
            val model = model()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            model.acknowledgeCompletion()
            store.clear()
            runCurrent()
            model.requestReservationOpen()
            model.acknowledgeCompletion()
            assertEquals(null, model.completion.value)
        }

    /** Supplies fixed eligibility for the two recorded fixture nights.
     * @param saved Primitive route state to restore.
     */
    private fun model(saved: SavedStateHandle = SavedStateHandle()) =
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
            { _, _ ->
                setOf(record.checkInDate, record.checkInDate.plusDays(1))
            },
        ).also { store.put("presentation-${count++}", it) }

    /** Asserts a typed read outcome before exposing editable facts.
     * @param model Parent under test.
     */
    private fun content(model: BookingViewModel): BookingUiState.Content {
        assertTrue(model.state.value is BookingUiState.Content)
        return model.state.value as BookingUiState.Content
    }

    /** Retires parent jobs before future scheduler work drains.
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

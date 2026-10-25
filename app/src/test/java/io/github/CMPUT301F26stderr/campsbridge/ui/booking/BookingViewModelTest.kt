package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

/** Own-winning facts, scoped inventory and primitive drafts precede any submission wiring. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private var modelCount = 0
    private val now = CampingFixtures.now.plusSeconds(150)
    private val clock = MutableClock(now)
    private val site = CampingFixtures.campsite()
    private val lottery = CampingFixtures.lottery().copy(drawnAt = now.minusSeconds(20))
    private val arrival = CampingFixtures.arrival
    private val month = YearMonth.from(arrival)
    private val offered = listOf(arrival, arrival.plusDays(1), month.atEndOfMonth())
    private val offer = LotteryOffer(site.id, offered)
    private val ownEntry = LotteryEntry(lottery.id, "entrant", EntryState.OFFERED, now, now)
    private val lotteries =
        FakeLotteryRepository(listOf(lottery)).apply {
            entries.value = listOf(ownEntry)
            liveOffers.value = mapOf(lottery.id to listOf(offer))
        }
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value = offered.map { CalendarNight(it, true) }
        }
    private val bookings = FakeBookingRepository()
    private var eligible = setOf(arrival, arrival.plusDays(1))
    private val eligibilityRequests = mutableListOf<Pair<Set<LocalDate>, List<CalendarNight>>>()

    /** Retires every created graph, including all deadline and source coroutines. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** Only the fixed parent, own membership, offers and site sources are needed. */
    @Test fun unusedOwnWinLoadsWithoutScansOrInventoryBeforeVisibility() =
        runBookingTest {
            val model = model()
            assertEquals(BookingUiState.Loading, model.state.value)
            BookingReadSource.entries.forEach { model.retry(it) }
            runCurrent()
            val state = content(model)
            assertEquals(lottery, state.lottery)
            assertEquals(site, state.campsite)
            assertEquals(offer, state.offer)
            assertEquals(BookingDraft(), state.draft)
            assertEquals(month, state.month)
            assertEquals(listOf(lottery.id), lotteries.observedRecordIds)
            assertEquals(listOf("entrant"), lotteries.entrantRequests)
            assertEquals(listOf(lottery.id), lotteries.observedOfferIds)
            assertEquals(listOf(site.id), sites.observedIds)
            assertEquals(listOf(lottery.id to "entrant"), bookings.opportunityRequests)
            assertTrue(lotteries.entriesRequests.isEmpty())
            assertTrue(lotteries.ownerRequests.isEmpty())
            assertEquals(0, sites.allObservations)
            assertTrue(sites.observedCalendars.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
            assertTrue(bookings.entrantRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
            assertEquals(0, sites.writeCalls)
        }

    /** Matching both account and lottery prevents unrelated wins from enabling this draft. */
    @Test fun unrelatedOrNonwinningEntriesCannotStartConsumptionLookup() =
        runBookingTest {
            lotteries.entries.value =
                listOf(
                    ownEntry.copy(accountId = "someone-else"),
                    ownEntry.copy(lotteryId = "other"),
                    ownEntry.copy(state = EntryState.LOST),
                )
            val model = model()
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.NOT_A_WINNER), model.state.value)
            assertTrue(bookings.opportunityRequests.isEmpty())
            lotteries.entries.value = listOf(ownEntry)
            runCurrent()
            content(model)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Partial draw outcomes stay masked until the actual parent confirms completion. */
    @Test fun unfinishedDrawDoesNotReadOrInventAUsableWin() =
        runBookingTest {
            lotteries.records.value = listOf(lottery.copy(drawnAt = null))
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.DRAW_PENDING), model.state.value)
            assertTrue(bookings.opportunityRequests.isEmpty())
            assertTrue(sites.observedCalendars.isEmpty())
            lotteries.records.value = listOf(lottery)
            runCurrent()
            content(model)
            assertEquals(1, bookings.opportunityRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Missing and unpublished parents are confirmed unavailability rather than unused wins. */
    @Test fun absentOrDraftLotteryCannotExposeDates() =
        runBookingTest {
            lotteries.records.value = emptyList()
            val model = model()
            runCurrent()
            assertEquals(
                unavailable(BookingUnavailableReason.LOTTERY_UNAVAILABLE),
                model.state.value,
            )
            lotteries.records.value = listOf(lottery.copy(publishedAt = null))
            runCurrent()
            assertEquals(
                unavailable(BookingUnavailableReason.LOTTERY_UNAVAILABLE),
                model.state.value,
            )
            assertTrue(bookings.opportunityRequests.isEmpty())
        }

    /** Cancellation prevents a new booking even when a completed draw has an offered entry. */
    @Test fun cancelledLotteryRemainsUnavailableAfterConfirmedUnusedLookup() =
        runBookingTest {
            lotteries.records.value =
                listOf(lottery.copy(cancellation = Cancellation(now, "owner", null)))
            val model = model()
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.LOTTERY_CANCELLED), model.state.value)
            assertTrue(sites.observedCalendars.isEmpty())
        }

    /** Current site identity, publication, removal and lottery ownership all remain required. */
    @Test fun unavailableOrUnrelatedSiteCannotExposeDates() =
        runBookingTest {
            sites.records.value = emptyList()
            val model = model()
            runCurrent()
            assertEquals(
                unavailable(BookingUnavailableReason.CAMPSITE_UNAVAILABLE),
                model.state.value,
            )
            listOf(
                site.copy(publishedAt = null),
                site.copy(ownerId = "other-owner"),
                site.copy(removal = Removal(now, "owner", null)),
            ).forEach { unavailableSite ->
                sites.records.value = listOf(unavailableSite)
                runCurrent()
                assertEquals(
                    unavailable(BookingUnavailableReason.CAMPSITE_UNAVAILABLE),
                    model.state.value,
                )
            }
            sites.records.value = listOf(site)
            runCurrent()
            content(model)
        }

    /** The selected site must be allocated; an offer for another site is not a substitute. */
    @Test fun absentSelectedAllocationDoesNotUseAnotherOffer() =
        runBookingTest {
            lotteries.liveOffers.value =
                mapOf(lottery.id to listOf(offer.copy(campsiteId = "other")))
            val model = model()
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.OFFER_UNAVAILABLE), model.state.value)
            lotteries.liveOffers.value = mapOf(lottery.id to listOf(offer))
            runCurrent()
            content(model)
        }

    /** One nearest wait reevaluates exact open/close without writes or automatic extra lookup. */
    @Test fun bookingWindowRefreshesAtInclusiveOpenAndExclusiveClose() =
        runBookingTest {
            val windows = lottery.configuration
            clock.advance(Duration.between(now, windows.bookingOpensAt.minusSeconds(1)))
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.BOOKING_NOT_OPEN), model.state.value)
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
            content(model)
            clock.advance(Duration.between(clock.instant(), windows.bookingClosesAt))
            advanceTimeBy(
                Duration.between(windows.bookingOpensAt, windows.bookingClosesAt).toMillis(),
            )
            runCurrent()
            assertEquals(unavailable(BookingUnavailableReason.BOOKING_CLOSED), model.state.value)
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(1, bookings.opportunityRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Held server confirmation cannot be mistaken for absent history or start inventory. */
    @Test fun heldConsumptionReadIsLoadingAndDuplicateRetryDoesNotRestartIt() =
        runBookingTest {
            val gate = CompletableDeferred<Unit>()
            bookings.opportunityHandler = { _, _ ->
                gate.await()
                null
            }
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            model.retry(BookingReadSource.OPPORTUNITY)
            model.refreshTime()
            runCurrent()
            assertEquals(1, bookings.opportunityRequests.size)
            assertTrue(sites.observedCalendars.isEmpty())
            gate.complete(Unit)
            runCurrent()
            content(model)
        }

    /** Retained cancelled/refunded history wins over unavailable current listing data. */
    @Test fun cancelledExistingReservationStillConsumesWinAndNeedsNoDuplicateReader() =
        runBookingTest {
            val reservation =
                CampingFixtures.reservation().let {
                    it.copy(
                        cancellation = ReservationCancellation(now, "owner", Role.OWNER, null),
                        payment = it.payment.copy(refundedAt = now),
                    )
                }
            bookings.records.value = listOf(reservation)
            sites.records.value = emptyList()
            val saved = SavedStateHandle()
            val model = model(saved)
            runCurrent()
            assertEquals(BookingUiState.Reserved(reservation.id), model.state.value)
            lotteries.recordFailure.value = IOException("later failure")
            bookings.records.value = emptyList()
            model.refreshTime()
            runCurrent()
            assertEquals(BookingUiState.Reserved(reservation.id), model.state.value)
            assertTrue(bookings.observedRecordIds.isEmpty())
            assertTrue(saved.keys().all { saved.get<Any?>(it) is String? })
            assertTrue(saved.keys().any { saved.get<String>(it) == reservation.id })
        }

    /** A corrupt or unrelated lookup result is unknown, never consumed or confirmed unused. */
    @Test fun mismatchedConsumptionRecordIsAFailedConfirmation() =
        runBookingTest {
            val record = CampingFixtures.reservation()
            val model = model()
            // Replace the source before its installed lazy job has started.
            bookings.opportunityHandler = { _, _ -> record.copy(entrantId = "other") }
            runCurrent()
            assertEquals(BookingUiState.Failure(BookingReadSource.OPPORTUNITY), model.state.value)
            bookings.opportunityHandler = { _, _ -> record.copy(lotteryId = "another-draw") }
            model.retry(BookingReadSource.OPPORTUNITY)
            runCurrent()
            assertEquals(BookingUiState.Failure(BookingReadSource.OPPORTUNITY), model.state.value)
            assertTrue(sites.observedCalendars.isEmpty())
        }

    /** Failed lookup retry is explicit, coalesced and does not restart other sources. */
    @Test fun failedConsumptionRetriesOnlyItsServerLookup() =
        runBookingTest {
            bookings.opportunityHandler = { _, _ -> throw IOException("server unavailable") }
            val model = model()
            runCurrent()
            assertEquals(BookingUiState.Failure(BookingReadSource.OPPORTUNITY), model.state.value)
            val gate = CompletableDeferred<Unit>()
            bookings.opportunityHandler = { _, _ ->
                gate.await()
                null
            }
            model.retry(BookingReadSource.OPPORTUNITY)
            model.retry(BookingReadSource.OPPORTUNITY)
            assertEquals(BookingUiState.Loading, model.state.value)
            runCurrent()
            assertEquals(2, bookings.opportunityRequests.size)
            gate.complete(Unit)
            runCurrent()
            content(model)
            assertEquals(1, lotteries.observedRecordIds.size)
            assertEquals(1, sites.observedIds.size)
        }

    /** Each fixed-ID source has its own failure and duplicate-retry guard. */
    @Test fun sourceRetriesDoNotRestartOtherConfirmedObservations() =
        runBookingTest {
            val model = model()
            runCurrent()
            content(model)
            val failures =
                listOf(
                    BookingReadSource.LOTTERY to lotteries.recordFailure,
                    BookingReadSource.MEMBERSHIP to lotteries.entrantFailure,
                    BookingReadSource.OFFERS to lotteries.offerFailure,
                    BookingReadSource.CAMPSITE to sites.streamFailure,
                )
            failures.forEach { (source, failure) ->
                failure.value = IOException("read failed")
                runCurrent()
                assertEquals(BookingUiState.Failure(source), model.state.value)
                failure.value = null
                model.retry(source)
                model.retry(source)
                runCurrent()
                content(model)
            }
            assertEquals(2, lotteries.observedRecordIds.size)
            assertEquals(2, lotteries.entrantRequests.size)
            assertEquals(2, lotteries.observedOfferIds.size)
            assertEquals(2, sites.observedIds.size)
            assertEquals(1, lotteries.activeRecordObservers)
            assertEquals(1, lotteries.activeEntrantObservers)
            assertEquals(1, lotteries.activeOfferObservers)
            assertEquals(1, sites.activeObservers)
        }

    /** Default factories can read dates but cannot manufacture the pending F15 result. */
    @Test fun missingEligibilityHelperRemainsExplicitlyPending() =
        runBookingTest {
            val model = model(useHelper = false)
            model.setDatesVisible(true)
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            val state = content(model)
            assertTrue(state.eligibilityPending)
            assertEquals(null, state.visibleEligibleNights)
            assertEquals(null, state.stayValidation)
            assertEquals(BookingQuote.Value(13000), state.quote)
            assertTrue(eligibilityRequests.isEmpty())
        }

    /** The caller supplies original offer/inventory facts and uses the injected result exactly. */
    @Test fun eligibilityUsesConfirmedSourceFactsWithoutAnInlineIntersection() =
        runBookingTest {
            eligible = setOf(arrival.plusDays(7))
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            val state = content(model)
            assertFalse(state.eligibilityPending)
            assertEquals(eligible, state.visibleEligibleNights)
            assertTrue(
                eligibilityRequests.any {
                    it.first == offered.toSet() &&
                        it.second == sites.calendarNights.value
                },
            )
        }

    /** Offscreen selection reads contain every occupied night and exclude checkout. */
    @Test fun crossMonthStayRemainsValidatedAfterHidingOrChangingCalendar() =
        runBookingTest {
            val start = month.atEndOfMonth()
            val end = start.plusDays(2)
            eligible = setOf(start, start.plusDays(1))
            lotteries.liveOffers.value =
                mapOf(lottery.id to listOf(LotteryOffer(site.id, eligible.sorted())))
            sites.calendarNights.value = eligible.sorted().map { CalendarNight(it, true) }
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectCheckIn(start)
            model.selectCheckOut(end)
            runCurrent()
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            assertEquals(BookingQuote.Value(13000), content(model).quote)
            assertTrue(sites.observedCalendars.contains(Triple(site.id, start, end)))
            model.changeMonth(month.plusMonths(2))
            runCurrent()
            model.setDatesVisible(false)
            runCurrent()
            assertEquals(null, content(model).calendar.visible)
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            assertEquals(1, sites.activeCalendarObservers)
            assertFalse(eligible.contains(end))
        }

    /** Unknown selected inventory cannot validate; its retry leaves unrelated sources untouched. */
    @Test fun failedSelectedInventoryIsNotKnownEmptyOrValid() =
        runBookingTest {
            val gate = CompletableDeferred<Unit>()
            var fail = true
            sites.calendarHandler = { _, _, _ ->
                flow {
                    if (fail) throw IOException("range read failed")
                    gate.await()
                    emit(sites.calendarNights.value.take(2))
                }
            }
            val model = model()
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Failure, content(model).calendar.selected)
            assertEquals(null, content(model).stayValidation)
            fail = false
            model.retrySelectedInventory()
            model.retrySelectedInventory()
            runCurrent()
            assertEquals(CalendarInventoryRead.Loading, content(model).calendar.selected)
            assertEquals(null, content(model).stayValidation)
            gate.complete(Unit)
            runCurrent()
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            assertEquals(2, sites.observedCalendars.size)
            assertEquals(1, sites.observedIds.size)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Confirmed absence differs from failed inventory and never implies free nights. */
    @Test fun knownEmptyInventoryUsesF15EmptyResultAndUnavailableFeedback() =
        runBookingTest {
            sites.calendarNights.value = emptyList()
            eligible = emptySet()
            val model = model()
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            assertEquals(
                CalendarInventoryRead.Content(emptyList()),
                content(model).calendar.selected,
            )
            assertEquals(StaySelectionInput.UNAVAILABLE_NIGHTS, content(model).stayValidation)
        }

    /** Partial and invalid input receives ordered feedback without requesting an invalid range. */
    @Test fun dateFeedbackPrecedesInventoryForMissingReversedOrPastDates() =
        runBookingTest {
            val model = model()
            runCurrent()
            assertEquals(StaySelectionInput.MISSING_CHECK_IN, content(model).stayValidation)
            model.selectCheckIn(arrival)
            assertEquals(StaySelectionInput.MISSING_CHECK_OUT, content(model).stayValidation)
            model.selectCheckOut(arrival)
            assertEquals(StaySelectionInput.INVALID_RANGE, content(model).stayValidation)
            model.selectCheckOut(arrival.minusDays(1))
            assertEquals(StaySelectionInput.INVALID_RANGE, content(model).stayValidation)
            val yesterday = content(model).today.minusDays(1)
            model.selectCheckIn(yesterday)
            model.selectCheckOut(yesterday.plusDays(1))
            assertEquals(StaySelectionInput.PAST_CHECK_IN, content(model).stayValidation)
        }

    /** A changed arrival discards only its former departure and preserves raw guest text/month. */
    @Test fun editingDatesKeepsRawGuestInputAndUnchangedArrivalDoesNotClearCheckout() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            model.next(content(model).draft.step)
            model.editGuests(" 03 ")
            model.back()
            model.changeMonth(month.plusMonths(1))
            runCurrent()
            model.selectCheckIn(arrival)
            assertEquals(BookingDraft(arrival, arrival.plusDays(2), " 03 "), content(model).draft)
            model.selectCheckIn(arrival.plusDays(1))
            assertEquals(BookingDraft(arrival.plusDays(1), null, " 03 "), content(model).draft)
            assertEquals(month.plusMonths(1), content(model).month)
            model.selectCheckIn(null)
            assertEquals(BookingDraft(guestInput = " 03 "), content(model).draft)
        }

    /** Only primitive input is restored; new source reads never replay a reservation operation. */
    @Test fun draftRecreationPreservesSmallInputsAndRereadsUnusedConsumption() =
        runBookingTest {
            val saved = SavedStateHandle()
            val first = model(saved)
            runCurrent()
            first.selectCheckIn(arrival)
            first.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            first.next(content(first).draft.step)
            first.editGuests("3")
            first.back()
            first.changeMonth(month.plusMonths(1))
            runCurrent()
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            assertTrue(
                values.values.all { it == null || it is String || it is Boolean || it is Long },
            )
            store.clear()
            runCurrent()
            val restored = model(SavedStateHandle(values))
            runCurrent()
            assertEquals(BookingDraft(arrival, arrival.plusDays(2), "3"), content(restored).draft)
            assertEquals(month.plusMonths(1), content(restored).month)
            assertEquals(null, content(restored).calendar.visible)
            assertEquals(StaySelectionInput.VALID, content(restored).stayValidation)
            assertEquals(2, bookings.opportunityRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Malformed date/month strings cannot crash bootstrap or silently choose invalid ranges. */
    @Test fun malformedRestoredEndpointsAndMonthFallBackSafely() =
        runBookingTest {
            val saved =
                SavedStateHandle(
                    mapOf(
                        "bookingCheckIn" to "not-a-date",
                        "bookingCheckOut" to "2027-99-99",
                        "bookingMonth" to "bad-month",
                        "bookingGuests" to "",
                    ),
                )
            val model = model(saved)
            runCurrent()
            assertEquals(BookingDraft(guestInput = ""), content(model).draft)
            assertEquals(month, content(model).month)
            assertTrue(sites.observedCalendars.isEmpty())
        }

    /** First month follows campsite-local today or a restored arrival in a different month. */
    @Test fun initialMonthUsesLocalTodayThenRestoredArrivalWhenNoMonthWasChosen() =
        runBookingTest {
            val earlyUtc = java.time.Instant.parse("2027-05-01T05:00:00Z")
            clock.advance(Duration.between(now, earlyUtc))
            lotteries.records.value =
                listOf(
                    lottery.copy(
                        drawnAt = earlyUtc.minusSeconds(150),
                        configuration =
                            lottery.configuration.copy(
                                registrationOpensAt = earlyUtc.minusSeconds(300),
                                registrationClosesAt = earlyUtc.minusSeconds(200),
                                bookingOpensAt = earlyUtc.minusSeconds(100),
                                bookingClosesAt = earlyUtc.plusSeconds(1000),
                            ),
                    ),
                )
            val first = model()
            runCurrent()
            assertEquals(YearMonth.of(2027, 4), content(first).month)
            store.clear()
            runCurrent()
            val june = LocalDate.of(2027, 6, 15)
            val restored =
                model(
                    SavedStateHandle(
                        mapOf(
                            "bookingCheckIn" to june.toString(),
                            "bookingCheckOut" to june.plusDays(2).toString(),
                        ),
                    ),
                )
            runCurrent()
            assertEquals(YearMonth.from(june), content(restored).month)
            assertEquals(LocalDate.of(2027, 4, 30), content(restored).today)
        }

    /** A retained consuming ID survives recreation even if current records are now unavailable. */
    @Test fun knownConsumptionRestoresWithoutAnotherLookupOrInventory() =
        runBookingTest {
            bookings.records.value = listOf(CampingFixtures.reservation())
            val saved = SavedStateHandle()
            val first = model(saved)
            runCurrent()
            assertEquals(BookingUiState.Reserved("reservation"), first.state.value)
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            runCurrent()
            bookings.records.value = emptyList()
            lotteries.records.value = emptyList()
            val restored = model(SavedStateHandle(values))
            runCurrent()
            assertEquals(BookingUiState.Reserved("reservation"), restored.state.value)
            assertEquals(1, bookings.opportunityRequests.size)
            assertTrue(sites.observedCalendars.isEmpty())
        }

    /** UTC device date does not replace campsite-local today or reset the chosen visible month. */
    @Test fun localMidnightRefreshKeepsSelectedMonthAndRevalidatesPastArrival() =
        runBookingTest {
            val localMidnight = java.time.Instant.parse("2027-05-02T06:00:00Z")
            clock.advance(Duration.between(now, localMidnight.minusSeconds(1)))
            lotteries.records.value =
                listOf(
                    lottery.copy(
                        configuration =
                            lottery.configuration.copy(
                                bookingClosesAt = localMidnight.plusSeconds(3600),
                            ),
                    ),
                )
            val model = model()
            runCurrent()
            val localToday = LocalDate.parse("2027-05-01")
            assertEquals(localToday, content(model).today)
            eligible = setOf(localToday)
            model.selectCheckIn(localToday)
            model.selectCheckOut(localToday.plusDays(1))
            model.changeMonth(month.plusMonths(2))
            runCurrent()
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(localToday.plusDays(1), content(model).today)
            assertEquals(StaySelectionInput.PAST_CHECK_IN, content(model).stayValidation)
            assertEquals(month.plusMonths(2), content(model).month)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Current pricing recomputes a checked preview; no stale/frozen request is submitted. */
    @Test fun liveRateChangesRecomputeExactQuoteAndOverflowRemainsExplicit() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            assertEquals(BookingQuote.Value(13000), content(model).quote)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = 7000)))
            runCurrent()
            assertEquals(BookingQuote.Value(14000), content(model).quote)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = Long.MAX_VALUE)))
            runCurrent()
            assertEquals(BookingQuote.Overflow, content(model).quote)
            assertEquals(BookingDraft(arrival, arrival.plusDays(2)), content(model).draft)
        }

    /** Foreground return rechecks unused consumption before exposing another edit session. */
    @Test fun foregroundRefreshFindsAReservationWithoutAnyAutomaticWrite() =
        runBookingTest {
            val model = model()
            runCurrent()
            content(model)
            bookings.records.value = listOf(CampingFixtures.reservation())
            model.refreshTime()
            model.refreshTime()
            runCurrent()
            assertEquals(BookingUiState.Reserved("reservation"), model.state.value)
            assertEquals(2, bookings.opportunityRequests.size)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Retired noncooperative lookup cannot replace withdrawn membership with old history. */
    @Test fun withdrawnWinRejectsLateNoncooperativeLookup() =
        runBookingTest {
            val gate = CompletableDeferred<Unit>()
            bookings.opportunityHandler = { _, _ ->
                withContext(NonCancellable) {
                    gate.await()
                    CampingFixtures.reservation()
                }
            }
            val model = model()
            runCurrent()
            try {
                lotteries.entries.value = listOf(ownEntry.copy(state = EntryState.DECLINED))
                runCurrent()
                assertEquals(unavailable(BookingUnavailableReason.NOT_A_WINNER), model.state.value)
                gate.complete(Unit)
                runCurrent()
                assertEquals(unavailable(BookingUnavailableReason.NOT_A_WINNER), model.state.value)
                assertTrue(sites.observedCalendars.isEmpty())
            } finally {
                gate.complete(Unit)
            }
        }

    /** Parent failure removes active inventory while retaining unsubmitted dates for retry. */
    @Test fun sourceFailurePreservesInputAndReleasesDependentCalendar() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            runCurrent()
            model.next(content(model).draft.step)
            model.editGuests("3")
            model.back()
            runCurrent()
            lotteries.recordFailure.value = IOException("parent offline")
            runCurrent()
            assertEquals(BookingUiState.Failure(BookingReadSource.LOTTERY), model.state.value)
            assertEquals(0, sites.activeCalendarObservers)
            lotteries.recordFailure.value = null
            model.retry(BookingReadSource.LOTTERY)
            runCurrent()
            assertEquals(BookingDraft(arrival, arrival.plusDays(2), "3"), content(model).draft)
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
        }

    /** Cancellation does not become a user-visible retrieval error or release an unknown win. */
    @Test fun cancelledConsumptionReadDoesNotBecomeFailureOrUnused() =
        runBookingTest {
            bookings.opportunityHandler = { _, _ -> throw CancellationException("scope cancelled") }
            val model = model()
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            assertTrue(sites.observedCalendars.isEmpty())
            bookings.opportunityHandler = { _, _ -> null }
            model.retry(BookingReadSource.OPPORTUNITY)
            model.retry(BookingReadSource.OPPORTUNITY)
            runCurrent()
            content(model)
            assertEquals(2, bookings.opportunityRequests.size)
        }

    /** A cancelled live source retires stale facts and permits explicit same-source recovery. */
    @Test fun repositorySourceCancellationCanBeExplicitlyRetried() =
        runBookingTest {
            val model = model()
            runCurrent()
            content(model)
            lotteries.recordFailure.value = CancellationException("source interrupted")
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            lotteries.recordFailure.value = null
            model.retry(BookingReadSource.LOTTERY)
            model.retry(BookingReadSource.LOTTERY)
            runCurrent()
            content(model)
            assertEquals(2, lotteries.observedRecordIds.size)
            assertEquals(1, lotteries.entrantRequests.size)
            assertEquals(1, sites.observedIds.size)
        }

    /** Foreground return also recovers interrupted observations and unknown lookup confirmation. */
    @Test fun foregroundReturnRecoversCancelledReadsWithoutTreatingThemAsFailures() =
        runBookingTest {
            val model = model()
            runCurrent()
            content(model)
            lotteries.recordFailure.value = CancellationException("source interrupted")
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            lotteries.recordFailure.value = null
            model.refreshTime()
            runCurrent()
            content(model)
            bookings.opportunityHandler =
                { _, _ -> throw CancellationException("lookup interrupted") }
            model.refreshTime()
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            bookings.opportunityHandler = { _, _ -> null }
            model.refreshTime()
            model.refreshTime()
            runCurrent()
            content(model)
            assertEquals(2, lotteries.observedRecordIds.size)
            assertEquals(4, bookings.opportunityRequests.size)
            assertEquals(1, sites.observedIds.size)
        }

    /** Graph removal cancels all sources and later UI callbacks cannot restart any of them. */
    @Test fun clearedGraphRejectsStaleActionsAndReleasesEverySource() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            runCurrent()
            content(model)
            store.clear()
            runCurrent()
            model.setDatesVisible(true)
            model.selectCheckIn(arrival)
            model.selectCheckOut(arrival.plusDays(2))
            model.changeMonth(month.plusMonths(1))
            BookingReadSource.entries.forEach { model.retry(it) }
            model.retryVisibleInventory()
            model.retrySelectedInventory()
            model.refreshTime()
            runCurrent()
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(0, sites.activeObservers)
            assertEquals(0, lotteries.activeRecordObservers)
            assertEquals(0, lotteries.activeEntrantObservers)
            assertEquals(0, lotteries.activeOfferObservers)
            assertEquals(1, lotteries.observedRecordIds.size)
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Ends parent deadlines before runTest drains future scheduler work.
     * @param block Outcome assertions using the controlled scheduler.
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

    /** Builds the production parent with a fixed-result F15 dependency, not an exercise algorithm.
     * @param saved Primitive state to retain or recreate.
     * @param useHelper Whether the reserved dependency is available for this contract.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        useHelper: Boolean = true,
    ): BookingViewModel =
        BookingViewModel(
            "entrant",
            lottery.id,
            site.id,
            lotteries,
            sites,
            bookings,
            clock,
            saved,
            if (useHelper) {
                { dates, nights ->
                    eligibilityRequests.add(dates to nights)
                    eligible
                }
            } else {
                null
            },
        ).also { store.put("booking-${modelCount++}", it) }

    /** Asserts the outcome before accessing its typed facts, retaining intended red assertions.
     * @param model Parent under test.
     */
    private fun content(model: BookingViewModel): BookingUiState.Content {
        assertTrue(
            "Expected editable confirmed opportunity, got ${model.state.value}",
            model.state.value is BookingUiState.Content,
        )
        return model.state.value as BookingUiState.Content
    }

    /** Constructs a concise expected unavailability outcome.
     * @param reason Confirmed blocking fact.
     */
    private fun unavailable(reason: BookingUnavailableReason) =
        BookingUiState.Unavailable(reason)
}

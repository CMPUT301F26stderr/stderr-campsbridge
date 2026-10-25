package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
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
import java.time.YearMonth

/** The parent owns one guarded attempt, primitive uncertainty and explicit read-only recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingConfirmationTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private var creations = 0
    private val now = CampingFixtures.now.plusSeconds(150)
    private val clock = MutableClock(now)
    private val site = CampingFixtures.campsite()
    private val draw = CampingFixtures.lottery().copy(drawnAt = now.minusSeconds(20))
    private val record = CampingFixtures.reservation().copy(guestCount = 3)
    private val start = record.checkInDate
    private val end = record.checkOutDate
    private val nights = setOf(start, start.plusDays(1))
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value = nights.map { CalendarNight(it, true) }
        }
    private val lotteries =
        FakeLotteryRepository(listOf(draw)).apply {
            entries.value = listOf(LotteryEntry(draw.id, "entrant", EntryState.OFFERED, now, now))
            liveOffers.value = mapOf(draw.id to listOf(LotteryOffer(site.id, nights.sorted())))
        }
    private val bookings = FakeBookingRepository()

    /** Submission and uncertainty are retained before suspension; duplicate Confirm is ignored. */
    @Test fun heldAttemptFreezesOneExactRequestAndStoresUncertaintyBeforeIo() =
        runBookingTest {
            val saved = SavedStateHandle()
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = {
                assertEquals(true, saved.get<Boolean>("bookingRecoveryRequired"))
                held.await()
            }
            val model = model(saved)
            review(model)
            model.confirm()
            assertEquals(BookingSubmission.Submitting, model.submission.value)
            assertEquals(true, saved.get<Boolean>("bookingRecoveryRequired"))
            model.confirm()
            runCurrent()
            assertEquals(listOf(expectedRequest()), bookings.reserveRequests)
            assertNull(model.completion.value)
            held.complete(record.id)
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), model.state.value)
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
            model.confirm()
            runCurrent()
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** Every field/step/exit/policy action stays blocked while the immutable attempt is active. */
    @Test fun pendingAttemptRejectsConflictingActionsAndKeepsItsDisplayedReview() =
        runBookingTest {
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = { held.await() }
            val model = model()
            review(model)
            val original = content(model).draft
            model.confirm()
            runCurrent()
            model.editStep(BookingStep.DATES)
            model.changeMonth(YearMonth.from(start).plusMonths(1))
            model.selectCheckIn(start.plusDays(4))
            model.selectCheckOut(end.plusDays(4))
            model.clearDates()
            model.selectDate(start.plusDays(1))
            model.editGuests("7")
            model.acceptTerms(false, site.details.rules, BookingPolicy.VERSION)
            model.acknowledgeQuote(999)
            model.next(BookingStep.REVIEW)
            model.back()
            model.requestExit()
            model.confirmDiscard()
            model.retryRecovery()
            model.confirm()
            assertEquals(original, content(model).draft)
            assertFalse(model.canReadPolicy())
            assertFalse(model.discardPrompt.value)
            assertFalse(model.exitRequested.value)
            assertEquals(BookingSubmission.Submitting, model.submission.value)
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** Source errors cannot unlock abandonment or another command. */
    @Test fun sourceFailureCannotHideOrUnlockThePendingAttempt() =
        runBookingTest {
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = { held.await() }
            val model = model()
            review(model)
            model.confirm()
            runCurrent()
            lotteries.recordFailure.value = IOException("Parent offline")
            sites.streamFailure.value = IOException("Site offline")
            runCurrent()
            model.refreshTime()
            model.back()
            model.requestExit()
            model.confirm()
            runCurrent()
            assertEquals(BookingSubmission.Submitting, model.submission.value)
            assertFalse(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            assertEquals(1, bookings.reserveRequests.size)
            held.complete(record.id)
            runCurrent()
            assertEquals(record.id, model.completion.value)
        }

    /** Current rate/rules changes cannot silently rewrite the request already being confirmed. */
    @Test fun sourceChangesPreserveTheFrozenAttemptAndDisplayedAmount() =
        runBookingTest {
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = { held.await() }
            val model = model()
            review(model)
            model.confirm()
            runCurrent()
            sites.records.value =
                listOf(
                    site.copy(
                        details =
                            site.details.copy(
                                nightlyPriceCents = 7000,
                                rules = "Changed after confirmation started",
                            ),
                    ),
                )
            clock.advance(Duration.ofSeconds(91))
            model.refreshTime()
            runCurrent()
            assertEquals(listOf(expectedRequest()), bookings.reserveRequests)
            assertEquals(BookingSubmission.Submitting, model.submission.value)
            assertEquals(BookingQuote.Value(13000), content(model).quote)
            assertEquals(site.details.rules, content(model).draft.agreement?.rules)
            held.complete(record.id)
            runCurrent()
            assertEquals(record.id, model.completion.value)
        }

    /** Confirm cannot skip the four-step contract or manufacture first-review acceptance. */
    @Test fun confirmFromEarlierStepsNeverReservesOrAdvances() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.confirm()
            assertEquals(BookingStep.DATES, content(model).draft.step)
            dates(model)
            model.next(BookingStep.DATES)
            model.confirm()
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            model.next(BookingStep.GUESTS)
            model.confirm()
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertTrue(bookings.reserveRequests.isEmpty())
        }

    /** A changed amount requires explicit approval before confirmation. */
    @Test fun changedAmountCannotBeSubmittedUntilExplicitlyReapproved() =
        runBookingTest {
            bookings.reserveHandler = { record.id }
            val model = model()
            review(model)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = 7000)))
            runCurrent()
            model.confirm()
            runCurrent()
            assertTrue(bookings.reserveRequests.isEmpty())
            assertNull(content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(14000)
            model.confirm()
            runCurrent()
            assertEquals(
                listOf(expectedRequest().copy(expectedTotalCents = 14000)),
                bookings.reserveRequests,
            )
        }

    /** Stale terms and changed capacity are rechecked at Confirm, even after arriving on Review. */
    @Test fun finalValidationReturnsToTheAffectedInputWithoutWriting() =
        runBookingTest {
            val model = model()
            review(model)
            sites.records.value = listOf(site.copy(details = site.details.copy(maxGuests = 2)))
            runCurrent()
            model.confirm()
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            assertEquals("3", content(model).draft.guestInput)
            assertTrue(bookings.reserveRequests.isEmpty())
            model.editGuests("2")
            model.next(BookingStep.GUESTS)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(rules = "New rules")))
            runCurrent()
            model.confirm()
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertNull(content(model).draft.agreement)
            assertTrue(bookings.reserveRequests.isEmpty())
        }

    /** A typed conflict is known not to have committed; date correction preserves the rest. */
    @Test fun knownDateConflictClearsUncertaintyAndRefreshesRetainedDates() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = {
                throw PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Claimed")
            }
            val model = model(saved)
            review(model)
            val readsBefore = sites.observedCalendars.size
            model.confirm()
            runCurrent()
            assertEquals(
                BookingSubmission.Rejected(PersistenceFailure.Reason.CONFLICT),
                model.submission.value,
            )
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals(end, content(model).draft.checkOut)
            assertEquals("3", content(model).draft.guestInput)
            assertTrue(sites.observedCalendars.size > readsBefore)
            assertEquals(1, bookings.reserveRequests.size)
            model.back()
            assertTrue(model.state.value is BookingUiState.Content)
        }

    /** Rejected quote/terms must be approved again; no observed update can auto-submit. */
    @Test fun staleQuoteInvalidatesApprovalsAndRetainsUnsubmittedInputs() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = {
                sites.records.value =
                    listOf(
                        site.copy(
                            details =
                                site.details.copy(
                                    nightlyPriceCents = 7000,
                                    rules = "Current server rules",
                                ),
                        ),
                    )
                throw PersistenceFailure(PersistenceFailure.Reason.STALE_QUOTE, "Changed")
            }
            val model = model(saved)
            review(model)
            model.confirm()
            runCurrent()
            assertEquals(
                BookingSubmission.Rejected(PersistenceFailure.Reason.STALE_QUOTE),
                model.submission.value,
            )
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
            assertEquals(start, content(model).draft.checkIn)
            assertEquals("3", content(model).draft.guestInput)
            assertNull(content(model).draft.agreement)
            assertNull(content(model).draft.reviewedTotalCents)
            model.confirm()
            runCurrent()
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** A lost acknowledgement reads retained history once and delivers only its actual saved ID. */
    @Test fun transportAmbiguityRecoversTheActualSavedResultWithoutRepeatingReserve() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = {
                bookings.records.value = listOf(record)
                throw IOException("Lost acknowledgement")
            }
            val model = model(saved)
            review(model)
            model.confirm()
            runCurrent()
            assertEquals(1, bookings.reserveRequests.size)
            assertEquals(record.id, model.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), model.state.value)
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
            model.acknowledgeCompletion()
            model.refreshTime()
            runCurrent()
            assertNull(model.completion.value)
            assertTrue(bookings.observedRecordIds.isEmpty())
        }

    /** Recovery progress/failure remains an independent lock until an explicit read-only retry. */
    @Test fun failedRecoveryBlocksBlindResubmissionAndRetriesOnlyItsLookup() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = { throw IOException("Unknown reserve") }
            val held = CompletableDeferred<Unit>()
            val model = model(saved)
            review(model)
            bookings.opportunityHandler = { _, _ ->
                held.await()
                throw IOException("Unknown read")
            }
            model.confirm()
            runCurrent()
            assertEquals(BookingSubmission.Recovering, model.submission.value)
            model.confirm()
            model.retryRecovery()
            held.complete(Unit)
            runCurrent()
            assertEquals(BookingSubmission.Unknown, model.submission.value)
            assertEquals(true, saved.get<Boolean>("bookingRecoveryRequired"))
            model.confirm()
            model.back()
            model.requestExit()
            model.editStep(BookingStep.DATES)
            assertFalse(model.canReadPolicy())
            assertFalse(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            bookings.opportunityHandler = { _, _ -> record }
            model.retryRecovery()
            model.retryRecovery()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(1, bookings.reserveRequests.size)
            assertEquals(3, bookings.opportunityRequests.size)
        }

    /** Confirmed absence releases the lock but requires a new explicit, revalidated action. */
    @Test fun absentRecoveryNeverAutomaticallyReplaysTheFrozenRequest() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = { throw IOException("Unknown acknowledgement") }
            val model = model(saved)
            review(model)
            model.confirm()
            runCurrent()
            assertEquals(BookingSubmission.Absent, model.submission.value)
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
            assertNull(model.completion.value)
            assertEquals(1, bookings.reserveRequests.size)
            runCurrent()
            assertEquals(1, bookings.reserveRequests.size)
            bookings.reserveHandler = { record.id }
            model.confirm()
            runCurrent()
            assertEquals(2, bookings.reserveRequests.size)
            assertEquals(record.id, model.completion.value)
        }

    /** Restored uncertainty resolves even when current winning/listing source facts are missing. */
    @Test fun recreatedMarkerUsesIdentityOnlyAndRetainsCancelledConsumption() =
        runBookingTest {
            val saved = SavedStateHandle()
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = { held.await() }
            val first = model(saved)
            review(first)
            first.confirm()
            runCurrent()
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            assertEquals(true, values["bookingRecoveryRequired"])
            assertTrue(
                values.values.all { it == null || it is String || it is Long || it is Boolean },
            )
            store.clear()
            runCurrent()
            lotteries.records.value = emptyList()
            lotteries.entries.value = emptyList()
            sites.records.value = emptyList()
            bookings.records.value =
                listOf(
                    record.copy(
                        cancellation = ReservationCancellation(now, "owner", Role.OWNER, null),
                        payment = record.payment.copy(refundedAt = now),
                    ),
                )
            val restored = model(SavedStateHandle(values))
            runCurrent()
            assertEquals(record.id, restored.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), restored.state.value)
            assertEquals(1, bookings.reserveRequests.size)
            assertNull(first.completion.value)
        }

    /** An interrupted call retains uncertainty and offers read-only recovery. */
    @Test fun cancelledActiveAttemptCanRecoverWithoutASecondReserve() =
        runBookingTest {
            val saved = SavedStateHandle()
            bookings.reserveHandler = { throw CancellationException("Interrupted request") }
            val model = model(saved)
            review(model)
            model.confirm()
            runCurrent()
            assertEquals(BookingSubmission.Unknown, model.submission.value)
            assertEquals(true, saved.get<Boolean>("bookingRecoveryRequired"))
            bookings.records.value = listOf(record)
            model.retryRecovery()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** Confirm rechecks the current clock rather than trusting the last rendered open window. */
    @Test fun elapsedWindowCannotSubmitFromAnEarlierRenderedReview() =
        runBookingTest {
            val model = model()
            review(model)
            clock.advance(Duration.ofSeconds(91))
            model.confirm()
            runCurrent()
            assertEquals(
                BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_CLOSED),
                model.state.value,
            )
            assertTrue(bookings.reserveRequests.isEmpty())
            assertEquals(BookingSubmission.Idle, model.submission.value)
        }

    /** A restored Review cannot bypass the reserved eligibility function. */
    @Test fun missingEligibilityReturnsRestoredReviewToItsPendingDates() =
        runBookingTest {
            val saved =
                SavedStateHandle(
                    mapOf(
                        "bookingCheckIn" to start.toString(),
                        "bookingCheckOut" to end.toString(),
                        "bookingGuests" to "3",
                        "bookingStep" to "REVIEW",
                        "bookingRules" to site.details.rules,
                        "bookingPolicy" to BookingPolicy.VERSION,
                        "bookingReviewedTotal" to 13000L,
                    ),
                )
            val model = model(saved, useHelper = false)
            model.setDatesVisible(true)
            runCurrent()
            model.confirm()
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertEquals(BookingStepError.EligibilityPending, content(model).error)
            assertTrue(bookings.reserveRequests.isEmpty())
        }

    /** A consumed response retains cancelled history rather than permitting another reservation. */
    @Test fun alreadyUsedOpensCancelledHistoryWithoutAnotherMutation() =
        runBookingTest {
            bookings.reserveHandler = {
                bookings.records.value =
                    listOf(
                        record.copy(
                            cancellation = ReservationCancellation(now, "owner", Role.OWNER, null),
                            payment = record.payment.copy(refundedAt = now),
                        ),
                    )
                throw PersistenceFailure(PersistenceFailure.Reason.ALREADY_USED, "Consumed")
            }
            val model = model()
            review(model)
            model.confirm()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(BookingUiState.Reserved(record.id), model.state.value)
            model.confirm()
            model.retryRecovery()
            runCurrent()
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** Retired graphs reject noncooperative write results and every queued command callback. */
    @Test fun graphRetirementCannotPublishLateSuccessOrStartAnotherAttempt() =
        runBookingTest {
            val held = CompletableDeferred<String>()
            bookings.reserveHandler = { withContext(NonCancellable) { held.await() } }
            val saved = SavedStateHandle()
            val model = model(saved)
            review(model)
            model.confirm()
            runCurrent()
            try {
                assertEquals(1, bookings.reserveRequests.size)
                store.clear()
                runCurrent()
            } finally {
                held.complete(record.id)
            }
            runCurrent()
            model.confirm()
            model.retryRecovery()
            model.requestExit()
            runCurrent()
            assertNull(model.completion.value)
            assertFalse(model.exitRequested.value)
            assertEquals(1, bookings.reserveRequests.size)
            assertEquals(true, saved.get<Boolean>("bookingRecoveryRequired"))
            assertEquals(0, sites.activeCalendarObservers)
        }

    /** A foreground consumption read must finish before a new confirm can use the win. */
    @Test fun heldForegroundLookupCannotOverlapAReservationAttempt() =
        runBookingTest {
            val model = model()
            review(model)
            val held = CompletableDeferred<Unit>()
            bookings.opportunityHandler = { _, _ ->
                held.await()
                null
            }
            model.refreshTime()
            runCurrent()
            assertEquals(BookingUiState.Loading, model.state.value)
            model.confirm()
            assertTrue(bookings.reserveRequests.isEmpty())
            held.complete(Unit)
            runCurrent()
            bookings.reserveHandler = { record.id }
            model.confirm()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertEquals(1, bookings.reserveRequests.size)
        }

    /** Unknown restored consumption remains locked without relying on current win/listing reads. */
    @Test fun failedRestoredRecoveryCanRetryWhileAllCurrentSourcesAreAbsent() =
        runBookingTest {
            lotteries.records.value = emptyList()
            lotteries.entries.value = emptyList()
            sites.records.value = emptyList()
            bookings.opportunityHandler = { _, _ -> throw IOException("Unknown history") }
            val saved = SavedStateHandle(mapOf("bookingRecoveryRequired" to true))
            val model = model(saved)
            runCurrent()
            assertEquals(BookingSubmission.Unknown, model.submission.value)
            model.back()
            model.requestExit()
            model.confirm()
            assertFalse(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            bookings.opportunityHandler = { _, _ -> record }
            model.retryRecovery()
            runCurrent()
            assertEquals(record.id, model.completion.value)
            assertTrue(bookings.reserveRequests.isEmpty())
            assertTrue(saved.get<Boolean>("bookingRecoveryRequired") != true)
        }

    /** Creates a graph-owned parent with a fixed F15 fixture, never an exercise implementation.
     * @param saved Primitive draft and uncertainty state to restore.
     * @param useHelper Whether a known F15 result is available to this parent.
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
            clock,
            saved,
            if (useHelper) { _, _ -> nights } else null,
        ).also { store.put("confirmation-${creations++}", it) }

    /** Establishes a real selected range using only current parent actions.
     * @param model Parent under test.
     */
    private fun TestScope.dates(model: BookingViewModel) {
        runCurrent()
        model.setDatesVisible(true)
        model.selectCheckIn(start)
        model.selectCheckOut(end)
        runCurrent()
    }

    /** Walks all four actual draft steps and approves the exact first review amount.
     * @param model Parent under test.
     */
    private fun TestScope.review(model: BookingViewModel) {
        dates(model)
        model.next(BookingStep.DATES)
        runCurrent()
        model.editGuests("3")
        model.next(BookingStep.GUESTS)
        model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
        model.next(BookingStep.TERMS)
        runCurrent()
        assertEquals(BookingStep.REVIEW, content(model).draft.step)
        assertTrue(model.canReadPolicy())
    }

    /** Describes the exact immutable first request without client-owned payment/timestamps. */
    private fun expectedRequest() =
        BookingRequest(
            draw.id,
            "entrant",
            site.id,
            start,
            end,
            3,
            13000,
            site.details.rules,
            BookingPolicy.VERSION,
        )

    /** Checks a source-backed content outcome before accessing its input fields.
     * @param model Parent under test.
     */
    private fun content(model: BookingViewModel): BookingUiState.Content {
        assertTrue(model.state.value is BookingUiState.Content)
        return model.state.value as BookingUiState.Content
    }

    /** Retires deadlines and held commands before the scheduler drains remaining work.
     * @param block Contract assertions with controlled repository outcomes.
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

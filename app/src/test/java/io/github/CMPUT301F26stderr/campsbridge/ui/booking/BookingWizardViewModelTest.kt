package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.YearMonth

/** Explicit wizard actions retain one draft and require current terms/price approval. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingWizardViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private var count = 0
    private val now = CampingFixtures.now.plusSeconds(150)
    private val site = CampingFixtures.campsite()
    private val lottery = CampingFixtures.lottery().copy(drawnAt = now.minusSeconds(20))
    private val start = CampingFixtures.arrival
    private val end = start.plusDays(2)
    private val dates = setOf(start, start.plusDays(1))
    private var eligible = dates
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value = dates.sorted().map { CalendarNight(it, true) }
        }
    private val lotteries =
        FakeLotteryRepository(listOf(lottery)).apply {
            entries.value =
                listOf(LotteryEntry(lottery.id, "entrant", EntryState.OFFERED, now, now))
            liveOffers.value = mapOf(lottery.id to listOf(LotteryOffer(site.id, dates.sorted())))
        }
    private val bookings = FakeBookingRepository()

    /** Date advancement presents the ordered existing range result and does not write. */
    @Test fun nextRequiresDatesBeforeMovingToGuests() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(
                BookingStepError.Dates(StaySelectionInput.MISSING_CHECK_IN),
                content(model).error,
            )
            model.selectCheckIn(start)
            model.next(content(model).draft.step)
            assertEquals(
                BookingStepError.Dates(StaySelectionInput.MISSING_CHECK_OUT),
                content(model).error,
            )
            model.selectCheckOut(start)
            model.next(content(model).draft.step)
            assertEquals(
                BookingStepError.Dates(StaySelectionInput.INVALID_RANGE),
                content(model).error,
            )
            model.selectCheckOut(end)
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            assertEquals(null, content(model).error)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Pending F15 and pending inventory never advance using an invented all-free result. */
    @Test fun absentEligibilityHelperBlocksDateAdvancementExplicitly() =
        runBookingTest {
            val model = model(useHelper = false)
            chooseDates(model)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertEquals(BookingStepError.EligibilityPending, content(model).error)
        }

    /** A held or failed selected range remains unknown rather than a valid chosen stay. */
    @Test fun unknownInventoryBlocksDateAdvancementWithRetainedInput() =
        runBookingTest {
            val gate = CompletableDeferred<Unit>()
            sites.calendarHandler =
                { _, _, _ ->
                    flow {
                        gate.await()
                        emit(sites.calendarNights.value)
                    }
                }
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            assertEquals(BookingStepError.InventoryUnknown, content(model).error)
            assertEquals(start, content(model).draft.checkIn)
            gate.complete(Unit)
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
        }

    /** The existing parser rejects incomplete, nonpositive, fractional and excessive guests. */
    @Test fun guestsUseCurrentCapacityAndExistingPositiveCountFeedback() =
        runBookingTest {
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            runCurrent()
            val invalid =
                mapOf(
                    "" to PositiveCountInput.Error.REQUIRED,
                    "0" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                    "1.5" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                    "9" to PositiveCountInput.Error.EXCEEDS_MAXIMUM,
                )
            invalid.forEach { (text, expected) ->
                model.editGuests(text)
                model.next(content(model).draft.step)
                assertEquals(BookingStep.GUESTS, content(model).draft.step)
                assertEquals(BookingStepError.Guests(expected), content(model).error)
            }
            model.editGuests(" 08 ")
            model.next(content(model).draft.step)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertEquals(" 08 ", content(model).draft.guestInput)
        }

    /** The real four-step order accepts current terms and retains the amount actually reviewed. */
    @Test fun completeLocalWizardReachesReviewWithoutCreatingAReservation() =
        runBookingTest {
            val model = model()
            review(model)
            val draft = content(model).draft
            assertEquals(BookingStep.REVIEW, draft.step)
            assertEquals(
                BookingAgreement(site.details.rules, BookingPolicy.VERSION),
                draft.agreement,
            )
            assertEquals(13000L, draft.reviewedTotalCents)
            assertEquals(BookingQuote.Value(13000), content(model).quote)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
        }

    /** Reading a policy or arriving at Terms cannot silently tick the agreement checkbox. */
    @Test fun agreementRequiresExplicitCurrentRulesAndPolicy() =
        runBookingTest {
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            model.next(content(model).draft.step)
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertEquals(BookingStepError.AgreementRequired, content(model).error)
            model.acceptTerms(true, site.details.rules, "unbundled-v0")
            assertEquals(null, content(model).draft.agreement)
            model.acceptTerms(true, "rules that were not displayed", BookingPolicy.VERSION)
            assertEquals(null, content(model).draft.agreement)
            model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
        }

    /** Confirmed new rules invalidate acceptance and reject stale checkbox callbacks. */
    @Test fun changedRulesCannotBeAcceptedThroughAnOldUiCallback() =
        runBookingTest {
            val model = model()
            review(model)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(rules = "New rules")))
            runCurrent()
            assertEquals(null, content(model).draft.agreement)
            model.editStep(BookingStep.TERMS)
            model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
            assertEquals(null, content(model).draft.agreement)
            assertEquals(BookingStepError.AgreementRequired, content(model).error)
            model.acceptTerms(true, "New rules", BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals(
                BookingAgreement("New rules", BookingPolicy.VERSION),
                content(model).draft.agreement,
            )
        }

    /** Clearing the checkbox preserves other input but removes permission to enter review again. */
    @Test fun uncheckedAgreementBlocksReviewReturn() =
        runBookingTest {
            val model = model()
            review(model)
            model.editStep(BookingStep.TERMS)
            model.acceptTerms(false, site.details.rules, BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertEquals(BookingStepError.AgreementRequired, content(model).error)
            assertEquals(start, content(model).draft.checkIn)
        }

    /** Changed price loses approval even if a later update changes it back to the old rate. */
    @Test fun livePriceChangeRequiresExplicitApprovalOfTheCurrentlyDisplayedAmount() =
        runBookingTest {
            val model = model()
            review(model)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = 7000)))
            runCurrent()
            assertEquals(null, content(model).draft.reviewedTotalCents)
            assertEquals(BookingQuote.Value(14000), content(model).quote)
            model.acknowledgeQuote(13000)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(14000)
            assertEquals(14000L, content(model).draft.reviewedTotalCents)
            sites.records.value = listOf(site)
            runCurrent()
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(14000)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(13000)
            assertEquals(13000L, content(model).draft.reviewedTotalCents)
        }

    /** Review date edits return after validation and require approval of the changed total. */
    @Test fun dateReviewEditReusesDraftAndReturnsToReview() =
        runBookingTest {
            val model = model()
            review(model)
            model.editStep(BookingStep.DATES)
            assertTrue(content(model).draft.returnToReview)
            model.selectCheckOut(start.plusDays(1))
            assertEquals(null, content(model).draft.reviewedTotalCents)
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertFalse(content(model).draft.returnToReview)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(6500)
            assertEquals(6500L, content(model).draft.reviewedTotalCents)
            assertEquals(
                BookingAgreement(site.details.rules, BookingPolicy.VERSION),
                content(model).draft.agreement,
            )
        }

    /** Guest edits retain the stay/terms and do not create a separate wizard instance. */
    @Test fun guestReviewEditReturnsToReviewWithValidatedRawText() =
        runBookingTest {
            val model = model()
            review(model)
            model.editStep(BookingStep.GUESTS)
            model.editGuests("4")
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals("4", content(model).draft.guestInput)
            assertEquals(13000L, content(model).draft.reviewedTotalCents)
        }

    /** An invalid earlier step retains the review caller while routing through its correction. */
    @Test fun crossStepCorrectionPreservesReviewReturnAndOtherValidInput() =
        runBookingTest {
            val model = model()
            review(model)
            model.editStep(BookingStep.GUESTS)
            model.editGuests("3")
            eligible = emptySet()
            sites.calendarNights.value = dates.sorted().map { CalendarNight(it, false) }
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertTrue(content(model).draft.returnToReview)
            assertEquals(
                BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS),
                content(model).error,
            )
            eligible = setOf(start.plusDays(1))
            sites.calendarNights.value = listOf(CalendarNight(start.plusDays(1), true))
            model.selectCheckIn(start.plusDays(1))
            model.selectCheckOut(end)
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals("3", content(model).draft.guestInput)
            assertEquals(
                BookingAgreement(site.details.rules, BookingPolicy.VERSION),
                content(model).draft.agreement,
            )
            assertEquals(null, content(model).draft.reviewedTotalCents)
        }

    /** Back follows actual steps; an explicit review edit returns directly to its caller. */
    @Test fun backUsesStepHistoryAndReviewEditReturn() =
        runBookingTest {
            val model = model()
            review(model)
            model.editStep(BookingStep.GUESTS)
            model.back()
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            model.back()
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            model.back()
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            model.back()
            assertEquals(BookingStep.DATES, content(model).draft.step)
            model.back()
            assertTrue(model.discardPrompt.value)
            assertFalse(model.exitRequested.value)
        }

    /** Leaving the date step immediately retires its month while preserving selected nights. */
    @Test fun movingToGuestsStopsVisibleMonthWithoutDroppingSelectedValidation() =
        runBookingTest {
            val model = model()
            model.setDatesVisible(true)
            chooseDates(model)
            assertTrue(content(model).calendar.visible != null)
            model.next(content(model).draft.step)
            runCurrent()
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            assertEquals(null, content(model).calendar.visible)
            assertEquals(1, sites.activeCalendarObservers)
            assertEquals(StaySelectionInput.VALID, content(model).stayValidation)
            model.back()
            runCurrent()
            assertTrue(content(model).calendar.visible != null)
        }

    /** A later capacity reduction is rechecked before review, retaining entered guests. */
    @Test fun capacityChangeRechecksGuestsBeforeReview() =
        runBookingTest {
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            model.editGuests("3")
            model.next(content(model).draft.step)
            runCurrent()
            model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
            sites.records.value = listOf(site.copy(details = site.details.copy(maxGuests = 2)))
            runCurrent()
            model.next(content(model).draft.step)
            assertEquals(BookingStep.GUESTS, content(model).draft.step)
            assertEquals("3", content(model).draft.guestInput)
            assertEquals(
                BookingStepError.Guests(PositiveCountInput.Error.EXCEEDS_MAXIMUM),
                content(model).error,
            )
        }

    /** An unrepresentable current quote cannot establish review approval. */
    @Test fun quoteOverflowStopsReviewWithoutLosingDatesOrAcceptedRules() =
        runBookingTest {
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = Long.MAX_VALUE)))
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            model.next(content(model).draft.step)
            runCurrent()
            model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.TERMS, content(model).draft.step)
            assertEquals(BookingStepError.QuoteOverflow, content(model).error)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            assertEquals(start, content(model).draft.checkIn)
        }

    /** Primitive recreation retains the step/accepted text/amount without replaying Next. */
    @Test fun reviewDraftRestoresWithItsExactApprovalPrimitives() =
        runBookingTest {
            val saved = SavedStateHandle()
            val first = model(saved)
            review(first)
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            assertTrue(
                values.values.all { it == null || it is String || it is Boolean || it is Long },
            )
            store.clear()
            runCurrent()
            val restored = model(SavedStateHandle(values))
            runCurrent()
            assertEquals(content(first).draft, content(restored).draft)
            assertEquals(BookingStep.REVIEW, content(restored).draft.step)
            assertFalse(restored.exitRequested.value)
            assertFalse(restored.discardPrompt.value)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Restored terms/amount are checked against current sources rather than trusted blindly. */
    @Test fun restoredReviewInvalidatesChangedRulesPolicyAndPrice() =
        runBookingTest {
            val saved =
                SavedStateHandle(
                    mapOf(
                        "bookingCheckIn" to start.toString(),
                        "bookingCheckOut" to end.toString(),
                        "bookingStep" to "REVIEW",
                        "bookingRules" to "Historical rules",
                        "bookingPolicy" to "old-policy",
                        "bookingReviewedTotal" to 9999L,
                    ),
                )
            val model = model(saved)
            runCurrent()
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals(null, content(model).draft.agreement)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            assertEquals(start, content(model).draft.checkIn)
        }

    /** Corrupt enum/negative amount/partial agreement do not fabricate accepted state. */
    @Test fun malformedRestoredWizardFieldsFallBackToUnapprovedDates() =
        runBookingTest {
            val model =
                model(
                    SavedStateHandle(
                        mapOf(
                            "bookingStep" to "PAYMENT",
                            "bookingReviewedTotal" to -1L,
                            "bookingRules" to "incomplete",
                        ),
                    ),
                )
            runCurrent()
            assertEquals(BookingStep.DATES, content(model).draft.step)
            assertEquals(null, content(model).draft.agreement)
            assertEquals(null, content(model).draft.reviewedTotalCents)
        }

    /** Unchanged input leaves immediately; navigating calendar months alone is not a form edit. */
    @Test fun pristineExitDoesNotRequireDiscardConfirmation() =
        runBookingTest {
            val model = model()
            runCurrent()
            model.changeMonth(YearMonth.from(start).plusMonths(1))
            model.requestExit()
            assertFalse(model.discardPrompt.value)
            assertTrue(model.exitRequested.value)
            model.acknowledgeExit()
            assertFalse(model.exitRequested.value)
        }

    /** Dirty input can still be abandoned after a required source fails. */
    @Test fun discardWorksDuringFailureAndDismissRetainsInput() =
        runBookingTest {
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            model.editGuests("3")
            lotteries.recordFailure.value = IOException("offline")
            runCurrent()
            model.requestExit()
            assertTrue(model.discardPrompt.value)
            model.dismissDiscard()
            assertFalse(model.discardPrompt.value)
            assertFalse(model.exitRequested.value)
            model.requestExit()
            model.confirmDiscard()
            assertTrue(model.exitRequested.value)
            assertFalse(model.discardPrompt.value)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Stale UI actions cannot alter the draft while discard/exit navigation is pending. */
    @Test fun discardPromptAndPendingExitGuardFieldAndStepCallbacks() =
        runBookingTest {
            val model = model()
            review(model)
            val draft = content(model).draft
            model.requestExit()
            assertTrue(model.discardPrompt.value)
            model.editGuests("7")
            model.editStep(BookingStep.DATES)
            model.selectCheckIn(start.plusDays(1))
            model.acceptTerms(false, site.details.rules, BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            model.back()
            assertEquals(draft, content(model).draft)
            model.confirmDiscard()
            model.editGuests("6")
            model.editStep(BookingStep.GUESTS)
            assertEquals(draft, content(model).draft)
        }

    /** A retired graph cannot open prompts, navigate or accept new approval callbacks. */
    @Test fun clearedWizardRejectsStaleDiscardAndApprovalActions() =
        runBookingTest {
            val model = model()
            review(model)
            store.clear()
            runCurrent()
            model.requestExit()
            model.confirmDiscard()
            model.acceptTerms(false, site.details.rules, BookingPolicy.VERSION)
            model.acknowledgeQuote(999)
            assertFalse(model.discardPrompt.value)
            assertFalse(model.exitRequested.value)
        }

    /** Creates a parent using only a fixed eligibility fixture.
     * @param saved Primitive restoration values.
     * @param useHelper Whether the reserved F15 is supplied for this contract.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        useHelper: Boolean = true,
    ) =
        BookingViewModel(
            "entrant",
            lottery.id,
            site.id,
            lotteries,
            sites,
            bookings,
            MutableClock(now),
            saved,
            if (useHelper) { _, _ -> eligible } else null,
        ).also { store.put("wizard-${count++}", it) }

    /** Drives the real date actions and waits only for controlled reads.
     * @param model Parent under test.
     */
    private fun TestScope.chooseDates(model: BookingViewModel) {
        runCurrent()
        content(model)
        model.selectCheckIn(start)
        model.selectCheckOut(end)
        runCurrent()
    }

    /** Guest/terms edits cannot implicitly approve a price changed after the first review. */
    @Test fun returningFromAnUnrelatedReviewEditCannotApproveChangedPrice() =
        runBookingTest {
            val model = model()
            review(model)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = 7000)))
            runCurrent()
            model.editStep(BookingStep.GUESTS)
            model.editGuests("3")
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.editStep(BookingStep.TERMS)
            model.next(content(model).draft.step)
            assertEquals(BookingStep.REVIEW, content(model).draft.step)
            assertEquals(null, content(model).draft.reviewedTotalCents)
            model.acknowledgeQuote(14000)
            assertEquals(14000L, content(model).draft.reviewedTotalCents)
        }

    /** Recreation cannot disguise a return to review as the first displayed quote. */
    @Test fun invalidatedQuoteApprovalSurvivesRecreationDuringAReviewEdit() =
        runBookingTest {
            val saved = SavedStateHandle()
            val first = model(saved)
            review(first)
            sites.records.value =
                listOf(site.copy(details = site.details.copy(nightlyPriceCents = 7000)))
            runCurrent()
            first.editStep(BookingStep.GUESTS)
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            runCurrent()
            val restored = model(SavedStateHandle(values))
            runCurrent()
            assertEquals(BookingStep.GUESTS, content(restored).draft.step)
            restored.next(content(restored).draft.step)
            assertEquals(BookingStep.REVIEW, content(restored).draft.step)
            assertEquals(null, content(restored).draft.reviewedTotalCents)
            restored.acknowledgeQuote(14000)
            assertEquals(14000L, content(restored).draft.reviewedTotalCents)
        }

    /** Retired step callbacks cannot change dates/month/guests on a later screen. */
    @Test fun fieldCallbacksAreAcceptedOnlyByTheirOwningStep() =
        runBookingTest {
            val model = model()
            chooseDates(model)
            model.next(content(model).draft.step)
            runCurrent()
            val guestsDraft = content(model).draft
            val month = content(model).month
            model.selectCheckIn(start.plusDays(1))
            model.selectCheckOut(end.plusDays(1))
            model.changeMonth(month.plusMonths(1))
            assertEquals(guestsDraft, content(model).draft)
            assertEquals(month, content(model).month)
            model.editGuests("3")
            model.next(content(model).draft.step)
            val termsDraft = content(model).draft
            model.editGuests("7")
            assertEquals(termsDraft, content(model).draft)
            model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
            model.next(content(model).draft.step)
            val reviewed = content(model).draft
            model.editGuests("8")
            model.selectCheckIn(null)
            model.selectCheckOut(null)
            model.changeMonth(month.minusMonths(1))
            assertEquals(reviewed, content(model).draft)
            assertEquals(month, content(model).month)
        }

    /** Drives the real four-step actions; never assigns state or injects a fake agreement.
     * @param model Parent under test.
     */
    private fun TestScope.review(model: BookingViewModel) {
        chooseDates(model)
        model.next(content(model).draft.step)
        runCurrent()
        model.next(content(model).draft.step)
        model.acceptTerms(true, site.details.rules, BookingPolicy.VERSION)
        model.next(content(model).draft.step)
        runCurrent()
        assertEquals(BookingStep.REVIEW, content(model).draft.step)
    }

    /** Checks outcome before exposing typed facts, preserving useful expected-red assertions.
     * @param model Parent under test.
     */
    private fun content(model: BookingViewModel): BookingUiState.Content {
        assertTrue(model.state.value is BookingUiState.Content)
        return model.state.value as BookingUiState.Content
    }

    /** Retires parent deadlines before the test scheduler drains future work.
     * @param block Assertions using explicit sources and actions.
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

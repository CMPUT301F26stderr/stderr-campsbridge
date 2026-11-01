package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** BK3 content-only interactions: all effects are recorded callbacks, without live booking. */
class BookingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val next = mutableListOf<BookingStep>()
    private val edits = mutableListOf<BookingStep>()
    private val dates = mutableListOf<LocalDate>()
    private val guests = mutableListOf<String>()
    private val agreements = mutableListOf<Triple<Boolean, String, String>>()
    private val quotes = mutableListOf<Long>()
    private val retries = mutableListOf<BookingReadSource>()
    private val reservations = mutableListOf<String>()
    private var confirms = 0
    private var backs = 0
    private var policies = 0
    private var clears = 0
    private var visibleRetries = 0
    private var selectedRetries = 0
    private var dismissed = 0
    private var discarded = 0

    /** Every supplied step has its own heading/progress and delegates the corresponding action. */
    @Test fun fourStepsShowProgressAndDispatchOnlyTheirOwnActions() {
        val current = mutableStateOf<BookingUiState>(ready(BookingStep.DATES))
        show({ current.value })
        val titles = listOf("Choose dates", "Guests", "Rules and policy", "Review your stay")
        for (step in BookingStep.entries) {
            compose.runOnIdle { current.value = ready(step) }
            compose.onNodeWithText(titles[step.ordinal]).assertIsDisplayed()
            compose.onNodeWithText("Step ${step.ordinal + 1} of 4").assertIsDisplayed()
            compose
                .onNodeWithText(
                    if (step ==
                        BookingStep.REVIEW
                    ) {
                        "Confirm reservation"
                    } else {
                        "Next"
                    },
                ).performClick()
            assertEquals(step, (current.value as BookingUiState.Content).draft.step)
        }
        assertEquals(listOf(BookingStep.DATES, BookingStep.GUESTS, BookingStep.TERMS), next)
        assertEquals(1, confirms)
    }

    /** Date taps and source-specific retries leave mutation and recovery with the caller. */
    @Test fun datesExposeSelectableEndpointsAndIndependentRetries() {
        val initial = ready(BookingStep.DATES)
        val current = mutableStateOf(initial)
        show({ current.value })
        val arrival = requireNotNull(initial.draft.checkIn)
        compose.onNodeWithTag("calendar-date-$arrival").performScrollTo().performClick()
        assertEquals(listOf(arrival), dates)
        compose.onNodeWithText("Clear dates").performScrollTo().performClick()
        assertEquals(1, clears)
        compose.runOnIdle {
            current.value =
                initial.copy(
                    calendar =
                        BookingCalendarReadState(
                            CalendarInventoryRead.Failure,
                            CalendarInventoryRead.Failure,
                        ),
                    visibleEligibleNights = null,
                    selectedEligibleNights = null,
                )
        }
        compose.onNodeWithText("Retry calendar").performScrollTo().performClick()
        compose.onNodeWithText("Retry selected dates").performScrollTo().performClick()
        assertEquals(1, visibleRetries)
        assertEquals(1, selectedRetries)
        compose.onNodeWithTag("calendar-date-$arrival").performScrollTo().assertIsNotEnabled()
    }

    /** Raw invalid text is preserved, while bounded controls use the supplied parsed count. */
    @Test fun guestsReportRawInputAndRespectCapacity() {
        val initial = ready(BookingStep.GUESTS)
        val current = mutableStateOf(initial)
        show({ current.value })
        compose.onNodeWithContentDescription("Increase Guests").performClick()
        assertEquals("4", guests.last())
        compose.onNodeWithTag("booking-guests").performTextReplacement("invalid")
        assertEquals("invalid", guests.last())
        compose.runOnIdle {
            current.value =
                initial.copy(draft = initial.draft.copy(guestInput = "8"), guestCount = 8)
        }
        compose.onNodeWithContentDescription("Increase Guests").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decrease Guests").assertIsEnabled()
    }

    /** Agreement carries the precise displayed rules and packaged policy version. */
    @Test fun agreementReportsExactDisplayedTermsAndPolicyNavigation() {
        val rules = "Quiet after 10 pm.\nPack out all litter."
        val initial = ready(BookingStep.TERMS)
        val state =
            initial.copy(
                campsite =
                    initial.campsite.copy(
                        details = initial.campsite.details.copy(rules = rules),
                    ),
            )
        show({ state })
        compose.onNodeWithText("Reservation & refund policy").performScrollTo().performClick()
        assertEquals(1, policies)
        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        assertEquals(listOf(Triple(true, rules, BookingPolicy.VERSION)), agreements)
    }

    /** Retained old agreement must not be presented as acceptance of changed rules. */
    @Test fun changedRulesAreNotDisplayedAsAccepted() {
        val initial = ready(BookingStep.REVIEW)
        val state =
            initial.copy(
                campsite =
                    initial.campsite.copy(
                        details = initial.campsite.details.copy(rules = "New rules"),
                    ),
                canConfirm = false,
            )
        val current = mutableStateOf(state)
        show({ current.value })
        compose
            .onNodeWithText("The rules have changed. Review and accept them again.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Current rules and policy accepted").assertDoesNotExist()
        compose.runOnIdle {
            current.value =
                state.copy(draft = state.draft.copy(step = BookingStep.TERMS))
        }
        compose.onNodeWithText(AGREEMENT).performScrollTo().assertIsOff()
    }

    /** Review routes edits and explicit price approval with the exact displayed cents. */
    @Test fun reviewEditsAndUpdatedQuoteRemainExplicitCallbacks() {
        val initial = ready(BookingStep.REVIEW)
        show({ initial.copy(quote = BookingQuote.Value(14000), canConfirm = false) })
        for (label in listOf("Edit dates", "Edit guests", "Review rules and policy")) {
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        assertEquals(listOf(BookingStep.DATES, BookingStep.GUESTS, BookingStep.TERMS), edits)
        compose
            .onNodeWithText("Accept current total:", substring = true)
            .performScrollTo()
            .performClick()
        assertEquals(listOf(14000L), quotes)
        compose.onNodeWithText("Confirm reservation").assertIsNotEnabled()
    }

    /** The conflict view's primary action returns to dates instead of confirming. */
    @Test fun conflictReturnsToDatesWithoutConfirming() {
        val initial = ready(BookingStep.REVIEW)
        show({
            initial.copy(
                error = BookingStepError.Dates(StaySelectionInput.UNAVAILABLE_NIGHTS),
                canConfirm = false,
            )
        })
        compose.onNodeWithText("Choose dates").performClick()
        assertEquals(listOf(BookingStep.DATES), edits)
        assertEquals(0, confirms)
    }

    /** Submitting presentation disables editing, confirmation and both system/app exits. */
    @Test fun submittingBlocksDraftActionsAndExit() {
        show({ ready(BookingStep.REVIEW) }, submitting = { true })
        compose.onNodeWithText("Confirming your reservation…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Edit dates").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Edit guests").performScrollTo().assertIsNotEnabled()
        pressBack()
        assertEquals(0, backs)
        assertEquals(0, confirms)
    }

    /** Loading, failure, unavailability and saved history remain different render outcomes. */
    @Test fun sourceOutcomesRetryAndOpenOnlySuppliedIdentities() {
        val current =
            mutableStateOf<BookingUiState>(BookingUiState.Failure(BookingReadSource.OFFERS))
        show({ current.value })
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf(BookingReadSource.OFFERS), retries)
        compose.runOnIdle { current.value = BookingUiState.Loading }
        compose.onNodeWithText("Loading").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.runOnIdle {
            current.value =
                BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_CLOSED)
        }
        compose.onNodeWithText("The booking deadline has passed.").assertIsDisplayed()
        compose.runOnIdle { current.value = BookingUiState.Reserved("saved /id?1") }
        compose.onNodeWithText("View reservation").performClick()
        assertEquals(listOf("saved /id?1"), reservations)
    }

    /** Discard choices delegate to the owner without replacing or saving the supplied draft. */
    @Test fun discardDialogReportsKeepingAndAbandoningDraft() {
        val prompt = mutableStateOf(true)
        show({ ready(BookingStep.GUESTS) }, discard = { prompt.value })
        compose.onNodeWithText("Keep draft").performClick()
        assertEquals(1, dismissed)
        compose.runOnIdle { prompt.value = false }
        compose.onNodeWithText("Discard this booking draft?").assertDoesNotExist()
        compose.runOnIdle { prompt.value = true }
        compose.onNodeWithText("Discard draft").performClick()
        assertEquals(1, discarded)
    }

    /** Long rules remain scrollable and the footer remains reachable with enlarged fonts. */
    @Test fun largeTextKeepsLongRulesAndFooterReachable() {
        val initial = ready(BookingStep.TERMS)
        val state =
            initial.copy(
                campsite =
                    initial.campsite.copy(
                        details =
                            initial.campsite.details.copy(
                                rules = "Quiet hours and responsible camping.\n".repeat(24),
                            ),
                    ),
            )
        show({ state }, scale = 2f)
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText(AGREEMENT).performScrollTo().performClick()
        assertEquals(1, agreements.size)
        compose.onNodeWithText("Next").performClick()
        assertEquals(listOf(BookingStep.TERMS), next)
    }

    /** Supplies presentation-only reviewed facts; no repository is constructed. */
    private fun ready(step: BookingStep): BookingUiState.Content =
        previewBooking(step).copy(canConfirm = step == BookingStep.REVIEW)

    /** Renders state/callbacks under the real theme in a phone-sized preview surface.
     * @param state Current caller-owned state.
     * @param submitting Parent-owned submission flag.
     * @param discard Parent-owned discard prompt.
     * @param scale Test font scale.
     */
    private fun show(
        state: () -> BookingUiState,
        submitting: () -> Boolean = { false },
        discard: () -> Boolean = { false },
        scale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, scale),
            ) {
                CampsbridgeTheme {
                    Box(Modifier.width(390.dp).height(844.dp).testTag("booking-preview-root")) {
                        BookingScreen(
                            state(),
                            onBack = { backs++ },
                            onNext = { next.add(it) },
                            onCancel = {},
                            onMonth = {},
                            onDate = { dates.add(it) },
                            onClearDates = { clears++ },
                            onGuests = { guests.add(it) },
                            onAccept = {
                                accepted,
                                rules,
                                version,
                                ->
                                agreements.add(Triple(accepted, rules, version))
                            },
                            onEdit = { edits.add(it) },
                            onQuote = { quotes.add(it) },
                            onPolicy = { policies++ },
                            onRetry = { retries.add(it) },
                            onRetryVisible = { visibleRetries++ },
                            onRetrySelected = { selectedRetries++ },
                            onReservation = { reservations.add(it) },
                            onConfirm = { confirms++ },
                            isSubmitting = submitting(),
                            discardPrompt = discard(),
                            onDismissDiscard = { dismissed++ },
                            onConfirmDiscard = { discarded++ },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val AGREEMENT = "I have read and accept these rules and the reservation policy."
    }
}

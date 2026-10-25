package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import java.time.LocalDate
import java.time.YearMonth

/** Independent source to retry without restarting unrelated fixed-ID observations. */
enum class BookingReadSource { LOTTERY, MEMBERSHIP, OFFERS, CAMPSITE, OPPORTUNITY }

/** Confirmed facts that currently prevent a new reservation, rather than retrieval failure. */
enum class BookingUnavailableReason {
    LOTTERY_UNAVAILABLE,
    LOTTERY_CANCELLED,
    DRAW_PENDING,
    NOT_A_WINNER,
    CAMPSITE_UNAVAILABLE,
    OFFER_UNAVAILABLE,
    BOOKING_NOT_OPEN,
    BOOKING_CLOSED,
}

/** Winner/date read outcomes; this packet has no submission or synthetic success state. */
sealed interface BookingUiState {
    /** Required source or server consumption confirmation has not resolved. */
    data object Loading : BookingUiState

    /** One read failed; entered draft values remain owned by the parent.
     * @param source Exact source whose explicit retry is available.
     */
    data class Failure(
        val source: BookingReadSource,
    ) : BookingUiState

    /** Confirmed current facts do not permit date selection.
     * @param reason Specific opportunity/site/window feedback.
     */
    data class Unavailable(
        val reason: BookingUnavailableReason,
    ) : BookingUiState

    /** Known history consumes this win even when cancelled or current sources later fail.
     * @param reservationId Actual saved identity to open with the existing aggregate reader.
     */
    data class Reserved(
        val reservationId: String,
    ) : BookingUiState

    /** Confirmed unused opportunity with editable inputs and independent inventory scopes.
     * @param lottery Confirmed published and drawn lottery in its booking window.
     * @param campsite Confirmed active published site belonging to the lottery owner.
     * @param offer Actual allocation for this site's identity.
     * @param draft Unsubmitted date/guest input.
     * @param month Parent-owned displayed month.
     * @param today Current campsite-local date from the injected clock.
     * @param calendar Actual visible/selected range outcomes; null scopes were not requested.
     * @param eligibilityPending Reserved F15 is absent, so date-dependent continuation is pending.
     * @param visibleEligibleNights F15 result for known visible inventory, or null while unknown.
     * @param stayValidation Ordered date feedback; null when required inventory/F15 is unknown.
     * @param quote Checked current amount for positive date ranges, or null before a range exists.
     */
    data class Content(
        val lottery: Lottery,
        val campsite: Campsite,
        val offer: LotteryOffer,
        val draft: BookingDraft,
        val month: YearMonth,
        val today: LocalDate,
        val calendar: BookingCalendarReadState = BookingCalendarReadState(),
        val eligibilityPending: Boolean = true,
        val visibleEligibleNights: Set<LocalDate>? = null,
        val stayValidation: StaySelectionInput? = null,
        val quote: BookingQuote? = null,
    ) : BookingUiState
}

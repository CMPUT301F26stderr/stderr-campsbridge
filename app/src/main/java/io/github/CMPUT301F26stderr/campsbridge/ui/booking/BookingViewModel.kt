package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.bookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.validateStaySelection
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Owns one winner's draft, current approvals and guarded reservation/recovery commands.
 * @param entrantId Actual account whose own win is required.
 * @param lotteryId Saved lottery identity from navigation.
 * @param campsiteId Saved offered campsite identity from navigation.
 * @param lotteries Parent, own membership and allocation sources.
 * @param campsites Current site and half-open inventory sources.
 * @param bookings Atomic reservations and server-confirmed consuming history.
 * @param images Independent current campsite attachment/display source.
 * @param clock Explicit time for booking windows and campsite-local today.
 * @param savedState Primitive draft, uncertainty marker and known consuming identity.
 * @param availableNights Reserved F15; null explicitly leaves date eligibility pending.
 */
class BookingViewModel(
    private val entrantId: String,
    private val lotteryId: String,
    private val campsiteId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val bookings: BookingRepository,
    private val images: ImageRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val availableNights: ((Set<LocalDate>, List<CalendarNight>) -> Set<LocalDate>)? = null,
) : ViewModel() {
    val state = MutableStateFlow<BookingUiState>(BookingUiState.Loading).asStateFlow()
    val discardPrompt = MutableStateFlow(false).asStateFlow()
    val exitRequested = MutableStateFlow(false).asStateFlow()
    val completion = MutableStateFlow<String?>(null).asStateFlow()
    val submission = MutableStateFlow<BookingSubmission>(BookingSubmission.Idle).asStateFlow()
    val photoState = MutableStateFlow(ImageSlotState()).asStateFlow()

    /** Retries only a failed attachment or URL read, without changing the booking draft. */
    fun retryPhoto() {}

    /** Revalidates a reviewed stay and starts one guarded reservation attempt. */
    fun confirm() {}

    /** Retries only read-only confirmation of an unresolved attempt, without another reserve. */
    fun retryRecovery() {}

    /** Reports whether a current terms/review page may open its packaged policy child. */
    fun canReadPolicy(): Boolean =
        false

    /** Validates the displayed step and enters the next step or returns to review.
     * @param displayedStep Step whose footer was actually displayed; rejects queued old actions.
     */
    fun next(displayedStep: BookingStep) {}

    /** Moves within this wizard or requests explicit abandonment from its first step. */
    fun back() {}

    /** Opens one review edit without constructing a second working draft.
     * @param step Actual editable step to revisit.
     */
    fun editStep(step: BookingStep) {}

    /** Accepts only the exact displayed terms; stale callbacks cannot acknowledge newer rules.
     * @param accepted Whether the checkbox was checked or cleared.
     * @param rules Rules actually displayed by the caller.
     * @param policyVersion Policy version actually displayed by the caller.
     */
    fun acceptTerms(
        accepted: Boolean,
        rules: String,
        policyVersion: String,
    ) {}

    /** Approves only a currently displayed exact amount after a quote change.
     * @param totalCents Amount actually displayed by the caller.
     */
    fun acknowledgeQuote(totalCents: Long) {}

    /** Requests abandonment independently of current source success or failure. */
    fun requestExit() {}

    /** Closes an unsubmitted discard prompt and preserves all input. */
    fun dismissDiscard() {}

    /** Requests route navigation after explicit abandonment; performs no persistence write. */
    fun confirmDiscard() {}

    /** Consumes an exit only after the active route actually navigates. */
    fun acknowledgeExit() {}

    /** Consumes a saved-target handoff only after the active route actually navigates. */
    fun acknowledgeCompletion() {}

    /** Requests another explicit opening of already-known consuming history without writing. */
    fun requestReservationOpen() {}

    /** Records only whether the real dates page is visible.
     * @param visible Whether its monthly calendar is displayed.
     */
    fun setDatesVisible(visible: Boolean) {}

    /** Changes the retained month without changing selected endpoints.
     * @param month Requested displayed month.
     */
    fun changeMonth(month: YearMonth) {}

    /** Replaces arrival and clears a previous departure only when the arrival changes.
     * @param date Inclusive selected arrival, or null to clear dates.
     */
    fun selectCheckIn(date: LocalDate?) {}

    /** Retains departure for ordered feedback without constructing a domain request.
     * @param date Exclusive selected departure, or null to clear it.
     */
    fun selectCheckOut(date: LocalDate?) {}

    /** Applies only a currently selectable displayed cell as arrival or excluded checkout.
     * @param date Local date actually tapped in the visible calendar.
     */
    fun selectDate(date: LocalDate) {}

    /** Clears a partial/complete/invalid selection only from its owning dates step. */
    fun clearDates() {}

    /** Keeps raw guest text rather than silently coercing incomplete input.
     * @param text Editable count text.
     */
    fun editGuests(text: String) {}

    /** Retries a failed/interrupted source without duplicating an active read.
     * @param source Exact failed or cancelled source.
     */
    fun retry(source: BookingReadSource) {}

    /** Retries only failed displayed-month inventory. */
    fun retryVisibleInventory() {}

    /** Retries only failed selected-range inventory. */
    fun retrySelectedInventory() {}

    /** Rechecks windows/local today and unused consumption after foreground return. */
    fun refreshTime() {}
}

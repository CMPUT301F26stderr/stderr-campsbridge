package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.bookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.validateStaySelection
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
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

/** Owns one winner's source-backed wizard draft and explicit terms/quote approvals; no writes.
 * @param entrantId Actual account whose own win is required.
 * @param lotteryId Saved lottery identity from navigation.
 * @param campsiteId Saved offered campsite identity from navigation.
 * @param lotteries Parent, own membership and allocation sources.
 * @param campsites Current site and half-open inventory sources.
 * @param bookings Server-confirmed consuming reservation lookup.
 * @param clock Explicit time for booking windows and campsite-local today.
 * @param savedState Primitive unsubmitted dates/month/guest input and known consumption identity.
 * @param availableNights Reserved F15; null explicitly leaves date eligibility pending.
 */
class BookingViewModel(
    private val entrantId: String,
    private val lotteryId: String,
    private val campsiteId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val bookings: BookingRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val availableNights: ((Set<LocalDate>, List<CalendarNight>) -> Set<LocalDate>)? = null,
) : ViewModel() {
    private var draft =
        BookingDraft(
            checkIn = restoredDate("bookingCheckIn"),
            checkOut = restoredDate("bookingCheckOut"),
            guestInput = savedState.get<String>("bookingGuests") ?: "1",
            step =
                BookingStep.entries.firstOrNull { it.name == savedState.get<String>("bookingStep") }
                    ?: BookingStep.DATES,
            returnToReview = savedState.get<Boolean>("bookingReviewEdit") ?: false,
            agreement = restoredAgreement(),
            reviewedTotalCents = savedState.get<Long>("bookingReviewedTotal")?.takeIf { it >= 0 },
        )

    // Distinguishes first presentation from returning after a date/price invalidated approval.
    private var hasReviewed =
        savedState.get<Boolean>("bookingHasReviewed")
            ?: (
                draft.step == BookingStep.REVIEW || draft.returnToReview ||
                    draft.reviewedTotalCents != null
            )
    private var month =
        savedState.get<String>("bookingMonth")?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        }
    private var datesVisible = false
    private var knownReservationId =
        savedState
            .get<String>("bookingReservationId")
            ?.takeIf { it.isNotBlank() }
    private var closed = false
    private var lottery: Lottery? = null
    private var ownEntry: LotteryEntry? = null
    private var offers = emptyList<LotteryOffer>()
    private var campsite: Campsite? = null
    private val loaded = mutableSetOf<BookingReadSource>()
    private val failed = mutableSetOf<BookingReadSource>()
    private val jobs = mutableMapOf<BookingReadSource, Job>()
    private val versions = mutableMapOf<BookingReadSource, Long>()
    private var opportunityJob: Job? = null
    private var opportunityVersion = 0L
    private var opportunityAttempted = false
    private var opportunityInFlight = false
    private var opportunityUnused = false
    private var opportunityFailed = false
    private var timeJob: Job? = null
    private var timeDeadline: Instant? = null
    private var error: BookingStepError? = null
    private val calendar = BookingCalendarReader(campsiteId, campsites, viewModelScope)
    private val mutableState = MutableStateFlow<BookingUiState>(BookingUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableDiscardPrompt = MutableStateFlow(false)
    val discardPrompt = mutableDiscardPrompt.asStateFlow()
    private val mutableExitRequested = MutableStateFlow(false)
    val exitRequested = mutableExitRequested.asStateFlow()

    private val calendarJob =
        viewModelScope.launch(start = CoroutineStart.LAZY) {
            calendar.state.collect { render() }
        }

    init {
        calendarJob.start()
        if (knownReservationId == null) {
            observeSource(BookingReadSource.LOTTERY, { lotteries.observe(lotteryId) }) {
                lottery =
                    it
            }
            observeSource(
                BookingReadSource.MEMBERSHIP,
                { lotteries.observeForEntrant(entrantId) },
            ) { rows ->
                ownEntry =
                    rows.firstOrNull { it.accountId == entrantId && it.lotteryId == lotteryId }
            }
            observeSource(BookingReadSource.OFFERS, { lotteries.observeOffers(lotteryId) }) {
                offers =
                    it
            }
            observeSource(BookingReadSource.CAMPSITE, { campsites.observe(campsiteId) }) {
                campsite =
                    it
            }
        } else {
            render()
        }
    }

    /** Validates the current step and enters the next step or returns to review. */
    fun next() {
        if (!canEdit()) return
        reconcile()
        val current = state.value as? BookingUiState.Content ?: return
        if (draft.step == BookingStep.REVIEW) return
        val invalid = stepError(current, draft.step)
        if (invalid != null) {
            error = invalid
            render()
            return
        }
        error = null
        if (draft.returnToReview || draft.step == BookingStep.TERMS) {
            enterReview(current)
        } else {
            draft = draft.copy(step = BookingStep.entries[draft.step.ordinal + 1])
            saveDraft()
            reconcile()
        }
    }

    /** Moves within this wizard or requests explicit abandonment from its first step. */
    fun back() {
        if (closed || mutableDiscardPrompt.value || mutableExitRequested.value) return
        if (state.value !is BookingUiState.Content || draft.step == BookingStep.DATES) {
            requestExit()
            return
        }
        draft =
            draft.copy(
                step =
                    if (draft.returnToReview) {
                        BookingStep.REVIEW
                    } else {
                        BookingStep.entries[draft.step.ordinal - 1]
                    },
                returnToReview = false,
            )
        error = null
        saveDraft()
        reconcile()
    }

    /** Opens one review edit without constructing a second working draft.
     * @param step Actual editable step to revisit.
     */
    fun editStep(step: BookingStep) {
        if (!canEdit() || draft.step != BookingStep.REVIEW || step == BookingStep.REVIEW) return
        draft = draft.copy(step = step, returnToReview = true)
        error = null
        saveDraft()
        reconcile()
    }

    /** Accepts only the exact displayed terms; stale callbacks cannot acknowledge newer rules.
     * @param accepted Whether the checkbox was checked or cleared.
     * @param rules Rules actually displayed by the caller.
     * @param policyVersion Policy version actually displayed by the caller.
     */
    fun acceptTerms(
        accepted: Boolean,
        rules: String,
        policyVersion: String,
    ) {
        if (!canEdit() || draft.step != BookingStep.TERMS) return
        val current = state.value as BookingUiState.Content
        if (accepted &&
            (rules != current.campsite.details.rules || policyVersion != BookingPolicy.VERSION)
        ) {
            error = BookingStepError.AgreementRequired
            render()
            return
        }
        draft =
            draft.copy(agreement = if (accepted) BookingAgreement(rules, policyVersion) else null)
        error = null
        saveDraft()
        render()
    }

    /** Approves only a currently displayed exact amount after a quote change.
     * @param totalCents Amount actually displayed by the caller.
     */
    fun acknowledgeQuote(totalCents: Long) {
        if (!canEdit() || draft.step != BookingStep.REVIEW) return
        val quote = (state.value as BookingUiState.Content).quote as? BookingQuote.Value ?: return
        if (quote.totalCents != totalCents) return
        draft = draft.copy(reviewedTotalCents = totalCents)
        error = null
        saveDraft()
        render()
    }

    /** Requests abandonment independently of current source success or failure. */
    fun requestExit() {
        if (closed || mutableExitRequested.value || mutableDiscardPrompt.value) return
        val dirty =
            draft.checkIn != null || draft.checkOut != null ||
                draft.guestInput != "1" || draft.agreement != null
        if (dirty) mutableDiscardPrompt.value = true else mutableExitRequested.value = true
    }

    /** Closes an unsubmitted discard prompt and preserves all input. */
    fun dismissDiscard() {
        if (!closed && !mutableExitRequested.value) mutableDiscardPrompt.value = false
    }

    /** Requests route navigation after explicit abandonment; performs no persistence write. */
    fun confirmDiscard() {
        if (closed || !mutableDiscardPrompt.value) return
        mutableDiscardPrompt.value = false
        mutableExitRequested.value = true
    }

    /** Consumes an exit only after the active route actually navigates. */
    fun acknowledgeExit() {
        if (!closed) mutableExitRequested.value = false
    }

    /** Rejects stale callbacks from another step or while prompt/navigation owns control.
     * @param step Optional field-owning step; null checks shared wizard actions.
     */
    private fun canEdit(step: BookingStep? = null): Boolean =
        !closed && !mutableDiscardPrompt.value &&
            !mutableExitRequested.value && state.value is BookingUiState.Content &&
            (step == null || draft.step == step)

    /** Validates the requested step using current source-backed facts and existing parsers.
     * @param current Confirmed editable opportunity at this action boundary.
     * @param step Step whose explicit Next was requested.
     */
    private fun stepError(
        current: BookingUiState.Content,
        step: BookingStep,
    ): BookingStepError? =
        when (step) {
            BookingStep.DATES -> {
                when {
                    current.stayValidation != null &&
                        current.stayValidation != StaySelectionInput.VALID -> {
                        BookingStepError.Dates(current.stayValidation)
                    }

                    current.eligibilityPending -> {
                        BookingStepError.EligibilityPending
                    }

                    current.stayValidation == null -> {
                        BookingStepError.InventoryUnknown
                    }

                    else -> {
                        null
                    }
                }
            }

            BookingStep.GUESTS -> {
                (
                    parsePositiveCount(
                        draft.guestInput,
                        current.campsite.details.maxGuests,
                    ) as? PositiveCountInput.Error
                )?.let { BookingStepError.Guests(it) }
            }

            BookingStep.TERMS -> {
                if (draft.agreement ==
                    BookingAgreement(current.campsite.details.rules, BookingPolicy.VERSION)
                ) {
                    null
                } else {
                    BookingStepError.AgreementRequired
                }
            }

            BookingStep.REVIEW -> {
                null
            }
        }

    /** Rechecks all form facts before retaining the amount about to be displayed on review.
     * @param current Current confirmed opportunity, inventory and checked quote.
     */
    private fun enterReview(current: BookingUiState.Content) {
        for (step in listOf(BookingStep.DATES, BookingStep.GUESTS, BookingStep.TERMS)) {
            val invalid = stepError(current, step) ?: continue
            draft = draft.copy(step = step)
            error = invalid
            saveDraft()
            reconcile()
            return
        }
        val quote = current.quote as? BookingQuote.Value
        if (quote == null) {
            error = BookingStepError.QuoteOverflow
            render()
            return
        }
        draft =
            draft.copy(
                step = BookingStep.REVIEW,
                returnToReview = false,
                reviewedTotalCents =
                    if (hasReviewed) draft.reviewedTotalCents else quote.totalCents,
            )
        hasReviewed = true
        saveDraft()
        reconcile()
    }

    /** Records only whether the real dates page is visible.
     * @param visible Whether its monthly calendar is displayed.
     */
    fun setDatesVisible(visible: Boolean) {
        if (closed) return
        datesVisible = visible
        reconcile()
    }

    /** Changes the retained month without changing selected endpoints.
     * @param month Requested displayed month.
     */
    fun changeMonth(month: YearMonth) {
        if (!canEdit(BookingStep.DATES)) return
        this.month = month
        savedState["bookingMonth"] = month.toString()
        reconcile()
    }

    /** Replaces arrival and clears a previous departure only when the arrival changes.
     * @param date Inclusive selected arrival, or null to clear dates.
     */
    fun selectCheckIn(date: LocalDate?) {
        if (!canEdit(BookingStep.DATES) || date == draft.checkIn) return
        draft = draft.copy(checkIn = date, checkOut = null, reviewedTotalCents = null)
        error = null
        saveDraft()
        reconcile()
    }

    /** Retains departure for ordered feedback without constructing a domain request.
     * @param date Exclusive selected departure, or null to clear it.
     */
    fun selectCheckOut(date: LocalDate?) {
        if (!canEdit(BookingStep.DATES)) return
        draft = draft.copy(checkOut = date, reviewedTotalCents = null)
        error = null
        saveDraft()
        reconcile()
    }

    /** Applies only a currently selectable displayed cell as arrival or excluded checkout.
     * @param date Local date actually tapped in the visible calendar.
     */
    fun selectDate(date: LocalDate) {
        if (!canEdit(BookingStep.DATES)) return
        val current = state.value as BookingUiState.Content
        val displayed = bookingCalendarState(current)
        if (!displayed.selectionEnabled || displayed.days[date]?.isSelectable != true) return
        val start = draft.checkIn
        if (start == null || draft.checkOut != null || start < current.today || date <= start) {
            if (date == start) selectCheckOut(null) else selectCheckIn(date)
        } else {
            selectCheckOut(date)
        }
    }

    /** Clears a partial/complete/invalid selection only from its owning dates step. */
    fun clearDates() {
        if (!canEdit(BookingStep.DATES)) return
        draft = draft.copy(checkIn = null, checkOut = null, reviewedTotalCents = null)
        error = null
        saveDraft()
        reconcile()
    }

    /** Keeps raw guest text rather than silently coercing incomplete input.
     * @param text Editable count text.
     */
    fun editGuests(text: String) {
        if (!canEdit(BookingStep.GUESTS)) return
        draft = draft.copy(guestInput = text)
        error = null
        saveDraft()
        render()
    }

    /** Retries a failed/interrupted source without duplicating an active read.
     * @param source Exact failed or cancelled source.
     */
    fun retry(source: BookingReadSource) {
        if (closed || knownReservationId != null) return
        if (source == BookingReadSource.OPPORTUNITY) {
            if (!opportunityInFlight && !opportunityUnused && hasOwnDrawnWin()) loadOpportunity()
            return
        }
        if (source !in failed && !sourceWasInterrupted(source)) return
        when (source) {
            BookingReadSource.LOTTERY -> {
                observeSource(source, { lotteries.observe(lotteryId) }) { lottery = it }
            }

            BookingReadSource.MEMBERSHIP -> {
                observeSource(source, { lotteries.observeForEntrant(entrantId) }) { rows ->
                    ownEntry =
                        rows.firstOrNull { it.accountId == entrantId && it.lotteryId == lotteryId }
                }
            }

            BookingReadSource.OFFERS -> {
                observeSource(source, { lotteries.observeOffers(lotteryId) }) { offers = it }
            }

            BookingReadSource.CAMPSITE -> {
                observeSource(source, { campsites.observe(campsiteId) }) { campsite = it }
            }

            BookingReadSource.OPPORTUNITY -> {
                Unit
            }
        }
    }

    /** Retries only failed displayed-month inventory. */
    fun retryVisibleInventory() {
        if (!closed) calendar.retryVisible()
    }

    /** Retries only failed selected-range inventory. */
    fun retrySelectedInventory() {
        if (!closed) calendar.retrySelection()
    }

    /** Rechecks windows/local today and unused consumption after foreground return. */
    fun refreshTime() {
        if (closed || knownReservationId != null) return
        jobs.keys
            .toList()
            .filter(::sourceWasInterrupted)
            .forEach(::retry)
        reconcile()
        if (!opportunityInFlight && !opportunityFailed && hasOwnDrawnWin()) loadOpportunity()
    }

    /** Recognizes a cancelled unknown source; failed reads keep their explicit failure action.
     * @param source Fixed observation slot to inspect.
     */
    private fun sourceWasInterrupted(source: BookingReadSource): Boolean =
        source !in loaded && source !in failed && jobs[source]?.isCompleted == true

    /** Owns one fixed source with consistent lazy installation and late-callback rejection.
     * @param source Independent read slot; OPPORTUNITY uses its explicit server lookup instead.
     * @param observe Creates the repository observation only when the installed job starts.
     * @param accept Retains only the current source's actual typed facts.
     */
    private fun <T> observeSource(
        source: BookingReadSource,
        observe: () -> Flow<T>,
        accept: (T) -> Unit,
    ) {
        if (closed || knownReservationId != null) return
        val version = (versions[source] ?: 0) + 1
        versions[source] = version
        jobs.remove(source)?.cancel()
        loaded.remove(source)
        failed.remove(source)
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    observe().collect { value ->
                        currentCoroutineContext().ensureActive()
                        if (versions[source] != version) return@collect
                        accept(value)
                        loaded.add(source)
                        failed.remove(source)
                        reconcile()
                    }
                } catch (cancelled: CancellationException) {
                    if (!closed && versions[source] == version) {
                        loaded.remove(source)
                        reconcile()
                    }
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (versions[source] == version) {
                        loaded.remove(source)
                        failed.add(source)
                        reconcile()
                    }
                }
            }
        jobs[source] = pending
        reconcile()
        pending.start()
    }

    /** Uses only completed parent and exact own membership facts to authorize consumption reads. */
    private fun hasOwnDrawnWin(): Boolean =
        BookingReadSource.LOTTERY in loaded && BookingReadSource.MEMBERSHIP in loaded &&
            lottery?.let { it.id == lotteryId && it.publishedAt != null && it.drawnAt != null } ==
            true &&
            ownEntry?.state == EntryState.OFFERED

    /** Reconciles dependent reads and projects current facts without starting writes. */
    private fun reconcile() {
        if (closed) return
        if (month == null && BookingReadSource.CAMPSITE in loaded) {
            campsite?.takeIf { it.id == campsiteId }?.let { site ->
                month = YearMonth.from(draft.checkIn ?: localToday(site))
                savedState["bookingMonth"] = month.toString()
            }
        }
        if (knownReservationId == null) {
            if (!hasOwnDrawnWin()) {
                stopOpportunity()
            } else if (!opportunityAttempted) {
                loadOpportunity()
            }
        }
        reconcileApproval()
        syncCalendar()
        render()
    }

    /** Invalidates accepted/reviewed snapshots only against confirmed current site facts. */
    private fun reconcileApproval() {
        val site =
            campsite?.takeIf { BookingReadSource.CAMPSITE in loaded && it.id == campsiteId }
                ?: return
        val agreement =
            draft.agreement?.takeIf {
                it.rules == site.details.rules && it.policyVersion == BookingPolicy.VERSION
            }
        val start = draft.checkIn
        val end = draft.checkOut
        val quote =
            if (start != null && end != null && end > start) {
                bookingQuote(start, end, site.details.nightlyPriceCents) as? BookingQuote.Value
            } else {
                null
            }
        val reviewed = draft.reviewedTotalCents?.takeIf { it == quote?.totalCents }
        if (agreement != draft.agreement || reviewed != draft.reviewedTotalCents) {
            draft = draft.copy(agreement = agreement, reviewedTotalCents = reviewed)
            saveDraft()
        }
    }

    /** Server-confirms consuming history before edits; UI never derives the stored key. */
    private fun loadOpportunity() {
        if (closed || opportunityInFlight || knownReservationId != null || !hasOwnDrawnWin()) return
        val version = ++opportunityVersion
        opportunityJob?.cancel()
        opportunityAttempted = true
        opportunityInFlight = true
        opportunityUnused = false
        opportunityFailed = false
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val reservation = bookings.getForOpportunity(lotteryId, entrantId)
                    currentCoroutineContext().ensureActive()
                    if (version != opportunityVersion) return@launch
                    check(
                        reservation == null || (
                            reservation.id.isNotBlank() &&
                                reservation.lotteryId == lotteryId &&
                                reservation.entrantId == entrantId
                        ),
                    )
                    opportunityInFlight = false
                    opportunityUnused = reservation == null
                    if (reservation != null) {
                        knownReservationId = reservation.id
                        savedState["bookingReservationId"] = reservation.id
                    }
                    reconcile()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == opportunityVersion) {
                        opportunityInFlight = false
                        opportunityFailed = true
                        reconcile()
                    }
                } finally {
                    if (version == opportunityVersion) opportunityInFlight = false
                }
            }
        opportunityJob = pending
        syncCalendar()
        render()
        pending.start()
    }

    /** Invalidates unknown/unused lookup evidence when the required own-winning facts retire. */
    private fun stopOpportunity() {
        if (!opportunityAttempted) return
        opportunityVersion++
        opportunityJob?.cancel()
        opportunityJob = null
        opportunityAttempted = false
        opportunityInFlight = false
        opportunityUnused = false
        opportunityFailed = false
    }

    /** Supplies exactly the visible month and chosen range while current facts permit editing. */
    private fun syncCalendar() {
        val editable = readOutcome() as? BookingUiState.Content
        if (editable == null) {
            calendar.stop()
        } else {
            calendar.update(
                month.takeIf { datesVisible && draft.step == BookingStep.DATES },
                draft.checkIn,
                draft.checkOut,
            )
        }
    }

    /** Resolves source, identity, consumption and window prerequisites before date-dependent UI. */
    private fun readOutcome(): BookingUiState {
        knownReservationId?.let { return BookingUiState.Reserved(it) }
        failed.firstOrNull()?.let { return BookingUiState.Failure(it) }
        if (!loaded.containsAll(
                listOf(
                    BookingReadSource.LOTTERY,
                    BookingReadSource.MEMBERSHIP,
                    BookingReadSource.OFFERS,
                    BookingReadSource.CAMPSITE,
                ),
            )
        ) {
            return BookingUiState.Loading
        }
        val draw = lottery
        if (draw == null || draw.id != lotteryId || draw.publishedAt == null) {
            return BookingUiState.Unavailable(BookingUnavailableReason.LOTTERY_UNAVAILABLE)
        }
        if (draw.drawnAt ==
            null
        ) {
            return BookingUiState.Unavailable(BookingUnavailableReason.DRAW_PENDING)
        }
        if (ownEntry?.state != EntryState.OFFERED) {
            return BookingUiState.Unavailable(BookingUnavailableReason.NOT_A_WINNER)
        }
        if (opportunityFailed) return BookingUiState.Failure(BookingReadSource.OPPORTUNITY)
        if (!opportunityUnused) return BookingUiState.Loading
        if (draw.cancellation != null) {
            return BookingUiState.Unavailable(BookingUnavailableReason.LOTTERY_CANCELLED)
        }
        val site = campsite
        if (site == null || site.id != campsiteId || !site.isPublished ||
            site.ownerId != draw.ownerId
        ) {
            return BookingUiState.Unavailable(BookingUnavailableReason.CAMPSITE_UNAVAILABLE)
        }
        val offer =
            offers.firstOrNull { it.campsiteId == campsiteId }
                ?: return BookingUiState.Unavailable(BookingUnavailableReason.OFFER_UNAVAILABLE)
        val now = clock.instant()
        if (now < draw.configuration.bookingOpensAt) {
            return BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_NOT_OPEN)
        }
        if (now >= draw.configuration.bookingClosesAt) {
            return BookingUiState.Unavailable(BookingUnavailableReason.BOOKING_CLOSED)
        }
        return BookingUiState.Content(
            draw,
            site,
            offer,
            draft,
            checkNotNull(month),
            localToday(site),
        )
    }

    /** Projects inventory/F15/date/quote results without mutating or resubscribing the child. */
    private fun render() {
        if (closed) return
        val outcome = readOutcome()
        mutableState.value =
            if (outcome is BookingUiState.Content) {
                val read = calendar.state.value
                val offered = outcome.offer.nights.toSet()
                val visible =
                    (read.visible as? CalendarInventoryRead.Content)?.let { inventory ->
                        availableNights?.invoke(offered, inventory.nights)
                    }
                val selected =
                    (read.selected as? CalendarInventoryRead.Content)?.let { inventory ->
                        if (inventory == read.visible) {
                            visible
                        } else {
                            availableNights?.invoke(offered, inventory.nights)
                        }
                    }
                val start = draft.checkIn
                val end = draft.checkOut
                val preliminary = validateStaySelection(start, end, emptySet(), outcome.today)
                val validation =
                    if (preliminary != StaySelectionInput.UNAVAILABLE_NIGHTS) {
                        preliminary
                    } else {
                        selected?.let { eligible ->
                            validateStaySelection(start, end, eligible, outcome.today)
                        }
                    }
                val quote =
                    if (start != null && end != null && end > start) {
                        bookingQuote(start, end, outcome.campsite.details.nightlyPriceCents)
                    } else {
                        null
                    }
                outcome.copy(
                    calendar = read,
                    eligibilityPending = availableNights == null,
                    visibleEligibleNights = visible,
                    selectedEligibleNights = selected,
                    stayValidation = validation,
                    quote = quote,
                    error = error,
                )
            } else {
                outcome
            }
        scheduleTime()
    }

    /** Uses the confirmed site's timezone instead of the device default.
     * @param site Current site whose civil dates are displayed.
     */
    private fun localToday(site: Campsite): LocalDate =
        clock.instant().atZone(ZoneId.of(site.details.timeZone)).toLocalDate()

    /** Waits for only the nearest booking boundary or campsite-local rollover. */
    private fun scheduleTime() {
        val now = clock.instant()
        val draw = lottery.takeIf { BookingReadSource.LOTTERY in loaded }
        val site = campsite.takeIf { BookingReadSource.CAMPSITE in loaded }
        val deadlines = mutableListOf<Instant>()
        if (knownReservationId == null && draw != null) {
            deadlines.add(draw.configuration.bookingOpensAt)
            deadlines.add(draw.configuration.bookingClosesAt)
            site?.let {
                deadlines.add(
                    localToday(
                        it,
                    ).plusDays(1).atStartOfDay(ZoneId.of(it.details.timeZone)).toInstant(),
                )
            }
        }
        val deadline = deadlines.filter { it > now }.minOrNull()
        if (deadline == timeDeadline && timeJob?.isActive == true) return
        timeJob?.cancel()
        timeJob = null
        timeDeadline = deadline
        if (deadline == null) return
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                timeJob = null
                timeDeadline = null
                reconcile()
            }
        timeJob = pending
        pending.start()
    }

    /** Saves only raw date/guest primitives, independently of transient source outcomes. */
    private fun saveDraft() {
        savedState["bookingCheckIn"] = draft.checkIn?.toString()
        savedState["bookingCheckOut"] = draft.checkOut?.toString()
        savedState["bookingGuests"] = draft.guestInput
        savedState["bookingStep"] = draft.step.name
        savedState["bookingReviewEdit"] = draft.returnToReview
        savedState["bookingRules"] = draft.agreement?.rules
        savedState["bookingPolicy"] = draft.agreement?.policyVersion
        savedState["bookingReviewedTotal"] = draft.reviewedTotalCents
        savedState["bookingHasReviewed"] = hasReviewed
    }

    /** Restores an exact pair without accepting a partial saved agreement. */
    private fun restoredAgreement(): BookingAgreement? {
        val rules = savedState.get<String>("bookingRules") ?: return null
        val policy = savedState.get<String>("bookingPolicy") ?: return null
        return BookingAgreement(rules, policy)
    }

    /** Parses a primitive restoration value safely; malformed input does not reach a query.
     * @param key Saved local-date key.
     */
    private fun restoredDate(key: String): LocalDate? =
        savedState.get<String>(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Releases all sources and invalidates queued/noncooperative work when the graph retires. */
    override fun onCleared() {
        closed = true
        versions.keys.toList().forEach { versions[it] = checkNotNull(versions[it]) + 1 }
        jobs.values.forEach { it.cancel() }
        opportunityVersion++
        opportunityJob?.cancel()
        timeJob?.cancel()
        calendar.close()
        calendarJob.cancel()
        super.onCleared()
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.availableBookingNights
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
 * @param availableNights Confirmed availability filter; null explicitly disables date selection.
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
    private val availableNights: ((Set<LocalDate>, List<CalendarNight>) -> Set<LocalDate>)? =
        ::availableBookingNights,
) : ViewModel() {
    private val draftState = BookingDraftState(savedState)
    private var datesVisible = false
    private var knownReservationId =
        savedState
            .get<String>("bookingReservationId")
            ?.takeIf { it.isNotBlank() }
    private var recoveryRequired = savedState.get<Boolean>("bookingRecoveryRequired") == true
    private val writer = BookingWriter(bookings)
    private var submissionJob: Job? = null
    private var submissionVersion = 0L
    private var submissionSnapshot: BookingUiState.Content? = null
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
    private val calendar = BookingCalendarReader(campsiteId, campsites, viewModelScope)
    private val mutableState = MutableStateFlow<BookingUiState>(BookingUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableDiscardPrompt = MutableStateFlow(false)
    val discardPrompt = mutableDiscardPrompt.asStateFlow()
    private val mutableExitRequested = MutableStateFlow(false)
    val exitRequested = mutableExitRequested.asStateFlow()
    private val mutableCompletion =
        MutableStateFlow(
            savedState.get<String>("bookingCompletion")?.takeIf { it == knownReservationId },
        )
    val completion = mutableCompletion.asStateFlow()
    private val mutableSubmission =
        MutableStateFlow<BookingSubmission>(
            if (recoveryRequired && knownReservationId == null) {
                BookingSubmission.Recovering
            } else {
                BookingSubmission.Idle
            },
        )
    val submission = mutableSubmission.asStateFlow()
    private val mutablePhoto = MutableStateFlow(ImageSlotState())
    val photoState = mutablePhoto.asStateFlow()
    private var photo: ImageSlotController? = null
    private var photoJob: Job? = null

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
            setRecoveryRequired(false)
            render()
        }
        if (recoveryRequired && knownReservationId == null) startSubmission(null)
    }

    /** Retries only a failed attachment or URL read, without changing the booking draft. */
    fun retryPhoto() {
        photo?.retry()
    }

    /** Starts one image reader for presentable content; other outcomes release its references. */
    private fun syncPhoto() {
        if (state.value !is BookingUiState.Content) {
            stopPhoto()
        } else if (photo == null) {
            val controller =
                ImageSlotController(
                    images,
                    ImageTarget(ImageTargetType.CAMPSITE, campsiteId),
                    viewModelScope,
                )
            photo = controller
            photoJob =
                viewModelScope.launch {
                    controller.state.collect { mutablePhoto.value = it }
                }
        }
    }

    /** Releases attachment work and clears transient display references for the retired parent. */
    private fun stopPhoto() {
        photoJob?.cancel()
        photoJob = null
        photo?.close()
        photo = null
        mutablePhoto.value = ImageSlotState()
    }

    /** Revalidates a reviewed stay and starts one guarded reservation attempt. */
    fun confirm() {
        if (!canEdit(BookingStep.REVIEW) || opportunityInFlight || opportunityFailed) return
        reconcile()
        var current = state.value as? BookingUiState.Content ?: return
        draftState.error = null
        draftState.enterReview(current)
        reconcile()
        current = state.value as? BookingUiState.Content ?: return
        if (draftState.value.step != BookingStep.REVIEW) return
        val quote = current.quote as? BookingQuote.Value ?: return
        if (draftState.value.reviewedTotalCents != quote.totalCents) return
        val guests =
            parsePositiveCount(draftState.value.guestInput, current.campsite.details.maxGuests)
                as? PositiveCountInput.Value ?: return
        val agreement = draftState.value.agreement ?: return
        val request =
            BookingRequest(
                lotteryId,
                entrantId,
                campsiteId,
                checkNotNull(draftState.value.checkIn),
                checkNotNull(draftState.value.checkOut),
                guests.count,
                quote.totalCents,
                agreement.rules,
                agreement.policyVersion,
            )
        submissionSnapshot = current
        startSubmission(request)
    }

    /** Retries only read-only confirmation of an unresolved attempt, without another reserve. */
    fun retryRecovery() {
        if (closed || knownReservationId != null || !recoveryRequired ||
            mutableDiscardPrompt.value || mutableExitRequested.value ||
            mutableSubmission.value != BookingSubmission.Unknown
        ) {
            return
        }
        startSubmission(null)
    }

    /** Reports whether a current terms/review page may open its packaged policy child. */
    fun canReadPolicy(): Boolean =
        canEdit() && draftState.value.step in setOf(BookingStep.TERMS, BookingStep.REVIEW)

    /** Installs one command before suspension; null requests only resolve prior uncertainty.
     * @param request Frozen reviewed stay, or null for identity-only recovery.
     */
    private fun startSubmission(request: BookingRequest?) {
        if (closed || knownReservationId != null || submissionJob?.isActive == true) return
        val version = ++submissionVersion
        mutableSubmission.value =
            if (request == null) {
                BookingSubmission.Recovering
            } else {
                BookingSubmission.Submitting
            }
        setRecoveryRequired(true)
        syncCalendar()
        render()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result =
                        if (request == null) {
                            writer.recover(lotteryId, entrantId)
                        } else {
                            writer.submit(request) {
                                if (!closed && submissionVersion == version) {
                                    mutableSubmission.value = BookingSubmission.Recovering
                                }
                            }
                        }
                    currentCoroutineContext().ensureActive()
                    if (!closed && submissionVersion == version) finishSubmission(result)
                } catch (cancelled: CancellationException) {
                    if (!closed && submissionVersion == version && recoveryRequired) {
                        mutableSubmission.value = BookingSubmission.Unknown
                    }
                    throw cancelled
                }
            }
        submissionJob = pending
        pending.start()
    }

    /** Applies confirmed outcomes; unknown results keep the same locked attempt.
     * @param result One acknowledged/rejected or read-only recovery result.
     */
    private fun finishSubmission(result: BookingWriteResult) {
        when (result) {
            is BookingWriteResult.Saved -> {
                rememberReservation(result.id)
            }

            BookingWriteResult.Unknown -> {
                mutableSubmission.value = BookingSubmission.Unknown
            }

            BookingWriteResult.Absent -> {
                setRecoveryRequired(false)
                submissionSnapshot = null
                mutableSubmission.value = BookingSubmission.Absent
                opportunityAttempted = true
                opportunityUnused = true
                opportunityFailed = false
                calendar.stop()
                reconcile()
            }

            is BookingWriteResult.Rejected -> {
                setRecoveryRequired(false)
                submissionSnapshot = null
                mutableSubmission.value = BookingSubmission.Rejected(result.reason)
                draftState.error = null
                draftState.value =
                    when (result.reason) {
                        PersistenceFailure.Reason.CONFLICT -> {
                            draftState.value.copy(
                                step = BookingStep.DATES,
                                returnToReview = true,
                            )
                        }

                        PersistenceFailure.Reason.CAPACITY_REACHED -> {
                            draftState.value.copy(
                                step = BookingStep.GUESTS,
                                returnToReview = true,
                            )
                        }

                        PersistenceFailure.Reason.STALE_QUOTE -> {
                            draftState.value.copy(
                                step = BookingStep.TERMS,
                                returnToReview = true,
                                agreement = null,
                                reviewedTotalCents = null,
                            )
                        }

                        else -> {
                            draftState.value
                        }
                    }
                draftState.save()
                calendar.stop()
                reconcile()
            }
        }
    }

    /** Retains a primitive marker; clearing it requires an acknowledged or known outcome.
     * @param required Whether a previous mutation still needs server confirmation.
     */
    private fun setRecoveryRequired(required: Boolean) {
        recoveryRequired = required
        if (required) {
            savedState["bookingRecoveryRequired"] = true
        } else {
            savedState.remove<Boolean>("bookingRecoveryRequired")
        }
    }

    /** Blocks conflicting input independently of failed or unavailable current sources. */
    private fun submissionBlocksEditing(): Boolean =
        when (mutableSubmission.value) {
            BookingSubmission.Submitting,
            BookingSubmission.Recovering,
            BookingSubmission.Unknown,
            -> true

            else -> false
        }

    /** Only an active save or recovery read temporarily holds navigation. */
    private fun submissionBlocksExit(): Boolean =
        mutableSubmission.value == BookingSubmission.Submitting ||
            mutableSubmission.value == BookingSubmission.Recovering

    /** Saves an explicit edit and clears feedback about the previous draft only when it changes.
     * @param updated New user-entered values or approvals.
     */
    private fun updateDraft(updated: BookingDraft) {
        if (updated != draftState.value &&
            (
                mutableSubmission.value is BookingSubmission.Rejected ||
                    mutableSubmission.value == BookingSubmission.Absent
            )
        ) {
            mutableSubmission.value = BookingSubmission.Idle
        }
        draftState.value = updated
        draftState.error = null
        draftState.save()
        reconcile()
    }

    /** Validates the displayed step and enters the next step or returns to review.
     * @param displayedStep Step whose footer was actually displayed; rejects queued old actions.
     */
    fun next(displayedStep: BookingStep) {
        if (!canEdit() || displayedStep != draftState.value.step) return
        reconcile()
        val current = state.value as? BookingUiState.Content ?: return
        if (draftState.value.step == BookingStep.REVIEW) return
        val invalid = draftState.stepError(current, draftState.value.step)
        if (invalid != null) {
            draftState.error = invalid
            render()
            return
        }
        draftState.error = null
        if (draftState.value.returnToReview || draftState.value.step == BookingStep.TERMS) {
            draftState.enterReview(current)
            reconcile()
        } else {
            draftState.value =
                draftState.value.copy(step = BookingStep.entries[draftState.value.step.ordinal + 1])
            draftState.save()
            reconcile()
        }
    }

    /** Moves within this wizard or requests explicit abandonment from its first step. */
    fun back() {
        if (closed || submissionBlocksExit() || mutableDiscardPrompt.value ||
            mutableExitRequested.value
        ) {
            return
        }
        if (mutableSubmission.value == BookingSubmission.Unknown ||
            state.value !is BookingUiState.Content || draftState.value.step == BookingStep.DATES
        ) {
            requestExit()
            return
        }
        draftState.value =
            draftState.value.copy(
                step =
                    if (draftState.value.returnToReview) {
                        BookingStep.REVIEW
                    } else {
                        BookingStep.entries[draftState.value.step.ordinal - 1]
                    },
                returnToReview = false,
            )
        draftState.error = null
        draftState.save()
        reconcile()
    }

    /** Opens one review edit without constructing a second working draft.
     * @param step Actual editable step to revisit.
     */
    fun editStep(step: BookingStep) {
        if (!canEdit() || draftState.value.step != BookingStep.REVIEW ||
            step == BookingStep.REVIEW
        ) {
            return
        }
        draftState.value = draftState.value.copy(step = step, returnToReview = true)
        draftState.error = null
        draftState.save()
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
        if (!canEdit() || draftState.value.step != BookingStep.TERMS) return
        val current = state.value as BookingUiState.Content
        if (accepted &&
            (rules != current.campsite.details.rules || policyVersion != BookingPolicy.VERSION)
        ) {
            draftState.error = BookingStepError.AgreementRequired
            render()
            return
        }
        updateDraft(
            draftState.value.copy(
                agreement = if (accepted) BookingAgreement(rules, policyVersion) else null,
            ),
        )
    }

    /** Approves only a currently displayed exact amount after a quote change.
     * @param totalCents Amount actually displayed by the caller.
     */
    fun acknowledgeQuote(totalCents: Long) {
        if (!canEdit() || draftState.value.step != BookingStep.REVIEW) return
        val quote = (state.value as BookingUiState.Content).quote as? BookingQuote.Value ?: return
        if (quote.totalCents != totalCents) return
        updateDraft(draftState.value.copy(reviewedTotalCents = totalCents))
    }

    /** Requests abandonment independently of current source success or failure. */
    fun requestExit() {
        if (closed || submissionBlocksExit() || mutableExitRequested.value ||
            mutableDiscardPrompt.value
        ) {
            return
        }
        val dirty =
            draftState.value.checkIn != null || draftState.value.checkOut != null ||
                draftState.value.guestInput != "1" || draftState.value.agreement != null
        if (dirty) mutableDiscardPrompt.value = true else mutableExitRequested.value = true
    }

    /** Closes an unsubmitted discard prompt and preserves all input. */
    fun dismissDiscard() {
        if (!closed && !mutableExitRequested.value) mutableDiscardPrompt.value = false
    }

    /** Requests route navigation after explicit abandonment; performs no persistence write. */
    fun confirmDiscard() {
        if (closed || submissionBlocksExit() || !mutableDiscardPrompt.value) return
        mutableDiscardPrompt.value = false
        mutableExitRequested.value = true
    }

    /** Consumes an exit only after the active route actually navigates. */
    fun acknowledgeExit() {
        if (!closed) mutableExitRequested.value = false
    }

    /** Consumes a saved-target handoff only after the active route actually navigates. */
    fun acknowledgeCompletion() {
        if (closed) return
        savedState.remove<String>("bookingCompletion")
        mutableCompletion.value = null
    }

    /** Requests another explicit opening of already-known consuming history without writing. */
    fun requestReservationOpen() {
        if (closed || mutableExitRequested.value || mutableDiscardPrompt.value) return
        knownReservationId?.let(::complete)
    }

    /** Retains one actual saved-target handoff independently of later source outcomes.
     * @param id Known consuming reservation identity; never a synthetic success target.
     */
    private fun complete(id: String) {
        if (closed) return
        savedState["bookingCompletion"] = id
        mutableCompletion.value = id
    }

    /** Rejects stale callbacks from another step or while prompt/navigation owns control.
     * @param step Optional field-owning step; null checks shared wizard actions.
     */
    private fun canEdit(step: BookingStep? = null): Boolean =
        !closed && !submissionBlocksEditing() && !mutableDiscardPrompt.value &&
            !mutableExitRequested.value && state.value is BookingUiState.Content &&
            (step == null || draftState.value.step == step)

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
        draftState.month = month
        savedState["bookingMonth"] = month.toString()
        reconcile()
    }

    /** Replaces arrival and clears a previous departure only when the arrival changes.
     * @param date Inclusive selected arrival, or null to clear dates.
     */
    fun selectCheckIn(date: LocalDate?) {
        if (!canEdit(BookingStep.DATES) || date == draftState.value.checkIn) return
        updateDraft(
            draftState.value.copy(checkIn = date, checkOut = null, reviewedTotalCents = null),
        )
    }

    /** Retains departure for ordered feedback without constructing a domain request.
     * @param date Exclusive selected departure, or null to clear it.
     */
    fun selectCheckOut(date: LocalDate?) {
        if (!canEdit(BookingStep.DATES)) return
        updateDraft(draftState.value.copy(checkOut = date, reviewedTotalCents = null))
    }

    /** Applies only a currently selectable displayed cell as arrival or excluded checkout.
     * @param date Local date actually tapped in the visible calendar.
     */
    fun selectDate(date: LocalDate) {
        if (!canEdit(BookingStep.DATES)) return
        val current = state.value as BookingUiState.Content
        val displayed = bookingCalendarState(current)
        if (!displayed.selectionEnabled || displayed.days[date]?.isSelectable != true) return
        val start = draftState.value.checkIn
        if (start == null || draftState.value.checkOut != null || start < current.today ||
            date <= start
        ) {
            if (date == start) selectCheckOut(null) else selectCheckIn(date)
        } else {
            selectCheckOut(date)
        }
    }

    /** Clears a partial/complete/invalid selection only from its owning dates step. */
    fun clearDates() {
        if (!canEdit(BookingStep.DATES)) return
        updateDraft(
            draftState.value.copy(checkIn = null, checkOut = null, reviewedTotalCents = null),
        )
    }

    /** Keeps raw guest text rather than silently coercing incomplete input.
     * @param text Editable count text.
     */
    fun editGuests(text: String) {
        if (!canEdit(BookingStep.GUESTS)) return
        updateDraft(draftState.value.copy(guestInput = text))
    }

    /** Retries a failed/interrupted source without duplicating an active read.
     * @param source Exact failed or cancelled source.
     */
    fun retry(source: BookingReadSource) {
        if (closed || submissionBlocksEditing() || knownReservationId != null) return
        if (source == BookingReadSource.OPPORTUNITY) {
            if (!opportunityInFlight && (!opportunityUnused || opportunityFailed) &&
                hasOwnDrawnWin()
            ) {
                loadOpportunity()
            }
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

            BookingReadSource.OPPORTUNITY -> {}
        }
    }

    /** Retries only failed displayed-month inventory. */
    fun retryVisibleInventory() {
        if (!closed && !submissionBlocksEditing()) calendar.retryVisible()
    }

    /** Retries only failed selected-range inventory. */
    fun retrySelectedInventory() {
        if (!closed && !submissionBlocksEditing()) calendar.retrySelection()
    }

    /** Rechecks windows/local today and unused consumption after foreground return. */
    fun refreshTime() {
        if (closed || knownReservationId != null) return
        if (submissionBlocksEditing()) {
            reconcile()
            return
        }
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
        if (draftState.month == null && BookingReadSource.CAMPSITE in loaded) {
            campsite?.takeIf { it.id == campsiteId }?.let { site ->
                draftState.month = YearMonth.from(draftState.value.checkIn ?: localToday(site))
                savedState["bookingMonth"] = draftState.month.toString()
            }
        }
        if (knownReservationId == null) {
            if (!hasOwnDrawnWin()) {
                stopOpportunity()
            } else if (!opportunityAttempted && !submissionBlocksEditing()) {
                loadOpportunity()
            }
        }
        campsite
            ?.takeIf { BookingReadSource.CAMPSITE in loaded && it.id == campsiteId }
            ?.let(draftState::reconcileApproval)
        syncCalendar()
        render()
    }

    /** Server-confirms consuming history before edits; UI never derives the stored key. */
    private fun loadOpportunity() {
        if (closed || submissionBlocksEditing() || opportunityInFlight ||
            knownReservationId != null || !hasOwnDrawnWin()
        ) {
            return
        }
        val version = ++opportunityVersion
        opportunityJob?.cancel()
        opportunityAttempted = true
        opportunityInFlight = true
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
                        rememberReservation(reservation.id)
                    } else {
                        reconcile()
                    }
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

    /** Shares consuming identity between ordinary entry and submission recovery.
     * @param id Repository-confirmed saved reservation identity.
     */
    private fun rememberReservation(id: String) {
        knownReservationId = id
        savedState["bookingReservationId"] = id
        setRecoveryRequired(false)
        submissionSnapshot = null
        mutableSubmission.value = BookingSubmission.Idle
        reconcile()
        complete(id)
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
        if (submissionBlocksEditing()) {
            calendar.stop()
            return
        }
        val editable = readOutcome() as? BookingUiState.Content
        if (editable == null) {
            calendar.stop()
        } else {
            calendar.update(
                draftState.month.takeIf {
                    datesVisible && draftState.value.step == BookingStep.DATES
                },
                draftState.value.checkIn,
                draftState.value.checkOut,
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
        if (opportunityFailed &&
            !opportunityUnused
        ) {
            return BookingUiState.Failure(BookingReadSource.OPPORTUNITY)
        }
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
            draftState.value,
            checkNotNull(draftState.month),
            localToday(site),
            isCheckingReservation = opportunityInFlight,
            reservationCheckFailed = opportunityFailed,
        )
    }

    /** Projects inventory, eligibility and price without changing the calendar subscription. */
    private fun render() {
        if (closed) return
        val frozen = submissionSnapshot
        if (submissionBlocksEditing() && frozen != null && knownReservationId == null) {
            mutableState.value = frozen
            syncPhoto()
            scheduleTime()
            return
        }
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
                val start = draftState.value.checkIn
                val end = draftState.value.checkOut
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
                val content =
                    outcome.copy(
                        calendar = read,
                        eligibilityPending = availableNights == null,
                        visibleEligibleNights = visible,
                        selectedEligibleNights = selected,
                        stayValidation = validation,
                        quote = quote,
                        error = draftState.error,
                        guestCount =
                            (
                                parsePositiveCount(draftState.value.guestInput, Long.MAX_VALUE)
                                    as? PositiveCountInput.Value
                            )?.count,
                    )
                val amount = (quote as? BookingQuote.Value)?.totalCents
                content.copy(
                    canConfirm =
                        draftState.value.step == BookingStep.REVIEW && !opportunityInFlight &&
                            !opportunityFailed && amount != null &&
                            draftState.value.reviewedTotalCents == amount &&
                            listOf(BookingStep.DATES, BookingStep.GUESTS, BookingStep.TERMS).all {
                                draftState.stepError(content, it) == null
                            },
                )
            } else {
                outcome
            }
        syncPhoto()
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

    /** Releases all sources and invalidates queued/noncooperative work when the graph retires. */
    override fun onCleared() {
        closed = true
        submissionVersion++
        submissionJob?.cancel()
        versions.keys.toList().forEach { versions[it] = checkNotNull(versions[it]) + 1 }
        jobs.values.forEach { it.cancel() }
        opportunityVersion++
        opportunityJob?.cancel()
        timeJob?.cancel()
        calendar.close()
        calendarJob.cancel()
        stopPhoto()
        super.onCleared()
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.resolveDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

private const val SETUP_KEY = "lotterySetup."

/** Owns six-step input, its allocation child and explicit same-ID save/recovery operations.
 * @param ownerId Confirmed owner identity used to scope campsite choices.
 * @param campsites Existing published-choice and inventory source.
 * @param lotteries Server reads and explicit lottery mutations.
 * @param lotteryId Existing target, or null for a new local form.
 * @param newId Stable identity allocator used only by a valid explicit save.
 * @param clock Initial month in the selected site's zone.
 * @param savedState Primitive input retained across graph recreation.
 * @param validateWindows Reserved F13 contract; null keeps configuration validation pending.
 */
class LotterySetupViewModel(
    private val ownerId: String,
    private val campsites: CampsiteRepository,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val validateWindows: LotteryWindowValidator? = null,
    private val lotteryId: String? = null,
    private val newId: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    },
) : ViewModel() {
    private val retainedInput = savedState.contains("${SETUP_KEY}title")
    private val writer = LotteryDraftWriter(lotteries)
    private var baseline = LotteryDraft()
    private var pendingConfiguration: LotteryConfiguration? = null
    private var readJob: Job? = null
    private val mutableState = MutableStateFlow(restoredState())
    val state = mutableState.asStateFlow()
    private val mutableExit = MutableStateFlow(savedState.get<Boolean>("${SETUP_KEY}exit") == true)
    val exitRequested = mutableExit.asStateFlow()

    private val mutableCompletion =
        MutableStateFlow(savedState.get<String>("${SETUP_KEY}completion"))
    val completion = mutableCompletion.asStateFlow()

    /** Consumes a successful navigation handoff after its graph has been removed. */
    fun acknowledgeCompletion() {
        if (completion.value == null) return
        savedState["${SETUP_KEY}finished"] = true
        savedState.remove<String>("${SETUP_KEY}completion")
        mutableCompletion.value = null
    }

    /** Starts an explicit reviewed draft save. */
    fun saveDraft() =
        beginSave(LotterySaveAction.DRAFT)

    /** Starts an explicit reviewed publication after its future-opening check. */
    fun publishLottery() =
        beginSave(LotterySaveAction.PUBLISH)

    /** Retries the retained action or resolves an uncertain publication without extra writes. */
    fun retrySave() {
        if (isBusy() || completion.value != null || exitRequested.value ||
            savedState.get<Boolean>("${SETUP_KEY}finished") == true
        ) {
            return
        }
        val current = state.value
        if (current.publicationPending) {
            recoverPublication()
        } else if (current.saveFailure != null) {
            current.saveAction?.let(::beginSave)
        }
    }

    /** Retries a failed saved-target read without replacing retained local input. */
    fun retryLoad() {
        if (state.value.targetLoad == LotteryTargetLoad.FAILURE && !isBusy() &&
            !state.value.publicationPending && completion.value == null && !exitRequested.value
        ) {
            loadTarget()
        }
    }

    /** Supplies the selected site's clock month for a native schedule picker with no prior date. */
    fun currentScheduleMonth(): YearMonth? =
        selectedSite()?.let { YearMonth.now(clock.withZone(ZoneId.of(it.details.timeZone))) }

    private var choiceJob: Job? = null
    private var calendarJob: Job? = null
    private var choiceVersion = 0L
    private var calendarVersion = 0L
    private var calendarRange: Triple<String, LocalDate, LocalDate>? = null

    init {
        if (!exitRequested.value && completion.value == null &&
            savedState.get<Boolean>("${SETUP_KEY}finished") != true
        ) {
            if (state.value.draft.id == null) observeChoices() else loadTarget()
        }
    }

    /** Updates only local title spelling.
     * @param value Raw title.
     */
    fun editTitle(value: String) =
        editDraft { it.copy(title = value) }

    /** Updates only local optional description.
     * @param value Raw description.
     */
    fun editDescription(value: String) =
        editDraft { it.copy(description = value) }

    /** Chooses one eligible published campsite and retires any former allocation.
     * @param id Current saved choice identity.
     */
    fun chooseCampsite(id: String) {
        if (!canEdit() || state.value.draft.step != LotteryStep.SITE) return
        if (choices().none { it.id == id } || state.value.draft.campsiteId == id) return
        val pending = pendingConfiguration
        editDraft {
            val changed = it.copy(campsiteId = id, nights = emptySet())
            if (pending != null && it.schedule.isEmpty()) {
                withSchedule(changed, pending, choices().first { site -> site.id == id })
            } else {
                changed
            }
        }
        pendingConfiguration = null
    }

    /** Updates one explicit local date.
     * @param point Fixed schedule input.
     * @param value Local selected date.
     */
    fun editScheduleDate(
        point: LotterySchedulePoint,
        value: LocalDate,
    ) {
        editSchedule(point) { it.copy(date = value) }
    }

    /** Updates one explicit local time.
     * @param point Fixed schedule input.
     * @param value Local selected time.
     */
    fun editScheduleTime(
        point: LotterySchedulePoint,
        value: LocalTime,
    ) {
        editSchedule(point) { it.copy(time = value) }
    }

    /** Accepts the displayed four local schedule values in the currently selected site's zone. */
    fun confirmScheduleZone() {
        val zone = selectedSite()?.details?.timeZone ?: return
        editDraft { it.copy(scheduleZone = zone) }
    }

    /** Updates raw maximum-winner input without a registration-cap field.
     * @param value Raw positive-count spelling.
     */
    fun editWinnerCount(value: String) =
        editDraft { it.copy(maximumWinnersText = value) }

    /** Advances only after this step's local prerequisites pass. */
    fun next() {
        if (!canEdit()) return
        val current = state.value
        val draft = current.draft
        if (draft.step == LotteryStep.REVIEW) return
        val errors =
            when (draft.step) {
                LotteryStep.BASICS -> {
                    LotterySetupErrors(titleRequired = draft.title.isBlank())
                }

                LotteryStep.SITE -> {
                    LotterySetupErrors(
                        campsiteRequired = selectedSite() == null,
                        allocationRequired = draft.nights.isEmpty(),
                    )
                }

                LotteryStep.REGISTRATION -> {
                    LotterySetupErrors(
                        campsiteRequired = selectedSite() == null,
                        schedule =
                            scheduleErrors(
                                draft,
                                selectedSite(),
                                listOf(
                                    LotterySchedulePoint.REGISTRATION_OPEN,
                                    LotterySchedulePoint.REGISTRATION_CLOSE,
                                ),
                            ),
                    )
                }

                LotteryStep.BOOKING -> {
                    LotterySetupErrors(
                        campsiteRequired = selectedSite() == null,
                        schedule =
                            scheduleErrors(
                                draft,
                                selectedSite(),
                                listOf(
                                    LotterySchedulePoint.BOOKING_OPEN,
                                    LotterySchedulePoint.BOOKING_CLOSE,
                                ),
                            ),
                    )
                }

                LotteryStep.WINNERS -> {
                    LotterySetupErrors(
                        winnerCount =
                            parsePositiveCount(
                                draft.maximumWinnersText,
                                Long.MAX_VALUE,
                            ) as? PositiveCountInput.Error,
                    )
                }

                LotteryStep.REVIEW -> {
                    return
                }
            }
        if (errors != LotterySetupErrors()) {
            publish(current.copy(errors = errors))
            return
        }
        val next =
            if (draft.returnToReview) {
                LotteryStep.REVIEW
            } else {
                LotteryStep.entries[draft.step.ordinal + 1]
            }
        publish(
            reviewed(
                current.copy(
                    draft = draft.copy(step = next, returnToReview = false),
                    errors = LotterySetupErrors(),
                ),
            ),
        )
    }

    /** Returns to the previous step/review or requests explicit abandonment. */
    fun back() {
        val current = state.value
        if (current.allocation != null) {
            cancelAllocation()
            return
        }
        if (isLocked()) return
        if (current.targetLoad != LotteryTargetLoad.READY) {
            requestExit()
            return
        }
        if (!canEdit()) return
        if (current.draft.step == LotteryStep.BASICS) {
            requestExit()
            return
        }
        val previous =
            if (current.draft.returnToReview) {
                LotteryStep.REVIEW
            } else {
                LotteryStep.entries[current.draft.step.ordinal - 1]
            }
        publish(
            reviewed(
                current.copy(
                    draft =
                        current.draft.copy(
                            step = previous,
                            returnToReview = false,
                        ),
                    errors = LotterySetupErrors(),
                    configuration = null,
                ),
            ),
        )
    }

    /** Opens an editable review section without discarding other input.
     * @param step Actual non-review step to edit.
     */
    fun editStep(step: LotteryStep) {
        if (!canEdit() || state.value.draft.step != LotteryStep.REVIEW ||
            step == LotteryStep.REVIEW
        ) {
            return
        }
        val current = state.value
        publish(
            current.copy(
                draft = current.draft.copy(step = step, returnToReview = true),
                errors = LotterySetupErrors(),
                configuration = null,
            ),
        )
    }

    /** Creates a working allocation from the parent's applied nights. */
    fun beginAllocation(): Boolean {
        if (!canEdit() || state.value.draft.step != LotteryStep.SITE) return false
        val site = selectedSite() ?: return false
        val current = state.value
        publish(
            current.copy(
                allocation =
                    LotteryAllocationUiState(
                        YearMonth.now(clock.withZone(ZoneId.of(site.details.timeZone))),
                        current.draft.nights.toSet(),
                    ),
            ),
        )
        reconcileCalendar()
        return true
    }

    /** Toggles a known eligible night, or removes an already-selected invalidated night.
     * @param date Actual visible calendar date.
     */
    fun toggleAllocationDate(date: LocalDate) {
        val current = state.value
        val allocation = current.allocation ?: return
        val known = allocation.inventory as? CalendarInventoryRead.Content ?: return
        if (exitRequested.value || current.discardPrompt ||
            YearMonth.from(date) != allocation.month
        ) {
            return
        }
        val selected = allocation.selected
        if (date !in selected && known.nights.none { it.date == date && it.isFree }) return
        val updated = if (date in selected) selected - date else selected + date
        publish(
            current.copy(
                allocation = allocation.copy(selected = updated, invalidSelection = false),
            ),
        )
        reconcileCalendar()
    }

    /** Requests a different visible month while retaining working nights.
     * @param month Actual requested local month.
     */
    fun changeAllocationMonth(month: YearMonth) {
        val current = state.value
        val allocation = current.allocation ?: return
        if (exitRequested.value || current.discardPrompt || allocation.month == month) return
        publish(current.copy(allocation = allocation.copy(month = month)))
        reconcileCalendar()
    }

    /** Applies a nonempty confirmed eligible set to the parent draft. */
    fun applyAllocation(): Boolean {
        val current = state.value
        val allocation = current.allocation ?: return false
        if (exitRequested.value || current.discardPrompt) return false
        if (selectedSite() == null || !allocation.canApply) {
            publish(current.copy(allocation = allocation.copy(invalidSelection = true)))
            return false
        }
        stopCalendar()
        publish(
            current.copy(
                draft = current.draft.copy(nights = allocation.selected.toSet()),
                allocation = null,
                errors = LotterySetupErrors(),
                configuration = null,
            ),
        )
        return true
    }

    /** Discards working allocation edits and stops their inventory observation. */
    fun cancelAllocation() {
        stopCalendar()
        publish(state.value.copy(allocation = null))
    }

    /** Retries failed owner-choice observation without replacing raw form input. */
    fun retryChoices() {
        if (!isLocked() &&
            state.value.choices == LotteryCampsiteChoices.Failure
        ) {
            observeChoices()
        }
    }

    /** Retries the required working-allocation range without changing selection. */
    fun retryAllocation() {
        if (state.value.allocation?.inventory == CalendarInventoryRead.Failure) {
            calendarRange = null
            reconcileCalendar()
        }
    }

    /** Requests exit, confirming abandonment only when local input changed. */
    fun requestExit() {
        if (isLocked()) return
        val changed = normalized(state.value.draft) != normalized(baseline)
        if (changed) publish(state.value.copy(discardPrompt = true)) else confirmDiscard()
    }

    /** Keeps local input after declining abandonment. */
    fun dismissDiscard() {
        if (isLocked()) return
        publish(state.value.copy(discardPrompt = false))
    }

    /** Discards this local draft and signals its registered caller to return. */
    fun confirmDiscard() {
        if (isLocked()) return
        readJob?.cancel()
        choiceVersion++
        choiceJob?.cancel()
        stopCalendar()
        for (key in savedState.keys().filter {
            it.startsWith(
                SETUP_KEY,
            )
        }) {
            savedState.remove<Any>(key)
        }
        savedState["${SETUP_KEY}exit"] = true
        mutableExit.value = true
        mutableState.value = LotterySetupUiState()
    }

    /** Freezes valid reviewed input and retains its identity/action before launching any work.
     * @param action Requested draft save or publication; retry preserves the previous choice.
     */
    private fun beginSave(action: LotterySaveAction) {
        if (!canEdit() || state.value.draft.step != LotteryStep.REVIEW) return
        val current = reviewed(state.value)
        val configuration = current.configuration
        if (configuration == null) {
            publish(current)
            return
        }
        if (action == LotterySaveAction.PUBLISH &&
            configuration.registrationOpensAt <= clock.instant()
        ) {
            publish(current.copy(publicationTooLate = true))
            return
        }
        val id = current.draft.id ?: newId().also { savedState["${SETUP_KEY}allowCreate"] = true }
        val draft = current.draft.copy(id = id)
        val request =
            LotterySaveRequest(
                id,
                ownerId,
                draft.title,
                draft.description,
                configuration,
                LotteryOffer(checkNotNull(draft.campsiteId), draft.nights.sorted()),
                allowCreate = savedState.get<Boolean>("${SETUP_KEY}allowCreate") == true,
                action = action,
            )
        publish(
            current.copy(
                draft = draft,
                savePhase = LotterySavePhase.RECOVERING,
                saveFailure = null,
                saveAction = action,
                publicationTooLate = false,
            ),
        )
        viewModelScope.launch { finishSave(writer.save(request, ::savePhase)) }
    }

    /** Retains the publication marker before the writer calls its potentially unacknowledged write.
     * @param phase Actual writer stage.
     */
    private fun savePhase(phase: LotterySavePhase) {
        if (phase == LotterySavePhase.SAVING_OFFER) savedState["${SETUP_KEY}allowCreate"] = false
        publish(
            state.value.copy(
                savePhase = phase,
                publicationPending =
                    state.value.publicationPending || phase == LotterySavePhase.PUBLISHING,
            ),
        )
    }

    /**
     * Resolves only the previous publication using identity, without reconstructing validated
     * input.
     */
    private fun recoverPublication() {
        val id = state.value.draft.id ?: return
        publish(state.value.copy(savePhase = LotterySavePhase.RECOVERING, saveFailure = null))
        viewModelScope.launch {
            val result = writer.recoverPublication(id, ownerId, ::savePhase)
            finishSave(result)
            if (result is LotterySaveResult.Failed && !result.publicationUncertain) {
                publish(reviewed(state.value.copy(targetLoad = LotteryTargetLoad.READY)))
                if (choiceJob == null) observeChoices()
            }
        }
    }

    /**
     * Applies an acknowledged result without losing input or confusing source failures with save
     * failures.
     * @param result Actual writer or read-only publication-recovery outcome.
     */
    private fun finishSave(result: LotterySaveResult) {
        when (result) {
            is LotterySaveResult.Saved -> {
                complete(result.id)
            }

            is LotterySaveResult.Published -> {
                complete(result.id)
            }

            is LotterySaveResult.Failed -> {
                publish(
                    state.value.copy(
                        savePhase = LotterySavePhase.IDLE,
                        saveFailure = result,
                        publicationPending = result.publicationUncertain,
                    ),
                )
            }

            LotterySaveResult.Unavailable -> {
                publish(
                    state.value.copy(
                        savePhase = LotterySavePhase.IDLE,
                        saveFailure = null,
                        publicationPending = false,
                        targetLoad = LotteryTargetLoad.UNAVAILABLE,
                    ),
                )
            }
        }
    }

    /** Retains navigation independently of loaded records, then retires all source observations.
     * @param id Actual saved or already-published target.
     */
    private fun complete(id: String) {
        savedState["${SETUP_KEY}completion"] = id
        mutableCompletion.value = id
        choiceVersion++
        choiceJob?.cancel()
        choiceJob = null
        stopCalendar()
        publish(state.value.copy(savePhase = LotterySavePhase.IDLE, publicationPending = false))
    }

    /** Server-reads an existing or interrupted target; restoration never repeats a mutation. */
    private fun loadTarget() {
        val id = state.value.draft.id ?: return
        readJob?.cancel()
        if (state.value.publicationPending) {
            recoverPublication()
            return
        }
        publish(state.value.copy(targetLoad = LotteryTargetLoad.LOADING))
        readJob =
            viewModelScope.launch {
                when (val result = writer.read(id, ownerId)) {
                    is LotteryDraftRead.Content -> {
                        restoreContent(result)
                    }

                    is LotteryDraftRead.Published -> {
                        complete(result.id)
                    }

                    LotteryDraftRead.Failed -> {
                        publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
                    }

                    LotteryDraftRead.Unavailable -> {
                        publish(state.value.copy(targetLoad = LotteryTargetLoad.UNAVAILABLE))
                    }

                    LotteryDraftRead.Missing -> {
                        val retryable =
                            lotteryId == null &&
                                savedState.get<Boolean>("${SETUP_KEY}allowCreate") == true
                        publish(
                            state.value.copy(
                                targetLoad =
                                    if (retryable) {
                                        LotteryTargetLoad.READY
                                    } else {
                                        LotteryTargetLoad.UNAVAILABLE
                                    },
                            ),
                        )
                        if (retryable) observeChoices()
                    }
                }
            }
    }

    /** Hydrates fresh server input or validates the identity behind retained edits.
     * @param content Owned draft and every authoritative allocation.
     */
    private suspend fun restoreContent(content: LotteryDraftRead.Content) {
        try {
            val offer = content.offers.singleOrNull()
            val site = offer?.let { campsites.get(it.campsiteId) }
            currentCoroutineContext().ensureActive()
            val record = content.lottery
            var fresh =
                LotteryDraft(
                    id = record.id,
                    step = LotteryStep.SITE,
                    title = record.title,
                    description = record.description,
                    campsiteId = offer?.campsiteId,
                    nights = offer?.nights?.toSet().orEmpty(),
                    maximumWinnersText = record.configuration.winnerCount.toString(),
                )
            if (site != null && site.ownerId == ownerId) {
                fresh =
                    withSchedule(fresh, record.configuration, site).copy(step = LotteryStep.REVIEW)
            }
            baseline = fresh
            val draft = if (retainedInput) state.value.draft else fresh
            if (draft.schedule.isEmpty()) pendingConfiguration = record.configuration
            savedState["${SETUP_KEY}allowCreate"] = false
            publish(state.value.copy(draft = draft, targetLoad = LotteryTargetLoad.READY))
            observeChoices()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
        }
    }

    /** Converts stored windows only in a real site's zone; no device-zone default is used.
     * @param draft Input whose schedule has not yet been populated.
     * @param configuration Confirmed stored windows and count.
     * @param site Actual selected record providing the zone.
     */
    private fun withSchedule(
        draft: LotteryDraft,
        configuration: LotteryConfiguration,
        site: Campsite,
    ): LotteryDraft {
        val instants =
            listOf(
                configuration.registrationOpensAt,
                configuration.registrationClosesAt,
                configuration.bookingOpensAt,
                configuration.bookingClosesAt,
            )
        val zone = ZoneId.of(site.details.timeZone)
        return draft.copy(
            scheduleZone = site.details.timeZone,
            schedule =
                LotterySchedulePoint.entries.zip(instants).associate { (point, instant) ->
                    val local = instant.atZone(zone)
                    point to LotteryLocalDateTime(local.toLocalDate(), local.toLocalTime())
                },
        )
    }

    /** Compares editable facts without treating navigation or allocated identity as unsaved input.
     * @param draft Current or server-baseline form.
     */
    private fun normalized(draft: LotteryDraft): LotteryDraft =
        draft.copy(id = null, step = LotteryStep.BASICS, returnToReview = false)

    /**
     * Updates local input only when its parent form is active, invalidating any prior
     * configuration.
     * @param transform Specific immutable field update.
     */
    private fun editDraft(transform: (LotteryDraft) -> LotteryDraft) {
        if (!canEdit()) return
        val current = state.value
        publish(
            reviewed(
                current.copy(
                    draft = transform(current.draft),
                    errors = LotterySetupErrors(),
                    configuration = null,
                ),
            ),
        )
    }

    /** Retains the previously accepted interpretation until a changed zone is explicitly confirmed.
     * @param point Actual schedule input.
     * @param transform Specific date or time edit.
     */
    private fun editSchedule(
        point: LotterySchedulePoint,
        transform: (LotteryLocalDateTime) -> LotteryLocalDateTime,
    ) {
        val zone = selectedSite()?.details?.timeZone ?: return
        editDraft { draft ->
            draft.copy(
                schedule =
                    draft.schedule +
                        (point to transform(draft.schedule[point] ?: LotteryLocalDateTime())),
                scheduleZone = draft.scheduleZone ?: zone,
            )
        }
    }

    /**
     * Returns whether parent actions can change local fields without racing a child or exit
     * prompt.
     */
    private fun canEdit(): Boolean =
        !isLocked() && state.value.targetLoad == LotteryTargetLoad.READY &&
            !state.value.discardPrompt && state.value.allocation == null

    /** Returns whether an operation owns the form, including unresolved publication. */
    private fun isLocked(): Boolean =
        isBusy() || state.value.publicationPending || exitRequested.value ||
            completion.value != null ||
            savedState.get<Boolean>("${SETUP_KEY}finished") == true

    /** Separates active progress from a failed operation that can be retried. */
    private fun isBusy(): Boolean =
        state.value.savePhase != LotterySavePhase.IDLE

    /** Returns only confirmed current choices; failure/loading is never an empty-source claim. */
    private fun choices(): List<Campsite> =
        (state.value.choices as? LotteryCampsiteChoices.Content)?.campsites.orEmpty()

    /** Resolves the selected identity from the current authoritative choice emission. */
    private fun selectedSite(): Campsite? =
        choices().firstOrNull {
            it.id ==
                state.value.draft.campsiteId
        }

    /** Observes one owner source and retains local form/child input through failed reads. */
    private fun observeChoices() {
        val version = ++choiceVersion
        choiceJob?.cancel()
        stopCalendar()
        publish(
            state.value.copy(
                choices = LotteryCampsiteChoices.Loading,
                configuration = null,
                allocation =
                    state.value.allocation?.copy(
                        inventory = CalendarInventoryRead.Loading,
                    ),
            ),
        )
        choiceJob =
            viewModelScope.launch {
                try {
                    campsites.observeForOwner(ownerId).collect { records ->
                        if (version != choiceVersion || exitRequested.value ||
                            completion.value != null
                        ) {
                            return@collect
                        }
                        val eligible = records.filter { it.ownerId == ownerId && it.isPublished }
                        publish(
                            reviewed(
                                state.value.copy(
                                    choices = LotteryCampsiteChoices.Content(eligible),
                                ),
                            ),
                        )
                        reconcileCalendar()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version != choiceVersion || exitRequested.value ||
                        completion.value != null
                    ) {
                        return@launch
                    }
                    stopCalendar()
                    publish(
                        state.value.copy(
                            choices = LotteryCampsiteChoices.Failure,
                            configuration = null,
                            allocation =
                                state.value.allocation?.copy(
                                    inventory = CalendarInventoryRead.Loading,
                                ),
                        ),
                    )
                }
            }
    }

    /** Reads one range spanning the visible month and every working selected month. */
    private fun reconcileCalendar() {
        val current = state.value
        val allocation = current.allocation
        val site = selectedSite()
        if (exitRequested.value || allocation == null || site == null) {
            stopCalendar()
            if (allocation != null) {
                publish(
                    current.copy(
                        allocation =
                            allocation.copy(inventory = CalendarInventoryRead.Loading),
                    ),
                )
            }
            return
        }
        val first =
            minOf(
                allocation.month,
                allocation.selected.minOrNull()?.let(YearMonth::from)
                    ?: allocation.month,
            )
        val last =
            maxOf(
                allocation.month,
                allocation.selected.maxOrNull()?.let(YearMonth::from)
                    ?: allocation.month,
            )
        val range = Triple(site.id, first.atDay(1), last.plusMonths(1).atDay(1))
        if (range == calendarRange) return
        stopCalendar()
        calendarRange = range
        val version = calendarVersion
        publish(
            current.copy(
                allocation = allocation.copy(inventory = CalendarInventoryRead.Loading),
            ),
        )
        calendarJob =
            viewModelScope.launch {
                try {
                    campsites
                        .observeCalendar(range.first, range.second, range.third)
                        .collect { nights ->
                            if (version != calendarVersion || exitRequested.value) return@collect
                            val active = state.value.allocation ?: return@collect
                            publish(
                                state.value.copy(
                                    allocation =
                                        active.copy(
                                            inventory = CalendarInventoryRead.Content(nights),
                                        ),
                                ),
                            )
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version != calendarVersion || exitRequested.value) return@launch
                    state.value.allocation?.let { active ->
                        publish(
                            state.value.copy(
                                allocation =
                                    active.copy(
                                        inventory = CalendarInventoryRead.Failure,
                                    ),
                            ),
                        )
                    }
                }
            }
    }

    /** Invalidates retired range callbacks before releasing their listener. */
    private fun stopCalendar() {
        calendarVersion++
        calendarJob?.cancel()
        calendarJob = null
        calendarRange = null
    }

    /** Returns direct missing/DST feedback for the requested fixed schedule fields.
     * @param draft Current raw input.
     * @param site Current eligible site supplying the explicit zone.
     * @param points Fields relevant to the current step or full review.
     */
    private fun scheduleErrors(
        draft: LotteryDraft,
        site: Campsite?,
        points: List<LotterySchedulePoint>,
    ): Map<LotterySchedulePoint, LotteryScheduleError> =
        buildMap {
            for (point in points) {
                val input = draft.schedule[point] ?: LotteryLocalDateTime()
                val error =
                    when {
                        input.date == null -> {
                            LotteryScheduleError.DATE_REQUIRED
                        }

                        input.time == null -> {
                            LotteryScheduleError.TIME_REQUIRED
                        }

                        site != null && resolve(input, site.details.timeZone) == null -> {
                            LotteryScheduleError.AMBIGUOUS_TIME
                        }

                        else -> {
                            null
                        }
                    }
                if (error != null) put(point, error)
            }
        }

    /** Converts one complete local value through the existing unambiguous-zone boundary.
     * @param input Explicit date/time input.
     * @param zone Actual selected campsite's IANA identifier.
     */
    private fun resolve(
        input: LotteryLocalDateTime,
        zone: String,
    ): Instant? {
        val date = input.date ?: return null
        val time = input.time ?: return null
        return resolveDateTime(date, time.hour, time.minute, ZoneId.of(zone))
    }

    /** Assembles review feedback and invokes F13 only after conversion and zone acknowledgement.
     * @param current Immutable snapshot to validate when review is visible.
     */
    private fun reviewed(current: LotterySetupUiState): LotterySetupUiState {
        if (current.draft.step != LotteryStep.REVIEW) return current.copy(configuration = null)
        val draft = current.draft
        val site =
            (current.choices as? LotteryCampsiteChoices.Content)
                ?.campsites
                ?.firstOrNull { it.id == draft.campsiteId }
        val schedule = scheduleErrors(draft, site, LotterySchedulePoint.entries)
        val count = parsePositiveCount(draft.maximumWinnersText, Long.MAX_VALUE)
        val zoneChanged =
            site != null && draft.schedule.isNotEmpty() &&
                draft.scheduleZone != site.details.timeZone
        val instants =
            if (site != null && schedule.isEmpty()) {
                LotterySchedulePoint.entries.map {
                    resolve(
                        draft.schedule.getValue(it),
                        site.details.timeZone,
                    )!!
                }
            } else {
                emptyList()
            }
        val windows =
            if (instants.size == 4 && !zoneChanged) {
                validateWindows?.invoke(instants[0], instants[1], instants[2], instants[3])
            } else {
                null
            }
        val errors =
            LotterySetupErrors(
                draft.title.isBlank(),
                site == null,
                draft.nights.isEmpty(),
                schedule,
                count as? PositiveCountInput.Error,
                windows?.takeUnless { it == LotteryWindowInput.Valid },
                windowsPending = instants.size == 4 && validateWindows == null,
                zoneConfirmationRequired = zoneChanged,
            )
        val valid =
            !errors.titleRequired && !errors.campsiteRequired && !errors.allocationRequired &&
                schedule.isEmpty() && count is PositiveCountInput.Value &&
                !zoneChanged && windows == LotteryWindowInput.Valid
        val configuration =
            if (valid) {
                LotteryConfiguration(
                    instants[0],
                    instants[1],
                    instants[2],
                    instants[3],
                    (count as PositiveCountInput.Value).count,
                    waitlistCapacity = null,
                )
            } else {
                null
            }
        return current.copy(errors = errors, configuration = configuration)
    }

    /** Publishes one immutable local snapshot and retains only small primitive inputs.
     * @param current Complete next form/child state.
     */
    private fun publish(current: LotterySetupUiState) {
        mutableState.value = current
        val draft = current.draft
        savedState["${SETUP_KEY}id"] = draft.id
        savedState["${SETUP_KEY}saveAction"] = current.saveAction?.name
        savedState["${SETUP_KEY}publicationPending"] = current.publicationPending
        savedState["${SETUP_KEY}step"] = draft.step.name
        savedState["${SETUP_KEY}title"] = draft.title
        savedState["${SETUP_KEY}description"] = draft.description
        savedState["${SETUP_KEY}campsite"] = draft.campsiteId
        savedState["${SETUP_KEY}nights"] = ArrayList(draft.nights.map(LocalDate::toString))
        savedState["${SETUP_KEY}zone"] = draft.scheduleZone
        savedState["${SETUP_KEY}winners"] = draft.maximumWinnersText
        savedState["${SETUP_KEY}reviewReturn"] = draft.returnToReview
        for (point in LotterySchedulePoint.entries) {
            savedState["$SETUP_KEY${point.name}.date"] = draft.schedule[point]?.date?.toString()
            savedState["$SETUP_KEY${point.name}.time"] = draft.schedule[point]?.time?.toString()
        }
        val allocation = current.allocation
        savedState["${SETUP_KEY}allocationOpen"] = allocation != null
        savedState["${SETUP_KEY}month"] = allocation?.month?.toString()
        savedState["${SETUP_KEY}workingNights"] =
            allocation?.selected?.map(LocalDate::toString)?.let(::ArrayList)
    }

    /**
     * Restores local primitives without retaining loaded records or starting a persistence
     * action.
     */
    private fun restoredState(): LotterySetupUiState {
        val schedule =
            buildMap {
                for (point in LotterySchedulePoint.entries) {
                    val date =
                        savedState
                            .get<String>(
                                "$SETUP_KEY${point.name}.date",
                            )?.let(LocalDate::parse)
                    val time =
                        savedState
                            .get<String>(
                                "$SETUP_KEY${point.name}.time",
                            )?.let(LocalTime::parse)
                    if (date != null || time != null) put(point, LotteryLocalDateTime(date, time))
                }
            }
        val draft =
            LotteryDraft(
                id = savedState["${SETUP_KEY}id"] ?: lotteryId,
                step =
                    LotteryStep.valueOf(
                        savedState["${SETUP_KEY}step"] ?: LotteryStep.BASICS.name,
                    ),
                title = savedState["${SETUP_KEY}title"] ?: "",
                description = savedState["${SETUP_KEY}description"] ?: "",
                campsiteId = savedState["${SETUP_KEY}campsite"],
                nights = savedDates("nights"),
                schedule = schedule,
                scheduleZone = savedState["${SETUP_KEY}zone"],
                maximumWinnersText = savedState["${SETUP_KEY}winners"] ?: "",
                returnToReview = savedState["${SETUP_KEY}reviewReturn"] ?: false,
            )
        val month = savedState.get<String>("${SETUP_KEY}month")?.let(YearMonth::parse)
        val allocation =
            if (savedState.get<Boolean>("${SETUP_KEY}allocationOpen") == true &&
                month != null
            ) {
                LotteryAllocationUiState(month, savedDates("workingNights"))
            } else {
                null
            }
        val action =
            savedState
                .get<String>(
                    "${SETUP_KEY}saveAction",
                )?.let(LotterySaveAction::valueOf)
        return LotterySetupUiState(
            draft = draft,
            allocation = allocation,
            saveAction = action,
            saveFailure = action?.let { LotterySaveResult.Failed(LotterySavePhase.RECOVERING) },
            publicationPending = savedState["${SETUP_KEY}publicationPending"] ?: false,
            targetLoad =
                if (draft.id ==
                    null
                ) {
                    LotteryTargetLoad.READY
                } else {
                    LotteryTargetLoad.LOADING
                },
        )
    }

    /** Decodes a saved primitive date set owned by this form.
     * @param suffix Local input key suffix.
     */
    private fun savedDates(suffix: String): Set<LocalDate> =
        savedState
            .get<ArrayList<String>>(
                "$SETUP_KEY$suffix",
            ).orEmpty()
            .map(LocalDate::parse)
            .toSet()

    /**
     * Retires graph callbacks and releases both read lifetimes without changing the last
     * snapshot.
     */
    override fun onCleared() {
        choiceVersion++
        calendarVersion++
        choiceJob?.cancel()
        calendarJob?.cancel()
        readJob?.cancel()
        super.onCleared()
    }
}

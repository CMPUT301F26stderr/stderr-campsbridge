/*
 * Owns six-step input, its allocation child and explicit same-ID save/recovery operations.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.AppDisplayTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.common.navigation.StepNavigation
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
 * @param clock Time source for schedule display and campsite-local availability.
 * @param savedState Primitive input retained across graph recreation.
 * @param validateWindows F13 window validator; null keeps configuration validation explicitly pending.
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
    private val writer = LotteryDraftWriter(lotteries, clock)
    private var baseline = LotteryDraft()
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

    /** Supplies the shared display-zone month for a schedule picker with no prior date. */
    fun currentScheduleMonth(): YearMonth =
        YearMonth.now(clock.withZone(AppDisplayTime.zone))

    private var choiceJob: Job? = null
    private var choiceVersion = 0L
    private val workingAllocation =
        LotteryAllocationController(campsites, viewModelScope) { allocation ->
            publish(state.value.copy(allocation = allocation))
        }

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

    /** Focuses one owned campsite without changing any applied allocation.
     * @param id Current saved choice identity.
     */
    fun chooseCampsite(id: String) {
        if (!canEdit() || state.value.draft.step != LotteryStep.SITE) return
        if (choices().none { it.id == id } || state.value.draft.campsiteId == id) return
        editDraft {
            it.copy(campsiteId = id)
        }
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

    /** Updates raw maximum-winner input independently of registration capacity.
     * @param value Raw positive-count spelling.
     */
    fun editWinnerCount(value: String) =
        editDraft { it.copy(maximumWinnersText = value) }

    /** Removes one applied local allocation without saving the lottery.
     * @param campsiteId Applied campsite to remove.
     */
    fun removeAllocation(campsiteId: String) {
        if (!canEdit() || state.value.draft.step != LotteryStep.SITE ||
            state.value.draft.allocations
                .none { it.campsiteId == campsiteId }
        ) {
            return
        }
        editDraft { draft ->
            val remaining = draft.allocations.filterNot { it.campsiteId == campsiteId }
            draft.copy(
                allocations = remaining,
                campsiteId =
                    draft.campsiteId?.takeIf { id -> remaining.any { it.campsiteId == id } }
                        ?: remaining.firstOrNull()?.campsiteId,
            )
        }
    }

    /** Retains the optional registration-limit input.
     * @param value Raw positive whole number, or blank for unlimited.
     */
    fun editWaitlistCapacity(value: String) =
        editDraft { it.copy(waitlistCapacityText = value) }

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
                        campsiteRequired =
                            if (draft.allocations.isEmpty()) {
                                selectedSite() == null
                            } else {
                                !hasEligibleAllocations(
                                    current,
                                )
                            },
                        allocationRequired = draft.allocations.isEmpty(),
                    )
                }

                LotteryStep.REGISTRATION -> {
                    LotterySetupErrors(
                        campsiteRequired = !hasEligibleAllocations(current),
                        schedule =
                            lotteryScheduleErrors(
                                draft,
                                listOf(
                                    LotterySchedulePoint.REGISTRATION_OPEN,
                                    LotterySchedulePoint.REGISTRATION_CLOSE,
                                ),
                            ),
                    )
                }

                LotteryStep.BOOKING -> {
                    LotterySetupErrors(
                        campsiteRequired = !hasEligibleAllocations(current),
                        schedule =
                            lotteryScheduleErrors(
                                draft,
                                listOf(
                                    LotterySchedulePoint.BOOKING_OPEN,
                                    LotterySchedulePoint.BOOKING_CLOSE,
                                ),
                            ),
                    )
                }

                LotteryStep.WINNERS -> {
                    LotterySetupErrors(
                        campsiteRequired = !hasEligibleAllocations(current),
                        winnerCount =
                            parsePositiveCount(
                                draft.maximumWinnersText,
                                Long.MAX_VALUE,
                            ) as? PositiveCountInput.Error,
                        capacity = lotteryCapacityInput(draft) as? PositiveCountInput.Error,
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
                    stepNavigation = StepNavigation.FORWARD,
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
        if (current.draft.step == LotteryStep.BASICS && !current.draft.returnToReview) {
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
                    stepNavigation = StepNavigation.BACK,
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
                stepNavigation = StepNavigation.FORWARD,
                errors = LotterySetupErrors(),
                configuration = null,
            ),
        )
    }

    /** Opens a separate working calendar without changing applied offers.
     * @param campsiteId Owned published campsite, defaulting to the current focus.
     */
    fun beginAllocation(campsiteId: String? = state.value.draft.campsiteId): Boolean {
        if (!canEdit() || state.value.draft.step != LotteryStep.SITE) return false
        val site = choices().firstOrNull { it.id == campsiteId } ?: return false
        val current = state.value
        publish(current.copy(draft = current.draft.copy(campsiteId = site.id)))
        workingAllocation.open(
            site.id,
            YearMonth.now(clock.withZone(ZoneId.of(site.details.timeZone))),
            current.draft.allocations
                .firstOrNull { it.campsiteId == site.id }
                ?.nights
                .orEmpty()
                .toSet(),
        )
        return true
    }

    /** Replaces working nights after an explicit range addition or removal.
     * @param dates Complete working selection; new dates must be free in the loaded month.
     */
    fun selectAllocationDates(dates: Set<LocalDate>) {
        if (exitRequested.value || state.value.discardPrompt) return
        workingAllocation.select(dates)
    }

    /** Toggles a known eligible night, or removes an already-selected invalidated night.
     * @param date Actual visible calendar date.
     */
    fun toggleAllocationDate(date: LocalDate) {
        if (exitRequested.value || state.value.discardPrompt) return
        workingAllocation.toggle(date)
    }

    /** Requests a different visible month while retaining working nights.
     * @param month Actual requested local month.
     */
    fun changeAllocationMonth(month: YearMonth) {
        if (exitRequested.value || state.value.discardPrompt) return
        workingAllocation.changeMonth(month)
    }

    /** Applies a nonempty confirmed eligible set to the parent draft. */
    fun applyAllocation(): Boolean {
        val current = state.value
        if (current.allocation == null || exitRequested.value || current.discardPrompt) return false
        val site = selectedSite()
        if (site == null) {
            workingAllocation.invalidate()
            return false
        }
        val offer = workingAllocation.selectedOffer() ?: return false
        workingAllocation.cancel()
        publish(
            current.copy(
                draft =
                    current.draft.copy(
                        allocations =
                            (current.draft.allocations.filterNot { it.campsiteId == site.id } + offer)
                                .sortedBy { it.campsiteId },
                    ),
                allocation = null,
                errors = LotterySetupErrors(),
                configuration = null,
            ),
        )
        return true
    }

    /** Discards working allocation edits and stops their inventory observation. */
    fun cancelAllocation() {
        workingAllocation.cancel()
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
        workingAllocation.retry()
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
                draft.allocations,
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
            val sites = content.offers.associate { it.campsiteId to campsites.get(it.campsiteId) }
            val owned = content.offers.filter { sites[it.campsiteId]?.ownerId == ownerId }.sortedBy { it.campsiteId }
            currentCoroutineContext().ensureActive()
            val record = content.lottery
            var fresh =
                LotteryDraft(
                    id = record.id,
                    step = LotteryStep.SITE,
                    title = record.title,
                    description = record.description,
                    campsiteId = owned.firstOrNull()?.campsiteId,
                    maximumWinnersText = record.configuration.winnerCount.toString(),
                    allocations = owned,
                    waitlistCapacityText =
                        record.configuration.waitlistCapacity
                            ?.toString()
                            .orEmpty(),
                )
            fresh = withLotterySchedule(fresh, record.configuration)
            if (owned.isNotEmpty()) {
                fresh =
                    fresh.copy(step = LotteryStep.REVIEW)
            }
            baseline = fresh
            val draft = if (retainedInput) state.value.draft else fresh
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

    /** Compares editable facts without treating navigation or allocated identity as unsaved input.
     * @param draft Current or server-baseline form.
     */
    private fun normalized(draft: LotteryDraft): LotteryDraft =
        draft.copy(id = null, step = LotteryStep.BASICS, returnToReview = false, campsiteId = null)

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

    /** Changes one explicit local schedule input.
     * @param point Actual schedule input.
     * @param transform Specific date or time edit.
     */
    private fun editSchedule(
        point: LotterySchedulePoint,
        transform: (LotteryLocalDateTime) -> LotteryLocalDateTime,
    ) {
        editDraft { draft ->
            draft.copy(
                schedule =
                    draft.schedule +
                        (point to transform(draft.schedule[point] ?: LotteryLocalDateTime())),
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
        val allocation = state.value.allocation
        if (exitRequested.value || allocation == null) {
            workingAllocation.cancel()
        } else {
            workingAllocation.resume(selectedSite()?.id, allocation)
        }
    }

    /** Releases the range while retaining a working selection for context refresh. */
    private fun stopCalendar() {
        workingAllocation.pause()
    }

    /** Checks every applied offer against the current confirmed owner choices.
     * @param current Immutable form and choice snapshot.
     */
    private fun hasEligibleAllocations(current: LotterySetupUiState): Boolean {
        val sites = (current.choices as? LotteryCampsiteChoices.Content)?.campsites ?: return false
        return current.draft.allocations.isNotEmpty() &&
            current.draft.allocations.all { offer ->
                sites.any { it.id == offer.campsiteId }
            }
    }

    /** Validates shared details and this parent's applied-allocation prerequisites.
     * @param current Immutable snapshot to validate when review is visible.
     */
    private fun reviewed(current: LotterySetupUiState): LotterySetupUiState {
        if (current.draft.step != LotteryStep.REVIEW) return current.copy(configuration = null)
        val details = lotteryDetailsInput(current.draft, validateWindows)
        val eligible = hasEligibleAllocations(current)
        val errors =
            details.errors.copy(
                campsiteRequired = !eligible,
                allocationRequired = current.draft.allocations.isEmpty(),
            )
        return current.copy(errors = errors, configuration = details.configuration.takeIf { eligible })
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
        savedState["${SETUP_KEY}winners"] = draft.maximumWinnersText
        savedState["${SETUP_KEY}waitlistCapacity"] = draft.waitlistCapacityText
        savedState["${SETUP_KEY}reviewReturn"] = draft.returnToReview
        val previousSites = savedState.get<ArrayList<String>>("${SETUP_KEY}allocationSites").orEmpty()
        for (index in previousSites.indices) {
            savedState.remove<ArrayList<String>>(
                "${SETUP_KEY}allocation.$index.nights",
            )
        }
        savedState["${SETUP_KEY}allocationSites"] = ArrayList(draft.allocations.map { it.campsiteId })
        for ((index, offer) in draft.allocations.withIndex()) {
            savedState["${SETUP_KEY}allocation.$index.nights"] = ArrayList(offer.nights.map(LocalDate::toString))
        }
        for (point in LotterySchedulePoint.entries) {
            savedState["$SETUP_KEY${point.name}.date"] = draft.schedule[point]?.date?.toString()
            savedState["$SETUP_KEY${point.name}.time"] = draft.schedule[point]?.time?.toString()
        }
        val allocation = current.allocation
        savedState["${SETUP_KEY}allocationOpen"] = allocation != null
        savedState["${SETUP_KEY}month"] = allocation?.month?.toString()
        savedState["${SETUP_KEY}workingNights"] =
            allocation?.selected?.map(LocalDate::toString)?.let { ArrayList(it) }
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
                schedule = schedule,
                maximumWinnersText = savedState["${SETUP_KEY}winners"] ?: "",
                waitlistCapacityText = savedState["${SETUP_KEY}waitlistCapacity"] ?: "",
                returnToReview = savedState["${SETUP_KEY}reviewReturn"] ?: false,
                allocations =
                    savedState.get<ArrayList<String>>("${SETUP_KEY}allocationSites").orEmpty().mapIndexed { index, id ->
                        LotteryOffer(id, savedDates("allocation.$index.nights").sorted())
                    },
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
        choiceJob?.cancel()
        workingAllocation.close()
        readJob?.cancel()
        super.onCleared()
    }
}

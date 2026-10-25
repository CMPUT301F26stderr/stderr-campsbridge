package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.resolveDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** Owns local six-step input and the single-site allocation child.
 * @param ownerId Confirmed owner identity used to scope campsite choices.
 * @param campsites Existing published-choice and inventory source.
 * @param clock Initial month in the selected site's zone.
 * @param savedState Primitive input retained across graph recreation.
 * @param validateWindows Reserved F13 contract; null keeps configuration validation pending.
 */
class LotterySetupViewModel(
    private val ownerId: String,
    private val campsites: CampsiteRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val validateWindows: LotteryWindowValidator? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(restoredState())
    val state = mutableState.asStateFlow()
    private val mutableExit = MutableStateFlow(savedState.get<Boolean>("${SETUP_KEY}exit") == true)
    val exitRequested = mutableExit.asStateFlow()

    /** Supplies the selected site's clock month for a native schedule picker with no prior date. */
    fun currentScheduleMonth(): YearMonth? =
        selectedSite()?.let { YearMonth.now(clock.withZone(ZoneId.of(it.details.timeZone))) }

    private var choiceJob: Job? = null
    private var calendarJob: Job? = null
    private var choiceVersion = 0L
    private var calendarVersion = 0L
    private var calendarRange: Triple<String, LocalDate, LocalDate>? = null

    init {
        if (!exitRequested.value) observeChoices()
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
        editDraft { it.copy(campsiteId = id, nights = emptySet()) }
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
        val known = allocation.inventory as? CampsiteInventoryUiState.Content ?: return
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
        if (!exitRequested.value &&
            state.value.choices == LotteryCampsiteChoices.Failure
        ) {
            observeChoices()
        }
    }

    /** Retries the required working-allocation range without changing selection. */
    fun retryAllocation() {
        if (state.value.allocation?.inventory == CampsiteInventoryUiState.Failure) {
            calendarRange = null
            reconcileCalendar()
        }
    }

    /** Requests exit, confirming abandonment only when local input changed. */
    fun requestExit() {
        if (exitRequested.value) return
        val draft = state.value.draft
        val changed =
            draft.title.isNotEmpty() || draft.description.isNotEmpty() ||
                draft.campsiteId != null || draft.schedule.isNotEmpty() ||
                draft.maximumWinnersText.isNotEmpty()
        if (changed) publish(state.value.copy(discardPrompt = true)) else confirmDiscard()
    }

    /** Keeps local input after declining abandonment. */
    fun dismissDiscard() {
        publish(state.value.copy(discardPrompt = false))
    }

    /** Discards this local draft and signals its registered caller to return. */
    fun confirmDiscard() {
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
        !exitRequested.value && !state.value.discardPrompt &&
            state.value.allocation == null

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
                        inventory = CampsiteInventoryUiState.Loading,
                    ),
            ),
        )
        choiceJob =
            viewModelScope.launch {
                try {
                    campsites.observeForOwner(ownerId).collect { records ->
                        if (version != choiceVersion || exitRequested.value) return@collect
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
                    if (version != choiceVersion || exitRequested.value) return@launch
                    stopCalendar()
                    publish(
                        state.value.copy(
                            choices = LotteryCampsiteChoices.Failure,
                            configuration = null,
                            allocation =
                                state.value.allocation?.copy(
                                    inventory = CampsiteInventoryUiState.Loading,
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
                            allocation.copy(inventory = CampsiteInventoryUiState.Loading),
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
                allocation = allocation.copy(inventory = CampsiteInventoryUiState.Loading),
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
                                            inventory = CampsiteInventoryUiState.Content(nights),
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
                                        inventory = CampsiteInventoryUiState.Failure,
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
        return LotterySetupUiState(draft = draft, allocation = allocation)
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
        super.onCleared()
    }
}

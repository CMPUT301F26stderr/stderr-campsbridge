package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.AppDisplayTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Owns organizer details while keeping shared allocations outside the form.
 * @param lotteryId Existing target identity.
 * @param organizerId Acting organizer identity.
 * @param lotteries Saved context and the existing details writer operations.
 * @param clock Time used for the published configuration boundary.
 * @param savedState Primitive raw input retained by this edit graph.
 */
class LotteryDetailsEditViewModel(
    val lotteryId: String,
    private val organizerId: String,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LotteryDetailsEditUiState>(LotteryDetailsEditUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableCompletion =
        MutableStateFlow(
            lotteryId.takeIf {
                savedState.get<Boolean>("${KEY}completed") ==
                    true
            },
        )
    val completion = mutableCompletion.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    private val writer = LotteryDraftWriter(lotteries, clock)
    private var reading: Job? = null
    private var baseline: Lottery? = null
    private var baselineDraft = LotteryDraft(id = lotteryId)
    private var editor: LotteryDetailsEditUiState.Content? = null

    init {
        retry()
    }

    /** Restarts the required saved source without submitting retained input. */
    fun retry() {
        reading?.cancel()
        mutableState.value = LotteryDetailsEditUiState.Loading
        reading =
            viewModelScope.launch {
                try {
                    lotteries.observe(lotteryId).collect { lottery ->
                        if (lottery == null || !available(lottery)) {
                            mutableState.value = LotteryDetailsEditUiState.Unavailable
                        } else {
                            val previous = editor
                            val fresh = lotteryDetailsDraft(lottery)
                            val draft =
                                when {
                                    previous?.isDirty == true -> previous.draft
                                    previous == null && savedState.get<Boolean>("${KEY}dirty") == true -> restore(fresh)
                                    else -> fresh
                                }
                            if (baseline == null || previous?.isDirty == false) {
                                baseline = lottery
                                baselineDraft = fresh
                            }
                            val editable = configurationEditable(lottery)
                            val current =
                                previous ?: LotteryDetailsEditUiState.Content(
                                    lottery,
                                    draft,
                                    editable,
                                    saveFailed = savedState.get<Boolean>("${KEY}attempting") == true,
                                )
                            publish(
                                current.copy(
                                    lottery = lottery,
                                    draft = if (editable) draft else frozen(draft, lottery),
                                    configurationEditable = editable,
                                ),
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = LotteryDetailsEditUiState.Failure
                }
            }
    }

    /** Rechecks the published configuration boundary without writing. */
    fun refreshTime() {
        val current = mutableState.value as? LotteryDetailsEditUiState.Content ?: return
        val editable = configurationEditable(current.lottery)
        if (editable == current.configurationEditable) return
        publish(
            current.copy(
                configurationEditable = editable,
                draft = if (editable) current.draft else frozen(current.draft, current.lottery),
                errors = LotterySetupErrors(),
            ),
        )
    }

    /** Supplies the existing date field's default month in the shared display zone. */
    fun currentScheduleMonth(): YearMonth =
        YearMonth.now(clock.withZone(AppDisplayTime.zone))

    /** Returns one deadline delay for the resumed route, or null when no freeze is pending. */
    fun millisUntilConfigurationFreeze(): Long? {
        val current = mutableState.value as? LotteryDetailsEditUiState.Content ?: return null
        if (current.lottery.publishedAt == null || !configurationEditable(current.lottery)) return null
        val remaining = Duration.between(clock.instant(), current.lottery.configuration.registrationOpensAt)
        return (remaining.toMillis() + if (remaining.nano % 1_000_000 == 0) 0 else 1).coerceAtLeast(1)
    }

    /** Retains raw title spelling.
     * @param value Raw title.
     */
    fun editTitle(value: String) =
        change { it.copy(title = value) }

    /** Retains multiline description text.
     * @param value Raw description.
     */
    fun editDescription(value: String) =
        change { it.copy(description = value) }

    /** Retains a count only while configuration remains editable.
     * @param value Raw winner count.
     */
    fun editWinnerCount(value: String) =
        change(configuration = true) { it.copy(maximumWinnersText = value) }

    /** Retains optional capacity only while configuration remains editable.
     * @param value Raw count, or blank for unlimited.
     */
    fun editWaitlistCapacity(value: String) =
        change(configuration = true) { it.copy(waitlistCapacityText = value) }

    /** Applies an explicit schedule date while configuration remains editable.
     * @param point Fixed schedule field.
     * @param value Chosen local date in the shared display zone.
     */
    fun editScheduleDate(
        point: LotterySchedulePoint,
        value: LocalDate,
    ) =
        change(configuration = true) {
            it.copy(
                schedule =
                    it.schedule + (point to (it.schedule[point] ?: LotteryLocalDateTime()).copy(date = value)),
            )
        }

    /** Applies an explicit schedule time while configuration remains editable.
     * @param point Fixed schedule field.
     * @param value Chosen local time in the shared display zone.
     */
    fun editScheduleTime(
        point: LotterySchedulePoint,
        value: LocalTime,
    ) =
        change(configuration = true) {
            it.copy(
                schedule =
                    it.schedule + (point to (it.schedule[point] ?: LotteryLocalDateTime()).copy(time = value)),
            )
        }

    /** Reviews current facts and explicitly saves only the permitted organizer details. */
    fun save() {
        refreshTime()
        val current = mutableState.value as? LotteryDetailsEditUiState.Content ?: return
        if (current.isSaving) return
        val input = lotteryDetailsInput(current.draft, savedConfiguration = current.lottery.configuration)
        val configuration = input.configuration
        if (configuration == null) {
            publish(current.copy(errors = input.errors))
            return
        }
        val request =
            LotterySaveRequest(
                lotteryId,
                organizerId,
                current.draft.title.trim(),
                current.draft.description,
                configuration,
                emptyList(),
                allowCreate = false,
                scope = LotterySaveScope.DETAILS,
            )
        savedState["${KEY}attempting"] = true
        publish(current.copy(isSaving = true, saveFailed = false, contextChanged = false))
        viewModelScope.launch {
            try {
                val fresh = lotteries.get(lotteryId)
                currentCoroutineContext().ensureActive()
                if (fresh == null || !available(fresh)) {
                    mutableState.value = LotteryDetailsEditUiState.Unavailable
                    return@launch
                }
                if (!sameContext(fresh, checkNotNull(baseline)) ||
                    (!configurationEditable(fresh) && request.configuration != fresh.configuration)
                ) {
                    baseline = fresh
                    baselineDraft = lotteryDetailsDraft(fresh)
                    val editable = configurationEditable(fresh)
                    val refreshed =
                        current.copy(
                            lottery = fresh,
                            draft = frozen(current.draft, fresh),
                            configurationEditable = editable,
                            isSaving = false,
                            contextChanged = true,
                        )
                    publishOperationResult(refreshed)
                    savedState["${KEY}attempting"] = false
                    return@launch
                }
                when (writer.save(request)) {
                    is LotterySaveResult.Saved -> {
                        finish(request)
                    }

                    LotterySaveResult.Unavailable, is LotterySaveResult.Published -> {
                        mutableState.value =
                            LotteryDetailsEditUiState.Unavailable
                    }

                    is LotterySaveResult.Failed -> {
                        reconcile(request)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failSave()
            } finally {
                if (viewModelScope.isActive && editor?.isSaving == true) {
                    val idle = checkNotNull(editor).copy(isSaving = false)
                    if (mutableState.value is LotteryDetailsEditUiState.Content) publish(idle) else editor = idle
                }
            }
        }
    }

    /** Consumes a confirmed details-save navigation result once. */
    fun acknowledgeCompletion() {
        savedState["${KEY}completed"] = false
        mutableCompletion.value = null
    }

    /** Uses one discard confirmation when raw details would otherwise be lost. */
    fun requestExit() {
        if (editor?.isSaving == true) return
        if (editor?.isDirty == true) mutableDiscard.value = true else mutableExit.value = true
    }

    /** Keeps the raw form when discard confirmation is dismissed. */
    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    /** Abandons only local details after confirmation. */
    fun confirmDiscard() {
        if (editor?.isSaving == true) return
        mutableDiscard.value = false
        mutableExit.value = true
    }

    /** Consumes an explicit return to the saved reader. */
    fun acknowledgeExit() {
        mutableExit.value = false
    }

    /** Compares an uncertain operation with a saved record, never a cached stream emission.
     * @param request Frozen fields from the attempted explicit update.
     */
    private suspend fun reconcile(request: LotterySaveRequest) {
        val actual =
            try {
                lotteries.get(lotteryId).also { currentCoroutineContext().ensureActive() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        if (actual != null && available(actual) && actual.title == request.title &&
            actual.description == request.description && actual.configuration == request.configuration
        ) {
            finish(request)
        } else {
            failSave()
        }
    }

    /** Retains failed input without replacing an unavailable or failed required source. */
    private fun failSave() {
        val failed = editor?.copy(isSaving = false, saveFailed = true) ?: return
        publishOperationResult(failed)
    }

    /** Keeps action results without overriding the required record's read outcome.
     * @param result Local input and operation feedback after an explicit action.
     */
    private fun publishOperationResult(result: LotteryDetailsEditUiState.Content) {
        if (mutableState.value is LotteryDetailsEditUiState.Content) publish(result) else editor = result
    }

    /** Publishes confirmed field success while retaining raw spelling until navigation completes.
     * @param request Acknowledged or server-confirmed details request.
     */
    private fun finish(request: LotterySaveRequest) {
        val current = checkNotNull(editor)
        val confirmed =
            current.lottery.copy(
                title = request.title,
                description = request.description,
                configuration = request.configuration,
            )
        baseline = confirmed
        baselineDraft = current.draft
        savedState["${KEY}attempting"] = false
        savedState["${KEY}completed"] = true
        val completed = current.copy(lottery = confirmed, isSaving = false, saveFailed = false, contextChanged = false)
        publishOperationResult(completed)
        mutableCompletion.value = lotteryId
    }

    /** Permits only the actual organizer of an uncancelled saved lottery.
     * @param lottery Current saved context.
     */
    private fun available(lottery: Lottery): Boolean =
        lottery.id == lotteryId && lottery.ownerId == organizerId &&
            organizerId in lottery.participantOwnerIds && lottery.cancellation == null

    /** Keeps drafts editable while published shared terms freeze at the original opening.
     * @param lottery Current saved context.
     */
    private fun configurationEditable(lottery: Lottery): Boolean =
        lottery.publishedAt == null || clock.instant() < lottery.configuration.registrationOpensAt

    /** Compares only facts affecting this editor, excluding changing waitlist/draw counters.
     * @param fresh Current server-read context.
     * @param previous Baseline against which local edits were made.
     */
    private fun sameContext(
        fresh: Lottery,
        previous: Lottery,
    ): Boolean =
        fresh.id == previous.id && fresh.ownerId == previous.ownerId &&
            fresh.participantOwnerIds == previous.participantOwnerIds && fresh.cancellation == previous.cancellation &&
            fresh.publishedAt == previous.publishedAt && fresh.title == previous.title &&
            fresh.description == previous.description &&
            fresh.configuration == previous.configuration

    /** Refreshes configured facts while preserving typed title and description.
     * @param draft Raw descriptive input.
     * @param lottery Saved configuration that must remain unchanged after registration opens.
     */
    private fun frozen(
        draft: LotteryDraft,
        lottery: Lottery,
    ): LotteryDraft =
        withLotterySchedule(
            draft.copy(
                maximumWinnersText = lottery.configuration.winnerCount.toString(),
                waitlistCapacityText =
                    lottery.configuration.waitlistCapacity
                        ?.toString()
                        .orEmpty(),
            ),
            lottery.configuration,
        )

    /** Applies raw local edits only while the corresponding fields remain available.
     * @param configuration Whether the change affects shared frozen terms.
     * @param transform Requested raw field update.
     */
    private fun change(
        configuration: Boolean = false,
        transform: (LotteryDraft) -> LotteryDraft,
    ) {
        refreshTime()
        val current = mutableState.value as? LotteryDetailsEditUiState.Content ?: return
        if (current.isSaving || (configuration && !current.configurationEditable)) return
        publish(
            current.copy(
                draft = transform(current.draft),
                errors = LotterySetupErrors(),
                saveFailed = false,
                contextChanged = false,
            ),
        )
    }

    /** Saves only raw primitives and one immutable UI projection.
     * @param value Current local input, confirmed context and explicit operation outcome.
     */
    private fun publish(value: LotteryDetailsEditUiState.Content) {
        val next = value.copy(isDirty = value.draft != baselineDraft)
        savedState["${KEY}present"] = true
        savedState["${KEY}dirty"] = next.isDirty
        savedState["${KEY}title"] = next.draft.title
        savedState["${KEY}description"] = next.draft.description
        savedState["${KEY}winners"] = next.draft.maximumWinnersText
        savedState["${KEY}capacity"] = next.draft.waitlistCapacityText
        for (point in LotterySchedulePoint.entries) {
            savedState["$KEY${point.name}.date"] =
                next.draft.schedule[point]
                    ?.date
                    ?.toString()
            savedState["$KEY${point.name}.time"] =
                next.draft.schedule[point]
                    ?.time
                    ?.toString()
        }
        editor = next
        mutableState.value = next
    }

    /** Restores local details over a confirmed identity without replaying any save.
     * @param initial Saved input used when no local draft was retained.
     */
    private fun restore(initial: LotteryDraft): LotteryDraft {
        if (savedState.get<Boolean>("${KEY}present") != true) return initial
        return initial.copy(
            title = savedState.get<String>("${KEY}title").orEmpty(),
            description = savedState.get<String>("${KEY}description").orEmpty(),
            maximumWinnersText = savedState.get<String>("${KEY}winners").orEmpty(),
            waitlistCapacityText = savedState.get<String>("${KEY}capacity").orEmpty(),
            schedule =
                LotterySchedulePoint.entries.associateWith { point ->
                    LotteryLocalDateTime(
                        savedState.get<String>("$KEY${point.name}.date")?.let(LocalDate::parse),
                        savedState.get<String>("$KEY${point.name}.time")?.let(LocalTime::parse),
                    )
                },
        )
    }

    /** Primitive keys owned by this saved organizer editor. */
    private companion object {
        const val KEY = "lottery-details:"
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Independent saved-campsite editor; one shared controller owns inventory reads and explicit writes.
 * @param ownerId Active listing owner.
 * @param campsiteId Actual saved identity from navigation.
 * @param campsites Parent read and inventory operations.
 * @param clock Initial displayed month in the saved listing's zone.
 * @param savedState Primitive editor restoration store.
 * @param remainingEdits Reserved F08 dependency, absent until its implementation is reviewed.
 */
class AvailabilityViewModel(
    private val ownerId: String,
    private val campsiteId: String,
    private val campsites: CampsiteRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val remainingEdits: AvailabilityDifference? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AvailabilityUiState>(AvailabilityUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableExitRequested = MutableStateFlow(false)
    val exitRequested = mutableExitRequested.asStateFlow()
    private val mutableDiscardPrompt = MutableStateFlow(false)
    val discardPrompt = mutableDiscardPrompt.asStateFlow()
    private var calendar: CampsiteAvailabilityController? = null

    init {
        load()
    }

    /** Retries a failed parent read. */
    fun retry() {
        if (state.value == AvailabilityUiState.Failure) load()
    }

    /** Selects only known unoccupied dates without creating a requested write.
     * @param dates Requested working selection.
     */
    fun selectDates(dates: Set<LocalDate>) {
        val current = editable() ?: return
        val owner = calendar ?: return
        publish(current.copy(selectedDates = dates.filter(owner::canEdit).toSet(), saved = false))
    }

    /** Changes the visible range while retaining requested work from other months.
     * @param month Requested local month.
     */
    fun changeMonth(month: YearMonth) {
        val current = editable() ?: return
        val owner = calendar ?: return
        if (owner.state.value.month == month) return
        owner.changeMonth(month)
        publish(current.copy(availability = owner.state.value, selectedDates = emptySet()))
    }

    /** Converts the working selection into explicit desired offered values.
     * @param offered True to offer; false to withdraw without cancelling reservations.
     */
    fun setRequestedOffered(offered: Boolean) {
        val current = editable() ?: return
        val owner = calendar ?: return
        if (current.selectedDates.isEmpty()) return
        owner.setRequestedOffered(current.selectedDates, offered)
        publish(
            current.copy(
                availability = owner.state.value,
                selectedDates = emptySet(),
                saved = false,
            ),
        )
    }

    /** Saves only explicit requested changes; no writes run during initialization. */
    fun save() {
        val current = editable() ?: return
        val owner = calendar ?: return
        if (owner.state.value.requested
                .isEmpty() || owner.state.value.pendingEdits == null
        ) {
            return
        }
        publish(current.copy(saved = false))
        // Start immediately so the shared owner's save guard precedes another user action.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val success = owner.save()
            val latest = state.value as? AvailabilityUiState.Content ?: return@launch
            publish(
                latest.copy(
                    availability = owner.state.value,
                    selectedDates = emptySet(),
                    saved = success,
                ),
            )
        }
    }

    /** Retries a failed visible-month reader without replacing the parent. */
    fun retryCalendar() {
        if (editable() != null) calendar?.retryCalendar()
    }

    /** Returns immediately when clean, otherwise asks before abandoning unresolved changes. */
    fun requestExit() {
        if (calendar?.state?.value?.isSaving == true || exitRequested.value) return
        val current = state.value as? AvailabilityUiState.Content
        val dirty =
            if (current != null) {
                current.availability.requested.isNotEmpty() || current.selectedDates.isNotEmpty()
            } else {
                state.value != AvailabilityUiState.Unavailable &&
                    (
                        CampsiteAvailabilityController.hasSavedRequests(
                            savedState,
                            "availability.",
                        ) ||
                            readSelection().isNotEmpty()
                    )
            }
        if (dirty) {
            mutableDiscardPrompt.value = true
        } else {
            mutableExitRequested.value = true
        }
    }

    /** Keeps the current editor and its requested dates. */
    fun dismissDiscard() {
        if (calendar?.state?.value?.isSaving == true) return
        mutableDiscardPrompt.value = false
    }

    /** Abandons local intent without attempting to undo acknowledged inventory writes. */
    fun confirmDiscard() {
        if (!discardPrompt.value || calendar?.state?.value?.isSaving == true) return
        val owner = calendar
        if (owner != null) {
            owner.discard()
        } else {
            CampsiteAvailabilityController.discardSavedRequests(savedState, "availability.")
        }
        val current = state.value as? AvailabilityUiState.Content
        if (current != null) {
            publish(
                current.copy(
                    availability = checkNotNull(calendar).state.value,
                    selectedDates = emptySet(),
                ),
            )
        }
        savedState["availability.selection"] = arrayListOf<String>()
        mutableDiscardPrompt.value = false
        mutableExitRequested.value = true
    }

    /** Resolves one authoritative parent before opening its zone-based range; retry is read-only. */
    private fun load() {
        mutableState.value = AvailabilityUiState.Loading
        viewModelScope.launch {
            try {
                val site = campsites.get(campsiteId)
                currentCoroutineContext().ensureActive()
                if (exitRequested.value) return@launch
                if (site == null || site.id != campsiteId || site.ownerId != ownerId ||
                    site.removal != null
                ) {
                    mutableState.value = AvailabilityUiState.Unavailable
                    return@launch
                }
                val owner =
                    CampsiteAvailabilityController(
                        ownerId,
                        campsiteId,
                        campsites,
                        YearMonth.now(clock.withZone(ZoneId.of(site.details.timeZone))),
                        savedState,
                        "availability.",
                        viewModelScope,
                        remainingEdits,
                    )
                calendar = owner
                publish(AvailabilityUiState.Content(site, owner.state.value, readSelection()))
                owner.state.collect { value ->
                    val current = state.value as? AvailabilityUiState.Content ?: return@collect
                    val selected =
                        if (value.inventory is CampsiteInventoryUiState.Content &&
                            !value.isSaving
                        ) {
                            current.selectedDates.filter(owner::canEdit).toSet()
                        } else {
                            current.selectedDates
                        }
                    publish(current.copy(availability = value, selectedDates = selected))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                calendar?.close()
                calendar = null
                mutableState.value = AvailabilityUiState.Failure
            }
        }
    }

    /** Rejects actions during save, discard confirmation or consumed route exit. */
    private fun editable(): AvailabilityUiState.Content? {
        val current = state.value as? AvailabilityUiState.Content ?: return null
        return current.takeUnless {
            calendar?.state?.value?.isSaving == true || discardPrompt.value ||
                exitRequested.value
        }
    }

    /** Restores only primitive working dates; the shared controller owns requested values/month. */
    private fun readSelection(): Set<LocalDate> =
        savedState
            .get<ArrayList<String>>(
                "availability.selection",
            ).orEmpty()
            .map(LocalDate::parse)
            .toSet()

    /** Stores the working selection without duplicating the controller's requested-write state.
     * @param value Next editor state.
     */
    private fun publish(value: AvailabilityUiState.Content) {
        savedState["availability.selection"] =
            ArrayList(value.selectedDates.map(LocalDate::toString))
        mutableState.value = value
    }

    /** Releases the editor's sole range reader; pending writes are cancelled by the ViewModel scope. */
    override fun onCleared() {
        calendar?.close()
        super.onCleared()
    }
}

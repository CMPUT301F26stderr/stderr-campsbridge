package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Requested-versus-known offered values; only the small F08 function body is teammate-owned. */
typealias AvailabilityDifference = (
    requested: Map<LocalDate, Boolean>,
    confirmed: Map<LocalDate, Boolean>,
) -> Map<LocalDate, Boolean>

/** Distinguishes missing comparison integration from a failed explicit inventory write. */
enum class AvailabilitySaveFailure { COMPARISON_PENDING, WRITE }

/** Month display and explicit local inventory work, independent of campsite details/photo.
 * @param month Visible local month.
 * @param inventory Successfully loaded records, loading or failure.
 * @param requested Desired edited-date values; these are not acknowledged writes.
 * @param pendingEdits Known differences plus unresolved attempts; null means F08 is pending.
 * @param isSaving Whether a frozen set of singleton writes is in progress.
 * @param failure Last explicit save failure.
 */
data class CampsiteAvailabilityState(
    val month: YearMonth,
    val inventory: CampsiteInventoryUiState = CampsiteInventoryUiState.Loading,
    val requested: Map<LocalDate, Boolean> = emptyMap(),
    val pendingEdits: Map<LocalDate, Boolean>? = null,
    val isSaving: Boolean = false,
    val failure: AvailabilitySaveFailure? = null,
)

/** Owns one campsite's range and explicit saves. Baselines stay transient; restored requests
 * replay idempotently unless a fresh loaded range confirms them.
 * @param ownerId Active listing owner.
 * @param campsiteId Confirmed saved parent, or null while setup remains local.
 * @param campsites Range reads and transaction-backed inventory operations.
 * @param initialMonth Initial month before restoration.
 * @param savedState Primitive restoration store owned by the containing feature.
 * @param stateKey Prefix separating this controller's fields from the containing draft.
 * @param parentScope Containing ViewModel lifetime and dispatcher.
 * @param remainingEdits Optional F08 integration; absence never authorizes saving requested dates.
 */
class CampsiteAvailabilityController(
    private val ownerId: String,
    campsiteId: String?,
    private val campsites: CampsiteRepository,
    initialMonth: YearMonth,
    private val savedState: SavedStateHandle,
    private val stateKey: String,
    parentScope: CoroutineScope,
    private val remainingEdits: AvailabilityDifference? = null,
) : AutoCloseable {
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private var targetId = campsiteId
    private var reader: Job? = null
    private val known = mutableMapOf<LocalDate, Boolean>()
    private val unacknowledged =
        savedState
            .get<ArrayList<String>>("${stateKey}unacknowledged")
            .orEmpty()
            .map(LocalDate::parse)
            .toMutableSet()
    private val mutableState =
        MutableStateFlow(
            CampsiteAvailabilityState(
                month =
                    savedState.get<String>("${stateKey}month")?.let(YearMonth::parse)
                        ?: initialMonth,
                requested = readRequestedDates(),
                failure = if (unacknowledged.isEmpty()) null else AvailabilitySaveFailure.WRITE,
            ),
        )
    val state = mutableState.asStateFlow()

    init {
        publish(state.value)
        observeMonth()
    }

    /** Changes the visible range without abandoning requested values from other months.
     * @param month Requested visible month.
     */
    fun changeMonth(month: YearMonth) {
        if (!scope.isActive || state.value.isSaving || state.value.month == month) return
        publish(state.value.copy(month = month))
        observeMonth()
    }

    /** Toggles only known, unoccupied inventory without a persistence side effect.
     * @param date Requested local night.
     */
    fun toggleDate(date: LocalDate) {
        val current = state.value
        if (!scope.isActive || current.isSaving) return
        val inventory = current.inventory as? CampsiteInventoryUiState.Content ?: return
        if (targetId != null && YearMonth.from(date) != current.month) return
        val night = inventory.nights.find { it.date == date }
        if (night?.reservationId != null) return
        if (date !in known) known[date] = night?.isOffered ?: false
        val offered = current.requested[date] ?: known.getValue(date)
        publish(current.copy(requested = current.requested + (date to !offered)))
    }

    /** Retries the visible range without fetching or creating another parent. */
    fun retryCalendar() {
        if (scope.isActive && !state.value.isSaving &&
            state.value.inventory == CampsiteInventoryUiState.Failure
        ) {
            observeMonth()
        }
    }

    /** Binds the actual parent after creation, never an invented upload/write identity.
     * @param id Confirmed saved campsite ID.
     */
    fun bindSavedTarget(id: String) {
        if (!scope.isActive || state.value.isSaving) return
        require(
            targetId == null || targetId == id,
        ) { "An availability owner cannot change campsite" }
        if (targetId == id) return
        targetId = id
        known.clear()
        observeMonth()
    }

    /** Saves frozen differences in date order, retaining every unacknowledged date on failure. */
    suspend fun save(): Boolean {
        val current = state.value
        if (!scope.isActive || current.isSaving) return false
        val work = current.pendingEdits
        if (work == null) {
            publish(current.copy(failure = AvailabilitySaveFailure.COMPARISON_PENDING))
            return false
        }
        val id = targetId
        if (work.isNotEmpty() && id == null) {
            publish(current.copy(failure = AvailabilitySaveFailure.WRITE))
            return false
        }
        publish(current.copy(isSaving = true, failure = null))
        try {
            for ((date, offered) in work.toSortedMap()) {
                unacknowledged.add(date)
                publish(state.value)
                campsites.setOffered(checkNotNull(id), ownerId, setOf(date), offered)
                currentCoroutineContext().ensureActive()
                unacknowledged.remove(date)
                known[date] = offered
                publish(state.value.copy(requested = state.value.requested - date))
            }
            publish(state.value.copy(requested = emptyMap()))
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            publish(state.value.copy(failure = AvailabilitySaveFailure.WRITE))
            return false
        } finally {
            if (scope.isActive) publish(state.value.copy(isSaving = false))
        }
    }

    /** Abandons local requested changes without attempting to undo already confirmed writes. */
    fun discard() {
        if (!scope.isActive || state.value.isSaving) return
        publish(state.value.copy(requested = emptyMap(), failure = null))
    }

    /** Releases the range reader and rejects later callbacks/actions. */
    override fun close() {
        scope.cancel()
    }

    /** Replaces only the current saved-parent range; local forms have known empty inventory. */
    private fun observeMonth() {
        reader?.cancel()
        known.clear()
        val id = targetId
        if (id == null) {
            publish(state.value.copy(inventory = CampsiteInventoryUiState.Content(emptyList())))
            return
        }
        val month = state.value.month
        publish(state.value.copy(inventory = CampsiteInventoryUiState.Loading))
        reader =
            scope.launch {
                try {
                    campsites
                        .observeCalendar(id, month.atDay(1), month.plusMonths(1).atDay(1))
                        .collect { nights ->
                            currentCoroutineContext().ensureActive()
                            if (targetId != id || state.value.month != month) return@collect
                            val records = nights.associateBy { it.date }
                            for (date in state.value.requested.keys) {
                                if (YearMonth.from(date) == month && date !in unacknowledged) {
                                    known[date] = records[date]?.isOffered ?: false
                                }
                            }
                            publish(
                                state.value.copy(
                                    inventory = CampsiteInventoryUiState.Content(nights),
                                ),
                            )
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (targetId == id && state.value.month == month) {
                        known.clear()
                        publish(state.value.copy(inventory = CampsiteInventoryUiState.Failure))
                    }
                }
            }
    }

    /** Compares only known baselines; unresolved attempts and unknown values remain explicit work.
     * @param requested Desired local values, frozen by save before suspension.
     */
    private fun pendingChanges(requested: Map<LocalDate, Boolean>): Map<LocalDate, Boolean>? {
        if (requested.isEmpty()) return emptyMap()
        val compare = remainingEdits ?: return null
        val comparable = requested.filterKeys { it in known && it !in unacknowledged }
        val differences =
            if (comparable.isEmpty()) {
                emptyMap()
            } else {
                compare(comparable, known.filterKeys { it in comparable })
            }
        return differences + requested.filterKeys { it !in known || it in unacknowledged }
    }

    /** Publishes derived pending work and persists only relevant edited-date recovery facts.
     * @param value Next local/display state, never a claimed server acknowledgement.
     */
    private fun publish(value: CampsiteAvailabilityState) {
        known.keys.retainAll(value.requested.keys)
        unacknowledged.retainAll(value.requested.keys)
        writeRequestedDates(value.requested)
        savedState["${stateKey}unacknowledged"] = ArrayList(unacknowledged.map(LocalDate::toString))
        savedState["${stateKey}month"] = value.month.toString()
        mutableState.value = value.copy(pendingEdits = pendingChanges(value.requested))
    }

    /** Restores requested primitive values without treating missing inventory as confirmed false. */
    private fun readRequestedDates(): Map<LocalDate, Boolean> {
        val dates = savedState.get<ArrayList<String>>("${stateKey}requested.dates").orEmpty()
        val offered =
            savedState.get<BooleanArray>("${stateKey}requested.offered") ?: booleanArrayOf()
        return dates.zip(offered.toList()).associate { (date, value) ->
            LocalDate.parse(date) to
                value
        }
    }

    /** Stores only small primitive date/value pairs, never SDK snapshots or listeners.
     * @param values Requested date values owned by this controller.
     */
    private fun writeRequestedDates(values: Map<LocalDate, Boolean>) {
        savedState["${stateKey}requested.dates"] = ArrayList(values.keys.map(LocalDate::toString))
        savedState["${stateKey}requested.offered"] = values.values.toBooleanArray()
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Owns a temporary offered-night selection and its required inventory range.
 * @param campsites Reads inventory without changing globally offered nights.
 * @param parentScope Parent editor's lifetime and dispatcher.
 * @param onChanged Projects working state into the parent UI; applied offers stay with that parent.
 */
class LotteryAllocationController(
    private val campsites: CampsiteRepository,
    parentScope: CoroutineScope,
    private val onChanged: (LotteryAllocationUiState?) -> Unit,
) : AutoCloseable {
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private var campsiteId: String? = null
    private var range: Triple<String, LocalDate, LocalDate>? = null
    private var generation = 0L
    private var reading: Job? = null
    var state: LotteryAllocationUiState? = null
        private set

    /** Opens a fresh working copy without modifying the caller's applied set.
     * @param id Confirmed eligible campsite identity.
     * @param month Initial visible month in the campsite's timezone.
     * @param selected Previously applied nights for this campsite.
     */
    fun open(
        id: String,
        month: YearMonth,
        selected: Set<LocalDate>,
    ) {
        stopReading()
        campsiteId = id
        publish(LotteryAllocationUiState(month, selected.toSet()))
        reconcile()
    }

    /** Reattaches a restored working copy only after the parent resolves its current context.
     * @param id Confirmed eligible identity, or null while its required context is unavailable.
     * @param restored Parent's retained working input; no inventory result is trusted on restoration.
     */
    fun resume(
        id: String?,
        restored: LotteryAllocationUiState,
    ) {
        val current = state
        if (current == null || campsiteId != id || current.month != restored.month ||
            current.selected != restored.selected
        ) {
            stopReading()
            campsiteId = id
            publish(restored.copy(inventory = CalendarInventoryRead.Loading))
        }
        reconcile()
    }

    /** Replaces working dates after explicit range selection; new nights must be known free.
     * @param dates Complete working set, including any selected off-screen nights.
     */
    fun select(dates: Set<LocalDate>) {
        val current = state ?: return
        val known = current.inventory as? CalendarInventoryRead.Content ?: return
        val added = dates - current.selected
        if (added.any { date ->
                YearMonth.from(date) != current.month ||
                    known.nights.none { it.date == date && it.isFree }
            }
        ) {
            return
        }
        publish(current.copy(selected = dates.toSet(), invalidSelection = false))
        reconcile()
    }

    /** Toggles a visible known night, including removal of a selected night that became booked.
     * @param date Visible local campsite date.
     */
    fun toggle(date: LocalDate) {
        val current = state ?: return
        val known = current.inventory as? CalendarInventoryRead.Content ?: return
        if (YearMonth.from(date) != current.month) return
        if (date !in current.selected && known.nights.none { it.date == date && it.isFree }) return
        select(if (date in current.selected) current.selected - date else current.selected + date)
    }

    /** Changes the visible month while retaining selected nights in other months.
     * @param month Requested local month.
     */
    fun changeMonth(month: YearMonth) {
        val current = state ?: return
        if (month == current.month) return
        publish(current.copy(month = month))
        reconcile()
    }

    /** Returns a confirmed nonempty offer for the parent to apply, or exposes selection feedback. */
    fun selectedOffer(): LotteryOffer? {
        val current = state ?: return null
        val id = campsiteId
        if (id == null || !current.canApply) {
            invalidate()
            return null
        }
        return LotteryOffer(id, current.selected.sorted())
    }

    /** Reports an Apply action whose current parent context no longer permits this selection. */
    fun invalidate() {
        state?.let { publish(it.copy(invalidSelection = true)) }
    }

    /** Retries only a failed required range, retaining every working night. */
    fun retry() {
        if (state?.inventory != CalendarInventoryRead.Failure) return
        stopReading()
        reconcile()
    }

    /** Releases inventory while the parent refreshes required campsite context. */
    fun pause() {
        stopReading()
        state?.let { publish(it.copy(inventory = CalendarInventoryRead.Loading)) }
    }

    /** Discards the temporary selection without changing any applied offers or inventory. */
    fun cancel() {
        stopReading()
        campsiteId = null
        // The parent may still hold restored input before campsite context has loaded here.
        state = null
        onChanged(null)
    }

    /** Cancels only this controller's work and preserves its last snapshot for the disposed owner. */
    override fun close() {
        generation++
        owner.cancel()
    }

    /** Observes one range covering the visible month and every selected night. */
    private fun reconcile() {
        val current = state ?: return
        val id = campsiteId ?: return
        val first = minOf(current.month, current.selected.minOrNull()?.let(YearMonth::from) ?: current.month)
        val last = maxOf(current.month, current.selected.maxOrNull()?.let(YearMonth::from) ?: current.month)
        val requested = Triple(id, first.atDay(1), last.plusMonths(1).atDay(1))
        if (range == requested) return
        stopReading()
        range = requested
        val version = generation
        publish(current.copy(inventory = CalendarInventoryRead.Loading))
        reading =
            scope.launch {
                try {
                    campsites.observeCalendar(requested.first, requested.second, requested.third).collect { nights ->
                        if (version != generation) return@collect
                        state?.let { publish(it.copy(inventory = CalendarInventoryRead.Content(nights))) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version != generation) return@launch
                    state?.let { publish(it.copy(inventory = CalendarInventoryRead.Failure)) }
                }
            }
    }

    /** Invalidates queued old-range results before cancelling the listener. */
    private fun stopReading() {
        generation++
        reading?.cancel()
        reading = null
        range = null
    }

    /** Publishes a single immutable working snapshot to the parent UI projection.
     * @param value Updated temporary selection or null after cancellation.
     */
    private fun publish(value: LotteryAllocationUiState?) {
        if (state == value) return
        state = value
        onChanged(value)
    }
}

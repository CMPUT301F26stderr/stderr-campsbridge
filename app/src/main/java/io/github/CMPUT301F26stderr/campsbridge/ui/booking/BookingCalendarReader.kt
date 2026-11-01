package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Independent inventory outcomes; null means that range has not been requested.
 * @param visible Displayed-month outcome, or null while hidden.
 * @param selected Selected-stay or checkout-support outcome, or null before selection.
 */
data class BookingCalendarReadState(
    val visible: CalendarInventoryRead? = null,
    val selected: CalendarInventoryRead? = null,
)

/** Owns replaceable inventory reads for one fixed campsite, without writes or draft mutation.
 * @param campsiteId Fixed saved campsite identity.
 * @param campsites Half-open inventory source.
 * @param scope Containing feature's coroutine lifetime.
 */
internal class BookingCalendarReader(
    campsiteId: String,
    campsites: CampsiteRepository,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(BookingCalendarReadState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private val visibleQuery = Query(campsiteId, campsites, scope, ::publish)

    /** Reconciles visible inventory; selection reads are implemented separately.
     * @param visibleMonth Displayed month, or null while hidden.
     * @param checkIn Inclusive arrival, or null before selection.
     * @param checkOut Exclusive departure, or null before selection is complete.
     */
    @Suppress("UNUSED_PARAMETER")
    fun update(
        visibleMonth: YearMonth?,
        checkIn: LocalDate? = null,
        checkOut: LocalDate? = null,
    ) {
        if (closed || !scope.isActive) return
        val range = visibleMonth?.let { DateRange(it.atDay(1), it.plusMonths(1).atDay(1)) }
        val pending = visibleQuery.install(range)
        publish()
        pending?.start()
    }

    /** Declares the visible-source retry action. */
    fun retryVisible() =
        Unit

    /** Declares the selected-source retry action. */
    fun retrySelection() =
        Unit

    /** Releases inventory without permanently retiring this parent-owned reader. */
    fun stop() {
        update(null)
    }

    /** Cancels queued/live work and rejects later actions and callbacks. */
    override fun close() {
        if (closed) return
        closed = true
        visibleQuery.install(null)
        mutableState.value = BookingCalendarReadState()
    }

    /** Publishes only the active source slot's accepted outcome. */
    private fun publish() {
        if (!closed) mutableState.value = BookingCalendarReadState(visible = visibleQuery.result)
    }

    /** One exact half-open query key.
     * @param start Inclusive first occupied date.
     * @param endExclusive Exclusive date after the last queried night.
     */
    private data class DateRange(
        val start: LocalDate,
        val endExclusive: LocalDate,
    )

    /** Replaceable source slot with a generation guard against retired emissions and failures.
     * @param campsiteId Fixed saved campsite identity.
     * @param campsites Source of confirmed half-open inventory.
     * @param scope Containing feature's cancellable lifetime.
     * @param onChanged Publishes an accepted source result.
     */
    private class Query(
        private val campsiteId: String,
        private val campsites: CampsiteRepository,
        private val scope: CoroutineScope,
        private val onChanged: () -> Unit,
    ) {
        var range: DateRange? = null
            private set
        var result: CalendarInventoryRead? = null
            private set
        private var job: Job? = null
        private var version = 0L

        /** Retires the old slot and prepares work without collecting during reconciliation.
         * @param range Exact next range, or null to release this slot.
         * @param force Allows explicit retry of the same failed range.
         * @return Lazy collection to start after the caller has installed all scopes.
         */
        fun install(
            range: DateRange?,
            force: Boolean = false,
        ): Job? {
            if (!force && range == this.range) return null
            val token = ++version
            job?.cancel()
            this.range = range
            result = if (range == null) null else CalendarInventoryRead.Loading
            if (range == null) {
                job = null
                return null
            }
            val pending =
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        campsites
                            .observeCalendar(campsiteId, range.start, range.endExclusive)
                            .collect { nights ->
                                currentCoroutineContext().ensureActive()
                                if (version != token) return@collect
                                result = CalendarInventoryRead.Content(nights)
                                onChanged()
                            }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (version == token) {
                            result = CalendarInventoryRead.Failure
                            onChanged()
                        }
                    }
                }
            job = pending
            return pending
        }
    }
}

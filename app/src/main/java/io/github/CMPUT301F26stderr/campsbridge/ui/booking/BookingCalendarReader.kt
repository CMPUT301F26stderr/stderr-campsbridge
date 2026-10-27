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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Independent displayed-month and selected-stay outcomes; null means that scope is not requested.
 * @param visible Actual displayed month's source, or null while the dates page is hidden.
 * @param selected Stay or pending departure's preceding night; shares a covering month.
 */
data class BookingCalendarReadState(
    val visible: CalendarInventoryRead? = null,
    val selected: CalendarInventoryRead? = null,
)

/** Reads a visible month and one stay range or supporting night; owns no draft or eligibility.
 * The parent invokes actions on its state dispatcher and closes this reader with its lifetime.
 * @param campsiteId Fixed actual saved campsite identity.
 * @param campsites Half-open inventory observations.
 * @param scope Parent-owned cancellable coroutine lifetime.
 */
internal class BookingCalendarReader(
    campsiteId: String,
    campsites: CampsiteRepository,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(BookingCalendarReadState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private var selectionUsesVisible = false
    private val visibleQuery = Query(campsiteId, campsites, scope, ::publish)
    private val selectedQuery = Query(campsiteId, campsites, scope, ::publish)

    /** Reconciles display and selection scopes without restarting identical readers.
     * @param visibleMonth Visible month, or null when its calendar is not displayed.
     * @param checkIn Inclusive selected arrival, or null for no selection.
     * @param checkOut Exclusive departure; null reads arrival or a later month's preceding night.
     */
    fun update(
        visibleMonth: YearMonth?,
        checkIn: LocalDate? = null,
        checkOut: LocalDate? = null,
    ) {
        if (closed) return
        val visibleRange = visibleMonth?.let { DateRange(it.atDay(1), it.plusMonths(1).atDay(1)) }
        val selectedRange =
            when {
                checkIn == null -> {
                    null
                }

                checkOut != null -> {
                    if (checkOut > checkIn) DateRange(checkIn, checkOut) else null
                }

                checkIn < LocalDate.MAX -> {
                    if (visibleRange != null && checkIn < visibleRange.start) {
                        DateRange(visibleRange.start.minusDays(1), visibleRange.start)
                    } else {
                        DateRange(checkIn, checkIn.plusDays(1))
                    }
                }

                else -> {
                    null
                }
            }
        selectionUsesVisible = selectedRange != null && visibleRange != null &&
            selectedRange.start >= visibleRange.start &&
            selectedRange.endExclusive <= visibleRange.endExclusive
        // Install both scopes before any immediate source callback can retire this parent.
        val visibleJob = visibleQuery.install(visibleRange)
        val selectedJob = selectedQuery.install(if (selectionUsesVisible) null else selectedRange)
        publish()
        visibleJob?.start()
        selectedJob?.start()
    }

    /** Retries only the failed visible-month source, publishing its guard before collection. */
    fun retryVisible() {
        if (closed || visibleQuery.result != CalendarInventoryRead.Failure) return
        val pending = visibleQuery.install(visibleQuery.range, force = true)
        publish()
        pending?.start()
    }

    /** Retries selection only, delegating to the shared source for an in-month stay. */
    fun retrySelection() {
        if (closed) return
        if (selectionUsesVisible) {
            retryVisible()
        } else if (selectedQuery.result == CalendarInventoryRead.Failure) {
            val pending = selectedQuery.install(selectedQuery.range, force = true)
            publish()
            pending?.start()
        }
    }

    /** Stops both ranges while allowing later same-parent reactivation. */
    fun stop() {
        update(null)
    }

    /** Retires readers and stale actions permanently with the booking parent. */
    fun close() {
        if (closed) return
        closed = true
        visibleQuery.install(null)
        selectedQuery.install(null)
        mutableState.value = BookingCalendarReadState()
    }

    /** Projects actual scopes, sharing the authoritative result for a contained selection. */
    private fun publish() {
        if (!closed) {
            mutableState.value =
                BookingCalendarReadState(
                    visibleQuery.result,
                    if (selectionUsesVisible) visibleQuery.result else selectedQuery.result,
                )
        }
    }

    /** Private query key, independent of visual selection and the parent's saved draft.
     * @param start Inclusive occupied date.
     * @param endExclusive Exclusive date after the last queried night.
     */
    private data class DateRange(
        val start: LocalDate,
        val endExclusive: LocalDate,
    )

    /** One replaceable source with its own retry guard and late-result token.
     * @param campsiteId Fixed actual saved campsite.
     * @param campsites Half-open inventory source.
     * @param scope Owning parent's coroutine lifetime.
     * @param onChanged Projects accepted content/failure after this slot updates.
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

        /** Installs a lazy source or retires it before the caller starts any prepared work.
         * @param range New exact range, or null to release this slot.
         * @param force Allows the caller's explicit failed-source retry of an unchanged range.
         * @return Installed lazy job, or null when no collection needs starting.
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
                            .observeCalendar(
                                campsiteId,
                                range.start,
                                range.endExclusive,
                            ).collect { nights ->
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

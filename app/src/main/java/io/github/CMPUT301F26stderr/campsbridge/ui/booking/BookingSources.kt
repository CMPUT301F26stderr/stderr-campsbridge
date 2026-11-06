/*
 * Observes booking records, consumed wins and booking-window deadlines for one selected offer.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Owns fixed booking reads, consumption checks and booking-window deadlines.
 * @param entrantId Account whose own win is required.
 * @param lotteryId Draw being booked.
 * @param campsiteId Offered site being booked.
 * @param lotteries Draw, membership and offer observations.
 * @param campsites Current campsite observation.
 * @param bookings Server-confirmed consumption lookup.
 * @param clock Current booking and site-local time.
 * @param scope Wizard-owned coroutine lifetime.
 * @param isReserved Whether this wizard already has a confirmed consuming identity.
 * @param editingBlocked Whether a reservation command owns the current attempt.
 * @param onChanged Reprojects the wizard after a current read or deadline changes.
 * @param onReservation Retains an actual consuming reservation identity.
 */
internal class BookingSources(
    private val entrantId: String,
    private val lotteryId: String,
    private val campsiteId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val bookings: BookingRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val isReserved: () -> Boolean,
    private val editingBlocked: () -> Boolean,
    private val onChanged: () -> Unit,
    private val onReservation: (String) -> Unit,
) {
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

    /** Current site only after the exact observation has confirmed its identity. */
    val confirmedCampsite: Campsite?
        get() = campsite?.takeIf { BookingReadSource.CAMPSITE in loaded && it.id == campsiteId }

    /** A current successful consumption check is required before another explicit save. */
    val canReserve: Boolean
        get() = opportunityUnused && !opportunityInFlight && !opportunityFailed

    /** Installs each fixed observation once for this wizard. */
    fun start() {
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
    }

    /** Starts or retires consumption evidence when the own-winning facts change. */
    fun reconcileOpportunity() {
        if (closed || isReserved()) return
        if (!hasOwnDrawnWin()) {
            stopOpportunity()
        } else if (!opportunityAttempted && !editingBlocked()) {
            loadOpportunity()
        }
    }

    /** A server recovery confirmed this opportunity has no consuming reservation. */
    fun confirmUnused() {
        opportunityAttempted = true
        opportunityUnused = true
        opportunityFailed = false
    }

    /** Retries a failed/interrupted source without duplicating an active read.
     * @param source Exact failed or cancelled source.
     */
    fun retry(source: BookingReadSource) {
        if (closed || editingBlocked() || isReserved()) return
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

    /** Rechecks time and consumption while keeping previously confirmed content visible. */
    fun refreshTime() {
        if (closed || isReserved()) return
        if (editingBlocked()) {
            onChanged()
            return
        }
        jobs.keys
            .toList()
            .filter(::sourceWasInterrupted)
            .forEach(::retry)
        onChanged()
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
        if (closed || isReserved()) return
        val version = (versions[source] ?: 0) + 1
        versions[source] = version
        jobs.remove(source)?.cancel()
        loaded.remove(source)
        failed.remove(source)
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    observe().collect { value ->
                        currentCoroutineContext().ensureActive()
                        if (versions[source] != version) return@collect
                        accept(value)
                        loaded.add(source)
                        failed.remove(source)
                        onChanged()
                    }
                } catch (cancelled: CancellationException) {
                    if (!closed && versions[source] == version) {
                        loaded.remove(source)
                        onChanged()
                    }
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (versions[source] == version) {
                        loaded.remove(source)
                        failed.add(source)
                        onChanged()
                    }
                }
            }
        jobs[source] = pending
        onChanged()
        pending.start()
    }

    /** Uses only completed parent and exact own membership facts to authorize consumption reads. */
    private fun hasOwnDrawnWin(): Boolean =
        BookingReadSource.LOTTERY in loaded && BookingReadSource.MEMBERSHIP in loaded &&
            lottery?.let { it.id == lotteryId && it.publishedAt != null && it.drawnAt != null } ==
            true &&
            ownEntry?.state == EntryState.OFFERED

    /** Server-confirms consuming history before edits; UI never derives the stored key. */
    private fun loadOpportunity() {
        if (closed || editingBlocked() || opportunityInFlight ||
            isReserved() || !hasOwnDrawnWin()
        ) {
            return
        }
        val version = ++opportunityVersion
        opportunityJob?.cancel()
        opportunityAttempted = true
        opportunityInFlight = true
        opportunityFailed = false
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
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
                        onReservation(reservation.id)
                    } else {
                        onChanged()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == opportunityVersion) {
                        opportunityInFlight = false
                        opportunityFailed = true
                        onChanged()
                    }
                } finally {
                    if (version == opportunityVersion) opportunityInFlight = false
                }
            }
        opportunityJob = pending
        onChanged()
        pending.start()
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

    /** Resolves source, identity, consumption and window prerequisites before date-dependent UI.
     * @param draft Current retained input.
     * @param month Restored or site-local month to display.
     */
    fun readOutcome(
        draft: BookingDraft,
        month: YearMonth?,
    ): BookingUiState {
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
            draft,
            checkNotNull(month),
            localToday(site),
            isCheckingReservation = opportunityInFlight,
            reservationCheckFailed = opportunityFailed,
        )
    }

    /** Uses the confirmed site's timezone instead of the device default.
     * @param site Current site whose civil dates are displayed.
     */
    fun localToday(site: Campsite): LocalDate =
        clock.instant().atZone(ZoneId.of(site.details.timeZone)).toLocalDate()

    /** Waits for only the nearest booking boundary or campsite-local rollover. */
    fun scheduleTime() {
        val now = clock.instant()
        val draw = lottery.takeIf { BookingReadSource.LOTTERY in loaded }
        val site = campsite.takeIf { BookingReadSource.CAMPSITE in loaded }
        val deadlines = mutableListOf<Instant>()
        if (!isReserved() && draw != null) {
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
            scope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                timeJob = null
                timeDeadline = null
                onChanged()
            }
        timeJob = pending
        pending.start()
    }

    /** Retires callbacks before cancelling source reads and deadline work. */
    fun close() {
        closed = true
        versions.keys.toList().forEach { versions[it] = checkNotNull(versions[it]) + 1 }
        jobs.values.forEach { it.cancel() }
        opportunityVersion++
        opportunityJob?.cancel()
        timeJob?.cancel()
    }
}

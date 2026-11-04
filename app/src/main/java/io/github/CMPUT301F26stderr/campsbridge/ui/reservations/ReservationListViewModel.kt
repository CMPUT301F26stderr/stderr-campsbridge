package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.VisibleImageSlots
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads one actor's saved stays and projects the active rows.
 * @param accountId Current actor identity.
 * @param context Entrant or owner presentation.
 * @param bookings Authoritative scoped stay source.
 * @param accounts Independent owner-row names.
 * @param images Independent visible photos.
 * @param clock Source of booked-local dates.
 * @param savedState Retained campsite selection.
 * @param initialCampsiteId Optional management-entry filter.
 * @param classify Date-category dependency, absent while integration is pending.
 * @param order Arrival-order dependency, absent while integration is pending.
 */
class ReservationListViewModel(
    private val accountId: String,
    val context: ReservationContext,
    private val bookings: BookingRepository,
    private val accounts: AccountRepository,
    images: ImageRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    initialCampsiteId: String? = null,
    private val classify: ((Reservation, LocalDate) -> ReservationCategory)? = null,
    private val order: ((List<Reservation>) -> List<Reservation>)? = null,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<ReservationListUiState>(ReservationListUiState.Loading)
    val state = mutableState.asStateFlow()
    private val selectedCampsite =
        MutableStateFlow(
            when {
                context == ReservationContext.ENTRANT -> null
                savedState.contains(FILTER_KEY) -> savedState.get<String>(FILTER_KEY)
                else -> initialCampsiteId
            },
        )
    val campsiteId = selectedCampsite.asStateFlow()
    private val photoReader = VisibleImageSlots(images, viewModelScope)
    val photos = photoReader.state
    private var visibleIds = emptySet<String>()
    private var records: List<Reservation>? = null
    private var sourceFailed = false
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var closed = false
    private var dateJob: Job? = null
    private var nextMidnight: Instant? = null
    private val names = mutableMapOf<String, ReservationEntrantName>()
    private val nameJobs = mutableMapOf<String, Job>()
    private val nameTokens = mutableMapOf<String, Long>()
    private var nextNameToken = 0L

    init {
        observe()
    }

    /** Reprojects an owner selection without opening another source.
     * @param id Saved campsite identity, or null for all.
     */
    fun selectCampsite(id: String?) {
        if (closed || context != ReservationContext.OWNER || id == campsiteId.value) return
        selectedCampsite.value = id
        savedState[FILTER_KEY] = id
        publish()
    }

    /** Retries a failed or interrupted source, coalescing repeated clicks. */
    fun retry() {
        if (!closed && (sourceFailed || (records == null && sourceJob?.isActive != true))) observe()
    }

    /** Rechecks booked-local dates when the destination returns to the foreground. */
    fun refreshTime() {
        if (closed) return
        if (records == null && !sourceFailed &&
            sourceJob?.isActive != true
        ) {
            observe()
        } else {
            publish()
        }
    }

    /** Retries failed or interrupted names without replacing the booking source. */
    fun retryNames() {
        if (closed || context != ReservationContext.OWNER) return
        for (id in currentItems().map { it.reservation.entrantId }.toSet()) {
            if (names[id] == ReservationEntrantName.Failure ||
                (names[id] == ReservationEntrantName.Loading && nameJobs[id]?.isActive != true)
            ) {
                retireName(id, forget = true)
            }
        }
        publish()
    }

    /** Bounds photos to visible rows still permitted by the current list.
     * @param ids Visible reservation identities, or empty when leaving the route.
     */
    fun showVisibleReservations(ids: Set<String>) {
        if (closed) return
        val permitted = currentItems().map { it.reservation.id }.toSet()
        val selected = ids intersect permitted
        if (visibleIds == selected) return
        visibleIds = selected
        updatePhotos()
    }

    /** Retries one visible photo independently of saved stay and name reads.
     * @param campsiteId Current campsite photo target.
     */
    fun retryPhoto(campsiteId: String) {
        if (!closed) photoReader.retry(ImageTarget(ImageTargetType.CAMPSITE, campsiteId))
    }

    /** Deduplicates campsite photos and releases targets that leave the displayed rows. */
    private fun updatePhotos() {
        val rows = currentItems()
        visibleIds = visibleIds intersect rows.map { it.reservation.id }.toSet()
        photoReader.show(
            rows
                .filter { it.reservation.id in visibleIds }
                .map {
                    ImageTarget(ImageTargetType.CAMPSITE, it.reservation.campsiteId)
                }.toSet(),
        )
    }

    /** Installs the replacement identity guard before starting a potentially synchronous flow. */
    private fun observe() {
        val version = ++sourceVersion
        sourceJob?.cancel()
        records = null
        sourceFailed = false
        publish()
        sourceJob =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val source =
                        when (context) {
                            ReservationContext.OWNER -> bookings.observeForOwner(accountId)
                            ReservationContext.ENTRANT -> bookings.observeForEntrant(accountId)
                        }
                    source.collect { incoming ->
                        currentCoroutineContext().ensureActive()
                        if (closed || version != sourceVersion) return@collect
                        records =
                            incoming
                                .filter {
                                    context == ReservationContext.OWNER || it.entrantId == accountId
                                }.distinctBy { it.id }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    if (!closed && version == sourceVersion) {
                        records = null
                        publish()
                    }
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == sourceVersion) {
                        records = null
                        sourceFailed = true
                        publish()
                    }
                }
            }
        sourceJob?.start()
    }

    /** Applies the supplied rules to saved records while keeping source failures distinct. */
    private fun publish() {
        if (closed) return
        val current = records
        if (current == null) {
            prepareNames(emptySet(), emptySet())
            mutableState.value =
                if (sourceFailed) ReservationListUiState.Failure else ReservationListUiState.Loading
            updatePhotos()
            scheduleDateRefresh(emptyList())
            return
        }
        val categories =
            classify
                ?.let { classifier ->
                    current.associate { it.id to classifier(it, localToday(it)) }
                }.orEmpty()
        val active =
            current.filter {
                (
                    categories[it.id] == ReservationCategory.UPCOMING ||
                        categories[it.id] == ReservationCategory.IN_PROGRESS
                ) &&
                    (campsiteId.value == null || it.campsiteId == campsiteId.value)
            }
        val ordered = order?.invoke(active) ?: active
        val displayedNames =
            if (context == ReservationContext.OWNER) {
                ordered.map { it.entrantId }.toSet()
            } else {
                emptySet()
            }
        val pendingNames = prepareNames(displayedNames, current.map { it.entrantId }.toSet())
        val choices =
            if (context == ReservationContext.OWNER) {
                current.groupBy { it.campsiteId }.map { (id, stays) ->
                    ReservationCampsiteChoice(id, stays.maxBy { it.createdAt }.bookedCampsite.name)
                }
            } else {
                emptyList()
            }
        mutableState.value =
            ReservationListUiState.Content(
                items =
                    ordered.map {
                        ReservationListItem(it, categories.getValue(it.id), names[it.entrantId])
                    },
                campsites = choices,
                classificationPending = classify == null,
                orderingPending = order == null,
            )
        updatePhotos()
        scheduleDateRefresh(current)
        pendingNames.forEach { it.start() }
    }

    /** Returns only the currently confirmed displayed rows. */
    private fun currentItems(): List<ReservationListItem> =
        (state.value as? ReservationListUiState.Content)?.items.orEmpty()

    /** Installs name jobs before publication so immediate completions cannot duplicate reads.
     * @param displayed Entrants referenced by visible list membership.
     * @param retained Entrants still present in the full scoped source.
     * @return New jobs to start after publishing Loading labels.
     */
    private fun prepareNames(
        displayed: Set<String>,
        retained: Set<String>,
    ): List<Job> {
        for (id in names.keys.toList()) {
            if (id !in displayed) retireName(id, forget = id !in retained)
        }
        return displayed.filter { it !in names }.map { id ->
            names[id] = ReservationEntrantName.Loading
            val token = ++nextNameToken
            nameTokens[id] = token
            viewModelScope
                .launch(start = CoroutineStart.LAZY) {
                    try {
                        val account = accounts.get(id)
                        currentCoroutineContext().ensureActive()
                        if (closed || nameTokens[id] != token) return@launch
                        names[id] =
                            if (account != null && account.id == id && account.isActive) {
                                ReservationEntrantName.Available(account.name)
                            } else {
                                ReservationEntrantName.Unavailable
                            }
                        publish()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (!closed && nameTokens[id] == token) {
                            names[id] = ReservationEntrantName.Failure
                            publish()
                        }
                    } finally {
                        if (nameTokens[id] == token) nameJobs.remove(id)
                    }
                }.also { nameJobs[id] = it }
        }
    }

    /** Invalidates a label before cancelling its potentially late read.
     * @param id Entrant whose label left the displayed rows.
     * @param forget Whether a completed label is no longer relevant.
     */
    private fun retireName(
        id: String,
        forget: Boolean,
    ) {
        nameTokens.remove(id)
        nameJobs.remove(id)?.cancel()
        if (forget || names[id] == ReservationEntrantName.Loading) names.remove(id)
    }

    /** Uses the zone recorded at booking time rather than the device or a current listing.
     * @param record Saved stay being classified.
     */
    private fun localToday(record: Reservation): LocalDate =
        clock.instant().atZone(ZoneId.of(record.bookedCampsite.timeZone)).toLocalDate()

    /** Waits once for the earliest midnight among saved campsite zones.
     * @param current Confirmed stays whose local dates may change.
     */
    private fun scheduleDateRefresh(current: List<Reservation>) {
        val next =
            current
                .map {
                    val zone = ZoneId.of(it.bookedCampsite.timeZone)
                    localToday(it).plusDays(1).atStartOfDay(zone).toInstant()
                }.minOrNull()
        if (next == nextMidnight && dateJob?.isActive == true) return
        dateJob?.cancel()
        dateJob = null
        nextMidnight = next
        if (next == null) return
        dateJob =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, next)
                dateJob = null
                nextMidnight = null
                publish()
            }
        dateJob?.start()
    }

    /** Retires source callbacks before cancelling their work. */
    override fun onCleared() {
        closed = true
        sourceVersion++
        sourceJob?.cancel()
        dateJob?.cancel()
        nameTokens.clear()
        nameJobs.values.forEach { it.cancel() }
        nameJobs.clear()
        photoReader.close()
        super.onCleared()
    }

    /** Only the small owner filter is retained across recreation. */
    private companion object {
        const val FILTER_KEY = "reservationCampsite"
    }
}

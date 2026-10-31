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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
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
@Suppress("UNUSED_PARAMETER")
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
    val photos = MutableStateFlow<Map<ImageTarget, ImageSlotState>>(emptyMap()).asStateFlow()
    private var records: List<Reservation>? = null
    private var sourceFailed = false
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var closed = false

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

    /** Retries failed owner labels. */
    fun retryNames() =
        Unit

    /** Reports visible saved rows.
     * @param ids Displayed reservation identities.
     */
    fun showVisibleReservations(ids: Set<String>) =
        Unit

    /** Retries a visible current photo.
     * @param campsiteId Photo target identity.
     */
    fun retryPhoto(campsiteId: String) =
        Unit

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
            mutableState.value =
                if (sourceFailed) ReservationListUiState.Failure else ReservationListUiState.Loading
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
                items = ordered.map { ReservationListItem(it, categories.getValue(it.id)) },
                campsites = choices,
                classificationPending = classify == null,
                orderingPending = order == null,
            )
    }

    /** Uses the zone recorded at booking time rather than the device or a current listing.
     * @param record Saved stay being classified.
     */
    private fun localToday(record: Reservation): LocalDate =
        clock.instant().atZone(ZoneId.of(record.bookedCampsite.timeZone)).toLocalDate()

    /** Retires source callbacks before cancelling their work. */
    override fun onCleared() {
        closed = true
        sourceVersion++
        sourceJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val FILTER_KEY = "reservationCampsite"
    }
}

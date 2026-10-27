package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.RatingState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.summarizeRatings
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Owns submitted discovery criteria and scoped reads, separately from its filter child.
 * @param campsites Published listing and occupied-night source.
 * @param images Attachment source for visible rows.
 * @param reviews Rating source for visible rows.
 * @param savedState Small query/filter restoration values.
 * @param matchesText Reserved F09 predicate.
 * @param matchesCapacity Reserved F10 predicate.
 * @param sortCampsites Reserved shared F11 ordering.
 * @param clock Date-picker opening month; no past-date filter is invented.
 */
class ExploreViewModel(
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val reviews: ReviewRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val matchesText: ((Campsite, String) -> Boolean)? = null,
    private val matchesCapacity: ((Long, Long?) -> Boolean)? = null,
    private val sortCampsites: ((List<Campsite>) -> List<Campsite>)? = null,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            ExploreUiState(
                queryText = savedState["explore.query"] ?: "",
                submittedQuery = savedState["explore.submitted"] ?: "",
                filters = restoreAppliedFilters(),
                filterDraft =
                    if (savedState.get<Boolean>("explore.filterOpen") ==
                        true
                    ) {
                        restoreDraft("working")
                    } else {
                        null
                    },
            ),
        )
    val state = mutableState.asStateFlow()
    private var records: List<Campsite>? = null
    private var sourceFailed = false
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var visibleIds = emptySet<String>()
    private val calendars = mutableMapOf<String, CalendarRead>()
    private val rows = mutableMapOf<String, RowRead>()

    init {
        observePublished()
    }

    /** One required stay-range read, used as an identity token against late responses.
     * @param start Inclusive occupied date.
     * @param end Exclusive checkout.
     */
    private class CalendarRead(
        val start: LocalDate,
        val end: LocalDate,
    ) {
        var nights: List<CalendarNight>? = null
        var failed = false
        var job: Job? = null
    }

    /** Optional visible-row readers, independent of result membership.
     * @param photo Existing attachment/URL owner.
     */
    private class RowRead(
        val photo: ImageSlotController,
    ) {
        var photoJob: Job? = null
        var ratingJob: Job? = null
        var rating: RatingState = RatingState.Loading
    }

    /** Supplies the picker month at opening without a timer or a hard-coded prototype date. */
    fun currentFilterMonth(): YearMonth =
        YearMonth.now(clock)

    /** Edits only the toolbar draft.
     * @param text New raw input.
     */
    fun editQuery(text: String) {
        mutableState.value = state.value.copy(queryText = text)
        persist()
    }

    /** Applies the toolbar query explicitly; the published source remains subscribed once. */
    fun submitSearch() {
        mutableState.value = state.value.copy(submittedQuery = state.value.queryText)
        persist()
        publish()
    }

    /** Opens one working copy of applied criteria without resetting an already-open editor. */
    fun beginFilters() {
        if (state.value.filterDraft != null) return
        mutableState.value =
            state.value.copy(
                filterDraft = state.value.filters.toDraft(),
                filterErrors = FilterErrors(),
            )
        persist()
    }

    /** Edits the working location.
     * @param text Raw location query.
     */
    fun editLocation(text: String) {
        editFilter { copy(locationText = text) }
    }

    /** Edits the working optional stay.
     * @param checkIn Inclusive arrival.
     * @param checkOut Exclusive checkout.
     */
    fun selectStayDates(
        checkIn: LocalDate?,
        checkOut: LocalDate?,
    ) {
        editFilter { copy(checkIn = checkIn, checkOut = checkOut) }
    }

    /** Edits the working whole-count input.
     * @param text Raw optional count.
     */
    fun editGuests(text: String) {
        editFilter { copy(guestText = text) }
    }

    /** Clears only working criteria; Cancel can still retain the previous applied values. */
    fun resetFilters() {
        editFilter { FilterDraft() }
    }

    /** Validates and applies working values, returning whether the child can close. */
    fun applyFilters(): Boolean {
        val draft = state.value.filterDraft ?: return false
        when (val input = validateDiscoveryFilters(draft)) {
            is FilterInput.Invalid -> {
                mutableState.value = state.value.copy(filterErrors = input.errors)
                return false
            }

            is FilterInput.Valid -> {
                mutableState.value =
                    state.value.copy(
                        filters = input.filters,
                        filterDraft = null,
                        filterErrors = FilterErrors(),
                    )
                persist()
                publish()
                return true
            }
        }
    }

    /** Dismisses working edits without changing applied criteria or the submitted toolbar query. */
    fun cancelFilters() {
        mutableState.value = state.value.copy(filterDraft = null, filterErrors = FilterErrors())
        persist()
    }

    /** Reports currently visible stable result IDs.
     * @param ids Visible result membership.
     */
    fun setVisibleCampsites(ids: Set<String>) {
        if (visibleIds == ids) return
        visibleIds = ids.toSet()
        publish()
    }

    /** Retries failed required sources without duplicating healthy subscriptions. */
    fun retry() {
        if (sourceFailed) {
            observePublished()
        } else {
            for (id in calendars.filterValues { it.failed }.keys.toList()) {
                calendars.remove(id)?.job?.cancel()
            }
            publish()
        }
    }

    /** Retries only optional sources for a currently visible row.
     * @param id Actual result identity.
     */
    fun retryRow(id: String) {
        val row = rows[id] ?: return
        row.photo.retry()
        if (row.rating == RatingState.Failure) startRatings(id, row)
        publish()
    }

    /** Updates one local filter draft; keystrokes never alter applied criteria.
     * @param change Local immutable draft change.
     */
    private fun editFilter(change: FilterDraft.() -> FilterDraft) {
        val draft = state.value.filterDraft ?: return
        mutableState.value =
            state.value.copy(filterDraft = draft.change(), filterErrors = FilterErrors())
        persist()
    }

    /** Replaces only the failed source and ignores responses from a retired subscription. */
    private fun observePublished() {
        sourceVersion++
        val version = sourceVersion
        sourceJob?.cancel()
        records = null
        sourceFailed = false
        publish()
        sourceJob =
            viewModelScope.launch {
                try {
                    campsites.observePublished().collect { values ->
                        if (version == sourceVersion) {
                            records = values.filter { it.isPublished }
                            publish()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version == sourceVersion) {
                        records = null
                        sourceFailed = true
                        publish()
                    }
                }
            }
    }

    /** Composes reserved decisions with lead-owned location/stay rules and required read state. */
    private fun publish() {
        val current = state.value
        val text = matchesText
        val capacity = matchesCapacity
        val source = records
        val candidates =
            if (source != null && text != null && capacity != null) {
                source.filter {
                    text(it, current.submittedQuery) &&
                        capacity(it.details.maxGuests, current.filters.requestedGuests) &&
                        matchesDiscoveryLocation(
                            it.details.locationLabel,
                            current.filters.locationText,
                        )
                }
            } else {
                emptyList()
            }
        reconcileCalendars(candidates.map { it.id }.toSet(), current.filters)
        val result =
            when {
                sourceFailed -> {
                    ExploreResult.Failure
                }

                source == null -> {
                    ExploreResult.Loading
                }

                text == null || capacity == null -> {
                    ExploreResult.HelperPending
                }

                calendars.values.any { it.failed } -> {
                    ExploreResult.Failure
                }

                calendars.values.any { it.nights == null } -> {
                    ExploreResult.Loading
                }

                else -> {
                    val matched =
                        candidates.filter { site ->
                            calendars[site.id]?.let {
                                hasAvailableStay(
                                    checkNotNull(it.nights),
                                    it.start,
                                    it.end,
                                )
                            }
                                ?: true
                        }
                    ExploreResult.Content(
                        sortCampsites?.invoke(matched) ?: matched,
                        sortCampsites == null,
                    )
                }
            }
        val visible =
            (result as? ExploreResult.Content)
                ?.campsites
                .orEmpty()
                .map { it.id }
                .toSet() intersect
                visibleIds
        reconcileRows(visible)
        mutableState.value =
            current.copy(
                result =
                    if (result is ExploreResult.Content) {
                        result.copy(
                            photos =
                                rows.mapValues {
                                    it.value.photo.state.value
                                },
                            ratings = rows.mapValues { it.value.rating },
                        )
                    } else {
                        result
                    },
            )
    }

    /** Retains only the candidate IDs and exact applied stay range.
     * @param ids Non-date candidate identities.
     * @param filters Applied criteria, validated before construction.
     */
    private fun reconcileCalendars(
        ids: Set<String>,
        filters: DiscoveryFilters,
    ) {
        val start = filters.checkIn
        val end = filters.checkOut
        for (id in calendars.keys.toList()) {
            val read = checkNotNull(calendars[id])
            if (id !in ids || start == null || end == null || read.start != start ||
                read.end != end
            ) {
                calendars.remove(id)?.job?.cancel()
            }
        }
        if (start == null || end == null) return
        for (id in ids) {
            if (id in calendars) continue
            val read = CalendarRead(start, end)
            calendars[id] = read
            read.job =
                viewModelScope.launch {
                    try {
                        campsites.observeCalendar(id, start, end).collect { nights ->
                            if (calendars[id] === read) {
                                read.nights = nights
                                publish()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (calendars[id] === read) {
                            read.failed = true
                            publish()
                        }
                    }
                }
        }
    }

    /** Keeps optional readers inside visible confirmed result membership.
     * @param ids Visible matching listing identities.
     */
    private fun reconcileRows(ids: Set<String>) {
        for (id in rows.keys.toList()) {
            if (id !in ids) {
                rows.remove(id)?.let { row ->
                    row.photoJob?.cancel()
                    row.ratingJob?.cancel()
                    row.photo.close()
                }
            }
        }
        for (id in ids) {
            if (id in rows) continue
            val row =
                RowRead(
                    ImageSlotController(
                        images,
                        ImageTarget(ImageTargetType.CAMPSITE, id),
                        viewModelScope,
                    ),
                )
            rows[id] = row
            row.photoJob =
                viewModelScope.launch {
                    row.photo.state.collect { if (rows[id] === row) publish() }
                }
            startRatings(id, row)
        }
    }

    /** Starts or retries one independent visible-row review source.
     * @param id Actual listing identity.
     * @param row Current row identity token.
     */
    private fun startRatings(
        id: String,
        row: RowRead,
    ) {
        row.ratingJob?.cancel()
        row.rating = RatingState.Loading
        row.ratingJob =
            viewModelScope.launch {
                try {
                    reviews.observeReviews(id).collect { values ->
                        if (rows[id] === row) {
                            row.rating = RatingState.Ready(summarizeRatings(values))
                            publish()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (rows[id] === row) {
                        row.rating = RatingState.Failure
                        publish()
                    }
                }
            }
    }

    /** Persists only primitive toolbar/applied/working values, never assembled results. */
    private fun persist() {
        val current = state.value
        savedState["explore.query"] = current.queryText
        savedState["explore.submitted"] = current.submittedQuery
        savedState["explore.filterOpen"] = current.filterDraft != null
        saveDraft("applied", current.filters.toDraft())
        saveDraft("working", current.filterDraft ?: FilterDraft())
    }

    /** Writes primitive values for one filter lifetime.
     * @param prefix Applied or working namespace.
     * @param draft Values to retain.
     */
    private fun saveDraft(
        prefix: String,
        draft: FilterDraft,
    ) {
        savedState["explore.$prefix.location"] = draft.locationText
        savedState["explore.$prefix.in"] = draft.checkIn?.toString()
        savedState["explore.$prefix.out"] = draft.checkOut?.toString()
        savedState["explore.$prefix.guests"] = draft.guestText
    }

    /** Accepts only valid restored applied criteria; raw invalid edits remain in the working draft. */
    private fun restoreAppliedFilters(): DiscoveryFilters =
        when (val input = validateDiscoveryFilters(restoreDraft("applied"))) {
            is FilterInput.Valid -> input.filters
            is FilterInput.Invalid -> DiscoveryFilters()
        }

    /** Reconstructs local values from the corresponding saved primitive namespace.
     * @param prefix Applied or working namespace.
     */
    private fun restoreDraft(prefix: String): FilterDraft =
        FilterDraft(
            savedState["explore.$prefix.location"] ?: "",
            savedState.get<String>("explore.$prefix.in")?.let(LocalDate::parse),
            savedState.get<String>("explore.$prefix.out")?.let(LocalDate::parse),
            savedState["explore.$prefix.guests"] ?: "",
        )

    /** Opens an editable representation without sharing mutable applied state. */
    private fun DiscoveryFilters.toDraft(): FilterDraft =
        FilterDraft(locationText, checkIn, checkOut, requestedGuests?.toString() ?: "")

    /** Retires tokens before cancellation so delayed responses cannot reopen obsolete work. */
    override fun onCleared() {
        sourceVersion++
        sourceJob?.cancel()
        calendars.values.forEach { it.job?.cancel() }
        calendars.clear()
        reconcileRows(emptySet())
        super.onCleared()
    }
}

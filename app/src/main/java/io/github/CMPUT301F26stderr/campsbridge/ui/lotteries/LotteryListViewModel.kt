package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
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

/** Owns scoped primary-list reads and derived categories, without membership mutations.
 * @param accountId Current account.
 * @param context Owner inventory or own entrant memberships.
 * @param lotteries Current parent, allocation and own-entry sources.
 * @param bookings One entrant-reservations aggregate; never queried once per row.
 * @param clock Current phase/category time.
 * @param savedState Small category and campsite-filter restoration values.
 * @param initialCampsiteId Optional management-entry allocation filter.
 */
class LotteryListViewModel(
    private val accountId: String,
    val context: LotteryContext,
    private val lotteries: LotteryRepository,
    private val bookings: BookingRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    initialCampsiteId: String? = null,
) : ViewModel() {
    val categories =
        when (context) {
            LotteryContext.OWNER -> {
                listOf(
                    LotteryListCategory.PUBLISHED,
                    LotteryListCategory.DRAFTS,
                )
            }

            LotteryContext.ENTRANT -> {
                listOf(
                    LotteryListCategory.ACTION_NEEDED,
                    LotteryListCategory.WAITING,
                    LotteryListCategory.HISTORY,
                )
            }
        }
    private val mutableState = MutableStateFlow<LotteryListUiState>(LotteryListUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableCategory =
        MutableStateFlow(
            categories.firstOrNull { it.name == savedState.get<String>("lotteryCategory") }
                ?: if (context ==
                    LotteryContext.OWNER
                ) {
                    LotteryListCategory.PUBLISHED
                } else {
                    LotteryListCategory.WAITING
                },
        )
    val category = mutableCategory.asStateFlow()
    private val mutableCampsite =
        MutableStateFlow(
            if (context == LotteryContext.ENTRANT) {
                null
            } else if (savedState.contains("lotteryCampsite")) {
                savedState.get<String>("lotteryCampsite")
            } else {
                initialCampsiteId
            },
        )
    val campsiteId = mutableCampsite.asStateFlow()
    private var ownerRecords: List<Lottery>? = null
    private var ownEntries: List<LotteryEntry>? = null
    private var ownReservations: List<Reservation>? = null
    private var ownerFailed = false
    private var entriesFailed = false
    private var bookingsFailed = false
    private var ownerJob: Job? = null
    private var entryJob: Job? = null
    private var bookingJob: Job? = null
    private var timeJob: Job? = null
    private val recordJobs = mutableMapOf<String, Job>()
    private val records = mutableMapOf<String, Lottery>()
    private val recordIssues = mutableMapOf<String, LotteryReferenceIssue>()
    private val offerJobs = mutableMapOf<String, Job>()
    private val offers = mutableMapOf<String, List<LotteryOffer>>()
    private val offerIssues = mutableMapOf<String, LotteryReferenceIssue>()

    init {
        if (context == LotteryContext.OWNER) {
            observeOwner()
        } else {
            observeMemberships()
            observeBookings()
        }
    }

    /** Selects an allowed presentation category.
     * @param category Requested category for this scope.
     */
    fun selectCategory(category: LotteryListCategory) {
        if (category !in categories || category == this.category.value) return
        mutableCategory.value = category
        savedState["lotteryCategory"] = category.name
        publish()
    }

    /** Selects or clears the owner management allocation filter.
     * @param campsiteId Actual campsite ID, or null for all owned lotteries.
     */
    fun selectCampsite(campsiteId: String?) {
        if (context != LotteryContext.OWNER || campsiteId == this.campsiteId.value) return
        mutableCampsite.value = campsiteId
        savedState["lotteryCampsite"] = campsiteId
        reconcileOffers()
        publish()
    }

    /** Retries failed scoped or linked sources without repeating successful subscriptions. */
    fun retry() {
        if (ownerFailed) observeOwner()
        if (entriesFailed) observeMemberships()
        if (bookingsFailed) observeBookings()
        for ((id, issue) in recordIssues.toMap()) {
            if (issue == LotteryReferenceIssue.FAILURE) observeRecord(id)
        }
        for ((id, issue) in offerIssues.toMap()) {
            if (issue == LotteryReferenceIssue.FAILURE) observeOffers(id)
        }
        publish()
    }

    /** Re-evaluates derived facts after foreground or a scheduled deadline. */
    fun refreshTime() =
        publish()

    /** Observes only this owner's inventory, without a global scan or entrant reads. */
    private fun observeOwner() {
        ownerJob?.cancel()
        ownerFailed = false
        ownerRecords = null
        reconcileOffers()
        publish()
        ownerJob =
            viewModelScope.launch {
                try {
                    lotteries.observeForOwner(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        ownerRecords = rows.filter { it.ownerId == accountId }
                        reconcileOffers()
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    ownerFailed = true
                    ownerRecords = null
                    reconcileOffers()
                    publish()
                }
            }
    }

    /** Reconciles only own referenced lottery identities when membership changes. */
    private fun observeMemberships() {
        entryJob?.cancel()
        entriesFailed = false
        ownEntries = null
        reconcileRecords()
        publish()
        entryJob =
            viewModelScope.launch {
                try {
                    lotteries.observeForEntrant(accountId).collect { entries ->
                        currentCoroutineContext().ensureActive()
                        ownEntries =
                            entries.filter { it.accountId == accountId }.distinctBy { it.lotteryId }
                        reconcileRecords()
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    entriesFailed = true
                    ownEntries = null
                    reconcileRecords()
                    publish()
                }
            }
    }

    /** Owns exactly one reservation aggregate, including cancelled consumed opportunities. */
    private fun observeBookings() {
        bookingJob?.cancel()
        bookingsFailed = false
        ownReservations = null
        publish()
        bookingJob =
            viewModelScope.launch {
                try {
                    bookings.observeForEntrant(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        ownReservations = rows.filter { it.entrantId == accountId }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    bookingsFailed = true
                    ownReservations = null
                    publish()
                }
            }
    }

    /** Retains shared identities while retiring only removed membership readers. */
    private fun reconcileRecords() {
        val ids = ownEntries.orEmpty().map { it.lotteryId }.toSet()
        for (id in recordJobs.keys.toList()) {
            if (id !in ids) {
                recordJobs.remove(id)?.cancel()
                records.remove(id)
                recordIssues.remove(id)
            }
        }
        for (id in ids) if (id !in recordJobs) observeRecord(id)
    }

    /** Observes one referenced parent without interpreting absence as a completed category.
     * @param id Own membership's target identity.
     */
    private fun observeRecord(id: String) {
        recordJobs.remove(id)?.cancel()
        records.remove(id)
        recordIssues[id] = LotteryReferenceIssue.LOADING
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observe(id).collect { record ->
                        currentCoroutineContext().ensureActive()
                        if (record == null || record.id != id || record.publishedAt == null) {
                            records.remove(id)
                            recordIssues[id] = LotteryReferenceIssue.MISSING
                        } else {
                            records[id] = record
                            recordIssues.remove(id)
                        }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    records.remove(id)
                    recordIssues[id] = LotteryReferenceIssue.FAILURE
                    publish()
                }
            }
        recordJobs[id] = pending
        pending.start()
    }

    /** Loads allocations only when the owner explicitly scopes inventory to one campsite. */
    private fun reconcileOffers() {
        val ids =
            if (campsiteId.value !=
                null
            ) {
                ownerRecords.orEmpty().map { it.id }.toSet()
            } else {
                emptySet()
            }
        for (id in offerJobs.keys.toList()) {
            if (id !in ids) {
                offerJobs.remove(id)?.cancel()
                offers.remove(id)
                offerIssues.remove(id)
            }
        }
        for (id in ids) if (id !in offerJobs) observeOffers(id)
    }

    /** Confirms an allocation independently of parent/category reads.
     * @param id Current owner inventory target.
     */
    private fun observeOffers(id: String) {
        offerJobs.remove(id)?.cancel()
        offers.remove(id)
        offerIssues[id] = LotteryReferenceIssue.LOADING
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observeOffers(id).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        offers[id] = rows
                        offerIssues.remove(id)
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    offers.remove(id)
                    offerIssues[id] = LotteryReferenceIssue.FAILURE
                    publish()
                }
            }
        offerJobs[id] = pending
        pending.start()
    }

    /** Derives current rows and schedules one nearest relevant deadline across the whole list. */
    private fun publish() {
        timeJob?.cancel()
        timeJob = null
        mutableState.value =
            when {
                ownerFailed || entriesFailed || bookingsFailed -> {
                    LotteryListUiState.Failure
                }

                context == LotteryContext.OWNER && ownerRecords == null -> {
                    LotteryListUiState.Loading
                }

                context == LotteryContext.ENTRANT &&
                    (ownEntries == null || ownReservations == null) -> {
                    LotteryListUiState.Loading
                }

                context == LotteryContext.OWNER -> {
                    ownerContent()
                }

                else -> {
                    entrantContent()
                }
            }
        if (state.value !is LotteryListUiState.Content) return
        val parents =
            if (context ==
                LotteryContext.OWNER
            ) {
                ownerRecords.orEmpty()
            } else {
                records.values
            }
        val now = clock.instant()
        val deadline =
            parents.mapNotNull { lotteryDeadline(it, lotteryLifecycle(it, now)) }.minOrNull()
                ?: return
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                publish()
            }
        timeJob = pending
        pending.start()
    }

    /** Builds owner rows, retaining unconfirmed campsite associations beside resolved rows. */
    private fun ownerContent(): LotteryListUiState.Content {
        val items = mutableListOf<LotteryListItem>()
        val unresolved = mutableListOf<LotteryListReference>()
        for (record in ownerRecords.orEmpty().sortedWith(
            compareByDescending<Lottery> {
                it.createdAt
            }.thenBy { it.id },
        )) {
            if ((record.publishedAt == null) !=
                (category.value == LotteryListCategory.DRAFTS)
            ) {
                continue
            }
            if (campsiteId.value != null) {
                val issue =
                    offerIssues[record.id]
                        ?: LotteryReferenceIssue.LOADING.takeIf { record.id !in offers }
                if (issue != null) {
                    unresolved.add(LotteryListReference(record.id, issue))
                    continue
                }
                if (offers[record.id].orEmpty().none { it.campsiteId == campsiteId.value }) continue
            }
            items.add(LotteryListItem(record, lotteryLifecycle(record, clock.instant())))
        }
        return LotteryListUiState.Content(items, unresolved)
    }

    /** Applies category precedence before filtering, without exposing partial draw outcomes. */
    private fun entrantContent(): LotteryListUiState.Content {
        val items = mutableListOf<LotteryListItem>()
        val unresolved = mutableListOf<LotteryListReference>()
        val reserved = ownReservations.orEmpty().associateBy { it.lotteryId }
        val now = clock.instant()
        for (entry in ownEntries.orEmpty().sortedWith(
            compareByDescending<LotteryEntry> {
                it.joinedAt
            }.thenBy { it.lotteryId },
        )) {
            val record = records[entry.lotteryId]
            if (record == null) {
                unresolved.add(
                    LotteryListReference(
                        entry.lotteryId,
                        recordIssues[entry.lotteryId] ?: LotteryReferenceIssue.LOADING,
                    ),
                )
                continue
            }
            val reservation = reserved[record.id]
            val offeredBeforeClose =
                entry.state == EntryState.OFFERED && now < record.configuration.bookingClosesAt
            val selected =
                when {
                    record.cancellation != null || entry.state == EntryState.WITHDRAWN ||
                        entry.state == EntryState.DECLINED -> {
                        LotteryListCategory.HISTORY
                    }

                    record.drawnAt == null -> {
                        LotteryListCategory.WAITING
                    }

                    reservation != null -> {
                        LotteryListCategory.HISTORY
                    }

                    offeredBeforeClose -> {
                        LotteryListCategory.ACTION_NEEDED
                    }

                    else -> {
                        LotteryListCategory.HISTORY
                    }
                }
            if (selected == category.value) {
                val displayedState =
                    entry.state.takeUnless {
                        record.drawnAt == null &&
                            (it == EntryState.OFFERED || it == EntryState.LOST)
                    }
                items.add(
                    LotteryListItem(
                        record,
                        lotteryLifecycle(record, now),
                        displayedState,
                        reservation?.id.takeIf {
                            record.drawnAt !=
                                null
                        },
                    ),
                )
            }
        }
        return LotteryListUiState.Content(items, unresolved)
    }

    /** Releases every account-scoped source and the one deadline wait. */
    override fun onCleared() {
        ownerJob?.cancel()
        entryJob?.cancel()
        bookingJob?.cancel()
        recordJobs.values.forEach { it.cancel() }
        offerJobs.values.forEach { it.cancel() }
        timeJob?.cancel()
        super.onCleared()
    }
}

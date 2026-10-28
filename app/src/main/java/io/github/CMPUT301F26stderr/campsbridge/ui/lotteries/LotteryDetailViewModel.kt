package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
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

/** Owns one saved target and its independent owner/entrant read sections.
 * @param accountId Current account whose context and own membership are requested.
 * @param lotteryId Actual saved identity carried by navigation.
 * @param lotteries Saved parent and live allocation source.
 * @param clock Deterministic current time for derived display boundaries.
 * @param campsites Names and zones for referenced allocations.
 * @param bookings Authoritative opportunity and saved reservation source.
 * @param context Explicit owner management or entrant presentation.
 * @param accounts Referenced participant profile labels.
 * @param savedState Small restorable participant filter.
 * @param filterParticipants Reserved participant selector; null keeps requested filtering pending.
 */
class LotteryDetailViewModel(
    private val accountId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val clock: Clock,
    private val bookings: BookingRepository,
    val context: LotteryContext,
    accounts: AccountRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    filterParticipants: ((List<LotteryEntry>, EntryState?) -> List<LotteryEntry>)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LotteryDetailUiState>(LotteryDetailUiState.Loading)
    val state = mutableState.asStateFlow()

    private val participantReader =
        LotteryParticipantsController(
            accountId,
            lotteryId,
            lotteries,
            accounts,
            clock,
            viewModelScope,
            savedState.get<String>("participantState")?.let { value ->
                EntryState.entries.firstOrNull { it.name == value }
            },
            filterParticipants,
        )
    val participants = participantReader.state
    val selectedParticipantState = participantReader.selectedState

    /** Changes only the actual owner child lifetime.
     * @param visible Whether the participant child is composed.
     */
    fun setParticipantsVisible(visible: Boolean) {
        participantReader.setVisible(visible && context == LotteryContext.OWNER)
    }

    /** Selects and persists a safe participant filter.
     * @param state Requested exact stored state, or null for All.
     */
    fun selectParticipantState(state: EntryState?) {
        participantReader.selectState(state)
        savedState["participantState"] = participantReader.selectedState.value?.name
    }

    /** Retries only a failed child entry source. */
    fun retryParticipants() =
        participantReader.retryEntries()

    /** Retries only failed participant labels. */
    fun retryParticipantNames() =
        participantReader.retryNames()

    private var drawJob: Job? = null
    private var drawVersion = 0L
    private var drawing = false
    private var drawFailed = false
    private var recordJob: Job? = null
    private var offerJob: Job? = null
    private var timeJob: Job? = null
    private var recordVersion = 0L
    private var offerVersion = 0L
    private var membershipJob: Job? = null
    private var membershipVersion = 0L
    private var membershipLoaded = false
    private var membershipFailed = false
    private var ownEntry: LotteryEntry? = null
    private var opportunityJob: Job? = null
    private var opportunityVersion = 0L
    private var opportunityActive = false
    private var lookupInFlight = false
    private var knownReservationId: String? = null
    private var opportunity: LotteryOpportunityRead = LotteryOpportunityRead.NotApplicable
    private val membershipCommands =
        LotteryMembershipController(
            accountId,
            lotteryId,
            lotteries,
            clock,
            viewModelScope,
            onChanged = ::renderSections,
        )

    init {
        observeRecord()
    }

    /** Runs an eligible owner fallback against the existing resumable draw operation. */
    fun draw() {
        val current = state.value as? LotteryDetailUiState.Content ?: return
        if (drawing || !canDraw(current.lottery)) return
        val version = ++drawVersion
        drawing = true
        drawFailed = false
        refreshSections()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.draw(lotteryId)
                    currentCoroutineContext().ensureActive()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == drawVersion) {
                        drawFailed = true
                    }
                } finally {
                    if (version == drawVersion) {
                        drawJob = null
                        drawing = false
                        refreshSections()
                    }
                }
            }
        drawJob = pending
        pending.start()
    }

    /** Checks current owner context and the existing lifecycle at each action boundary.
     * @param lottery Confirmed saved parent.
     */
    private fun canDraw(lottery: Lottery): Boolean =
        context == LotteryContext.OWNER &&
            lotteryLifecycle(lottery, clock.instant()) == LotteryLifecycle.DRAW_PENDING

    /** Retires only manual-command presentation; the repository owns resumable draw progress. */
    private fun stopDraw() {
        drawVersion++
        drawJob?.cancel()
        drawJob = null
        drawing = false
        drawFailed = false
    }

    /** Requests one eligible join/rejoin through the shared command owner. */
    fun join() =
        membershipCommands.join()

    /** Opens withdrawal confirmation using current supplied facts. */
    fun requestLeave() =
        membershipCommands.requestLeave()

    /** Dismisses an unsubmitted confirmation without writing. */
    fun dismissLeave() =
        membershipCommands.dismissLeave()

    /** Rechecks eligibility and submits one explicitly confirmed withdrawal. */
    fun confirmLeave() =
        membershipCommands.confirmLeave()

    /** Retries only the failed own-entry source. */
    fun retryMembership() {
        if (context == LotteryContext.ENTRANT && membershipFailed &&
            state.value is LotteryDetailUiState.Content
        ) {
            observeMembership()
        }
    }

    /** Retries unknown consumption without another booking attempt. */
    fun retryOpportunity() {
        if (opportunity == LotteryOpportunityRead.Failure) loadOpportunity()
    }

    /** Recalculates read-only lifecycle after foreground or deadline changes. */
    fun refreshTime() {
        refreshSections()
        if (opportunityActive &&
            (knownReservationId == null || opportunity == LotteryOpportunityRead.Failure)
        ) {
            loadOpportunity()
        }
    }

    /** Retries only the failed saved parent observation. */
    fun retry() {
        if (state.value == LotteryDetailUiState.Failure) observeRecord()
    }

    /** Retries only failed allocation and referenced-label reads. */
    fun retryOffers() {
        if ((state.value as? LotteryDetailUiState.Content)?.offers == LotteryOffersState.Failure) {
            observeOffers()
        }
    }

    /**
     * Owns one saved parent listener and releases child reads when the target becomes
     * unavailable.
     */
    private fun observeRecord() {
        val version = ++recordVersion
        recordJob?.cancel()
        participantReader.updateParent(null)
        stopOffers()
        stopTime()
        stopMembership()
        membershipCommands.updateFacts(null, null, false)
        stopDraw()
        mutableState.value = LotteryDetailUiState.Loading
        recordJob =
            viewModelScope.launch {
                try {
                    lotteries.observe(lotteryId).collect { record ->
                        if (version != recordVersion) return@collect
                        val unavailable =
                            record == null || record.id != lotteryId ||
                                when (context) {
                                    LotteryContext.OWNER -> record.ownerId != accountId
                                    LotteryContext.ENTRANT -> record.publishedAt == null
                                }
                        if (unavailable) {
                            stopOffers()
                            stopTime()
                            stopMembership()
                            membershipCommands.updateFacts(null, null, false)
                            stopDraw()
                            participantReader.updateParent(null)
                            mutableState.value = LotteryDetailUiState.Unavailable
                        } else {
                            checkNotNull(record)
                            if (record.cancellation != null) stopDraw()
                            val prior = state.value as? LotteryDetailUiState.Content
                            mutableState.value =
                                LotteryDetailUiState.Content(
                                    record,
                                    prior?.offers ?: LotteryOffersState.Loading,
                                    canContinue =
                                        context == LotteryContext.OWNER &&
                                            record.publishedAt == null &&
                                            record.cancellation == null,
                                    lifecycle = lotteryLifecycle(record, clock.instant()),
                                )
                            if (prior == null) {
                                observeOffers()
                                if (context == LotteryContext.ENTRANT) observeMembership()
                            }
                            reconcileOpportunity()
                            refreshSections()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == recordVersion) {
                        stopOffers()
                        stopTime()
                        stopMembership()
                        membershipCommands.updateFacts(null, null, false)
                        stopDraw()
                        participantReader.updateParent(null)
                        mutableState.value = LotteryDetailUiState.Failure
                    }
                }
            }
    }

    /** Owns one account membership source and never reads another entrant's participation. */
    private fun observeMembership() {
        if (context != LotteryContext.ENTRANT ||
            state.value !is LotteryDetailUiState.Content
        ) {
            return
        }
        stopMembership()
        val version = membershipVersion
        refreshSections()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observeForEntrant(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (version != membershipVersion) return@collect
                        ownEntry =
                            rows.firstOrNull {
                                it.accountId == accountId &&
                                    it.lotteryId == lotteryId
                            }
                        membershipLoaded = true
                        membershipFailed = false
                        reconcileOpportunity()
                        refreshSections()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == membershipVersion) {
                        membershipLoaded = false
                        membershipFailed = true
                        ownEntry = null
                        reconcileOpportunity()
                        refreshSections()
                    }
                }
            }
        membershipJob = pending
        pending.start()
    }

    /** Starts consumption reading only for a confirmed, completed own winning opportunity. */
    private fun reconcileOpportunity() {
        val current = state.value as? LotteryDetailUiState.Content
        val eligible =
            context == LotteryContext.ENTRANT && membershipLoaded && !membershipFailed &&
                ownEntry?.state == EntryState.OFFERED && current?.lottery?.drawnAt != null
        if (!eligible) {
            stopOpportunity()
        } else if (!opportunityActive) {
            opportunityActive = true
            loadOpportunity()
        }
    }

    /** Server-confirms consumption, then observes that one complete saved aggregate. */
    private fun loadOpportunity() {
        if (!opportunityActive || lookupInFlight ||
            state.value !is LotteryDetailUiState.Content
        ) {
            return
        }
        val version = ++opportunityVersion
        opportunityJob?.cancel()
        lookupInFlight = true
        opportunity = LotteryOpportunityRead.Loading
        refreshSections()
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val reservation = bookings.getForOpportunity(lotteryId, accountId)
                    currentCoroutineContext().ensureActive()
                    if (version != opportunityVersion) return@launch
                    lookupInFlight = false
                    if (reservation != null) knownReservationId = reservation.id
                    val savedId = knownReservationId
                    opportunity =
                        when {
                            reservation != null -> LotteryOpportunityRead.Reserved(reservation)
                            savedId != null -> LotteryOpportunityRead.Unavailable(savedId)
                            else -> LotteryOpportunityRead.Unused
                        }
                    refreshSections()
                    if (savedId != null) {
                        bookings.observe(savedId).collect { saved ->
                            currentCoroutineContext().ensureActive()
                            if (version != opportunityVersion) return@collect
                            opportunity =
                                if (saved == null) {
                                    LotteryOpportunityRead.Unavailable(savedId)
                                } else {
                                    LotteryOpportunityRead.Reserved(saved)
                                }
                            refreshSections()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == opportunityVersion) {
                        opportunity = LotteryOpportunityRead.Failure
                        refreshSections()
                    }
                } finally {
                    if (version == opportunityVersion) lookupInFlight = false
                }
            }
        opportunityJob = pending
        pending.start()
    }

    /** Recalculates public sections from current source facts without exposing raw draw entries. */
    private fun refreshSections() {
        val current = state.value as? LotteryDetailUiState.Content ?: return
        participantReader.updateParent(current.lottery.takeIf { context == LotteryContext.OWNER })
        savedState["participantState"] = participantReader.selectedState.value?.name
        participantReader.refreshTime()
        if (current.lottery.drawnAt != null ||
            current.lottery.cancellation != null
        ) {
            drawFailed = false
        }
        membershipCommands.updateFacts(
            current.lottery.takeIf { context == LotteryContext.ENTRANT },
            ownEntry,
            membershipLoaded && !membershipFailed,
        )
        renderSections()
    }

    /** Projects independent reads and shared command feedback without feeding facts back. */
    private fun renderSections() {
        val current = state.value as? LotteryDetailUiState.Content ?: return
        val membership =
            when {
                context == LotteryContext.OWNER -> {
                    LotteryMembershipRead.NotApplicable
                }

                membershipFailed -> {
                    LotteryMembershipRead.Failure
                }

                !membershipLoaded -> {
                    LotteryMembershipRead.Loading
                }

                else -> {
                    LotteryMembershipRead.Content(
                        lotteryMembershipStatus(current.lottery, ownEntry, clock.instant()),
                    )
                }
            }
        mutableState.value =
            current.copy(
                lifecycle = lotteryLifecycle(current.lottery, clock.instant()),
                membership = membership,
                opportunity = opportunity,
                participation = membershipCommands.state.value,
                canDraw = canDraw(current.lottery),
                isDrawing = drawing,
                drawFailed = drawFailed,
            )
        scheduleTime()
    }

    /** Invalidates late own-entry callbacks and their dependent consumption lane. */
    private fun stopMembership() {
        membershipVersion++
        membershipJob?.cancel()
        membershipJob = null
        membershipLoaded = false
        membershipFailed = false
        ownEntry = null
        stopOpportunity()
    }

    /** Retires a lookup/listener without allowing a late result to release or consume a win. */
    private fun stopOpportunity() {
        opportunityVersion++
        opportunityJob?.cancel()
        opportunityJob = null
        opportunityActive = false
        lookupInFlight = false
        opportunity = LotteryOpportunityRead.NotApplicable
    }

    /**
     * Owns live allocations and reads their actual campsites without dropping missing
     * references.
     */
    private fun observeOffers() {
        val current = state.value as? LotteryDetailUiState.Content ?: return
        stopOffers()
        val version = offerVersion
        mutableState.value = current.copy(offers = LotteryOffersState.Loading)
        offerJob =
            viewModelScope.launch {
                try {
                    lotteries.observeOffers(lotteryId).collect { offers ->
                        val summaries =
                            offers.sortedBy { it.campsiteId }.map { offer ->
                                val site = campsites.get(offer.campsiteId)
                                currentCoroutineContext().ensureActive()
                                LotteryOfferSummary(
                                    offer,
                                    site,
                                )
                            }
                        if (version != offerVersion) return@collect
                        val active = state.value as? LotteryDetailUiState.Content ?: return@collect
                        mutableState.value =
                            active.copy(offers = LotteryOffersState.Content(summaries))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == offerVersion) {
                        val active = state.value as? LotteryDetailUiState.Content
                        if (active !=
                            null
                        ) {
                            mutableState.value = active.copy(offers = LotteryOffersState.Failure)
                        }
                    }
                }
            }
    }

    /**
     * Waits only for the next relevant window; completed draws still come from repository facts.
     */
    private fun scheduleTime() {
        stopTime()
        val current = state.value as? LotteryDetailUiState.Content ?: return
        val deadline = lotteryDeadline(current.lottery, current.lifecycle) ?: return
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                refreshSections()
            }
        timeJob = pending
        pending.start()
    }

    /** Releases a deadline wait when its source or owner changes. */
    private fun stopTime() {
        timeJob?.cancel()
        timeJob = null
    }

    /** Invalidates retired child callbacks before releasing their listener. */
    private fun stopOffers() {
        offerVersion++
        offerJob?.cancel()
        offerJob = null
    }

    /** Releases this saved graph's subscriptions and rejects late callbacks. */
    override fun onCleared() {
        participantReader.close()
        recordVersion++
        recordJob?.cancel()
        stopOffers()
        stopTime()
        stopMembership()
        membershipCommands.close()
        stopDraw()
        super.onCleared()
    }
}

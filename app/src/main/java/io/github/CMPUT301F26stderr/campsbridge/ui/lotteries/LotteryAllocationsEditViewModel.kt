/*
 * Owns one participating owner's desired offer set without editing shared details.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Owns one participating owner's desired offer set without editing shared details.
 * @param lotteryId Existing shared lottery identity.
 * @param ownerId Acting participating owner.
 * @param campsites Required ownership facts and working-calendar reads.
 * @param lotteries Saved context and complete-set owned replacement.
 * @param clock Shared boundary clock.
 * @param savedState Primitive local offers and temporary calendar input.
 */
class LotteryAllocationsEditViewModel(
    val lotteryId: String,
    private val ownerId: String,
    private val campsites: CampsiteRepository,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LotteryAllocationsEditUiState>(LotteryAllocationsEditUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableCompletion = MutableStateFlow(lotteryId.takeIf { savedState.get<Boolean>("completed") == true })
    val completion = mutableCompletion.asStateFlow()
    private val mutableSaving = MutableStateFlow(false)
    val isSaving = mutableSaving.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()
    private var baseline = emptyList<LotteryOffer>()
    private var editor: LotteryAllocationsEditUiState.Content? = null
    private var reading: Job? = null
    private var focus = savedState.get<String>("workingSite")
    private var working = restoredCalendar()
    private var retainLocal = savedState.get<Boolean>("dirty") == true
    private val calendar =
        LotteryAllocationController(campsites, viewModelScope) { value ->
            working = value
            if (value == null) focus = null
            retainCalendar()
            editor?.let { publish(it.copy(allocation = value, selectedCampsiteId = focus)) }
        }

    init {
        retry()
    }

    /** Reloads required facts without replaying a write or dropping local selections. */
    fun retry() {
        if (isSaving.value) return
        calendar.pause()
        mutableState.value = LotteryAllocationsEditUiState.Loading
        observeContext()
    }

    /** Keeps ordinary observation separate from an explicit replacement and its conflict refresh. */
    private fun observeContext() {
        reading?.cancel()
        reading =
            viewModelScope.launch {
                try {
                    combine(
                        lotteries.observe(lotteryId),
                        lotteries.observeOffers(lotteryId),
                        campsites.observeForOwner(ownerId),
                    ) { _, _, choices -> choices }
                        .collectLatest { choices ->
                            // Cached listener absence must never seed a complete-set replacement.
                            acceptContext(lotteries.get(lotteryId), lotteries.getOffers(lotteryId), choices)
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    calendar.pause()
                    mutableState.value = LotteryAllocationsEditUiState.Failure
                }
            }
    }

    /** Removes one local owned row without writing shared allocations.
     * @param campsiteId Applied owned campsite identity.
     */
    fun removeAllocation(campsiteId: String) {
        refreshTime()
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return
        if (!current.isEditable || current.isSaving || current.allocation != null) return
        retainLocal = true
        publish(current.copy(offers = current.offers.filterNot { it.campsiteId == campsiteId }, saveFailed = false))
    }

    /** Opens a temporary calendar for an eligible owned campsite.
     * @param campsiteId Confirmed owned choice.
     */
    fun beginAllocation(campsiteId: String): Boolean {
        refreshTime()
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return false
        if (!current.isEditable || current.isSaving || current.allocation != null) return false
        val site = current.campsites.firstOrNull { it.id == campsiteId && eligible(it) } ?: return false
        focus = campsiteId
        val dates =
            current.offers
                .firstOrNull { it.campsiteId == campsiteId }
                ?.nights
                .orEmpty()
                .toSet()
        calendar.open(site.id, YearMonth.now(clock.withZone(ZoneId.of(site.details.timeZone))), dates)
        return true
    }

    /** Changes only working dates in the focused calendar.
     * @param dates Complete working night selection.
     */
    fun selectAllocationDates(dates: Set<LocalDate>) {
        if (canEditCalendar()) calendar.select(dates)
    }

    /** Changes the working visible month without losing off-screen selections.
     * @param month Requested campsite-local month.
     */
    fun changeAllocationMonth(month: YearMonth) {
        if (canEditCalendar()) calendar.changeMonth(month)
    }

    /** Applies confirmed working dates to the local desired set without persistence. */
    fun applyAllocation(): Boolean {
        if (!canEditCalendar()) {
            calendar.invalidate()
            return false
        }
        val offer = calendar.selectedOffer() ?: return false
        val current = checkNotNull(editor)
        retainLocal = true
        publish(current.copy(offers = current.offers.filterNot { it.campsiteId == offer.campsiteId } + offer))
        calendar.cancel()
        return true
    }

    /** Discards only the working calendar, retaining the caller's applied offers. */
    fun cancelAllocation() {
        calendar.cancel()
    }

    /** Retries failed working inventory without changing globally offered dates. */
    fun retryInventory() {
        calendar.retry()
    }

    /** Rechecks membership and the published registration boundary without writing. */
    fun refreshTime() {
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return
        publish(current)
    }

    /** Returns the resumed route's next freeze delay, with fractional milliseconds rounded up. */
    fun millisUntilFreeze(): Long? {
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return null
        if (!editable(current.lottery) || current.lottery.publishedAt == null) return null
        val remaining = Duration.between(clock.instant(), current.lottery.configuration.registrationOpensAt)
        return (remaining.toMillis() + if (remaining.nano % 1_000_000 == 0) 0 else 1).coerceAtLeast(1)
    }

    /** Explicitly replaces the complete desired owned set, including an empty set. */
    fun save() {
        refreshTime()
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return
        if (!current.canSave || completion.value != null) return
        val requested = current.offers.toList()
        reading?.cancel()
        reading = null
        mutableSaving.value = true
        savedState["attempting"] = true
        publish(current.copy(saveFailed = false, contextChanged = false))
        viewModelScope.launch {
            try {
                lotteries.replaceOwnerOffers(lotteryId, ownerId, requested)
                currentCoroutineContext().ensureActive()
                baseline = requested
                retainLocal = false
                savedState["attempting"] = false
                savedState["completed"] = true
                editor?.let { publish(it.copy(saveFailed = false, contextChanged = false)) }
                mutableCompletion.value = lotteryId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (failure is PersistenceFailure && failure.reason == PersistenceFailure.Reason.CONFLICT) {
                    retainLocal = true
                    editor?.let { publish(it.copy(contextChanged = true)) }
                    try {
                        refreshContext()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        mutableState.value = LotteryAllocationsEditUiState.Failure
                    }
                } else {
                    editor?.let { publish(it.copy(saveFailed = true)) }
                }
            } finally {
                if (viewModelScope.isActive) {
                    mutableSaving.value = false
                    editor?.let { publish(it) }
                    if (completion.value == null) observeContext()
                }
            }
        }
    }

    /** Consumes one confirmed return to the saved reader. */
    fun acknowledgeCompletion() {
        savedState["completed"] = false
        mutableCompletion.value = null
    }

    /** Requests one discard confirmation for local applied changes. */
    fun requestExit() {
        if (isSaving.value) return
        if (editor?.isDirty == true) mutableDiscard.value = true else mutableExit.value = true
    }

    /** Keeps the editor when its discard confirmation is dismissed. */
    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    /** Abandons only local desired offers after an explicit confirmation. */
    fun confirmDiscard() {
        if (isSaving.value) return
        mutableDiscard.value = false
        mutableExit.value = true
    }

    /** Consumes an explicit navigation exit once. */
    fun acknowledgeExit() {
        mutableExit.value = false
    }

    /** Reads authoritative context after initial entry or a repository conflict. */
    private suspend fun refreshContext() {
        val lottery = lotteries.get(lotteryId)
        val offers = lotteries.getOffers(lotteryId)
        val choices = campsites.observeForOwner(ownerId).first()
        acceptContext(lottery, offers, choices)
    }

    /** Resolves ownership before projecting offers; failure never becomes an editable empty set.
     * @param lottery Saved parent or confirmed absence.
     * @param offers Complete saved allocations.
     * @param choices Actor-scoped listing observations.
     */
    private suspend fun acceptContext(
        lottery: Lottery?,
        offers: List<LotteryOffer>,
        choices: List<Campsite>,
    ) {
        currentCoroutineContext().ensureActive()
        if (lottery == null || lottery.id != lotteryId || (editor == null && ownerId !in lottery.participantOwnerIds)) {
            mutableState.value = LotteryAllocationsEditUiState.Unavailable
            return
        }
        val referenced = offers.map { offer -> checkNotNull(campsites.get(offer.campsiteId)) }
        currentCoroutineContext().ensureActive()
        baseline = offers.filter { offer -> referenced.any { it.id == offer.campsiteId && it.ownerId == ownerId } }
        val previous = editor
        val desired =
            when {
                previous != null && (retainLocal || isSaving.value) -> previous.offers
                previous == null && retainLocal -> restoredOffers()
                else -> baseline
            }
        val owned = (choices + referenced.filter { it.ownerId == ownerId }).associateBy { it.id }.values.toList()
        val next =
            previous ?: LotteryAllocationsEditUiState.Content(
                lottery,
                desired,
                owned,
                editable(lottery),
                saveFailed = savedState.get<Boolean>("attempting") == true,
            )
        publish(
            next.copy(
                lottery = lottery,
                offers = desired,
                campsites = owned,
                allocation = working,
                selectedCampsiteId = focus,
            ),
            expose = true,
        )
        working?.let { calendar.resume(focus?.takeIf { id -> owned.any { it.id == id && eligible(it) } }, it) }
    }

    /** Checks the current context before applying or changing working inventory. */
    private fun canEditCalendar(): Boolean {
        refreshTime()
        val current = state.value as? LotteryAllocationsEditUiState.Content ?: return false
        return current.isEditable && !current.isSaving && current.campsites.any { it.id == focus && eligible(it) }
    }

    /** Keeps shared terms editable only before registration on published records.
     * @param lottery Current membership, cancellation and deadline facts.
     */
    private fun editable(lottery: Lottery): Boolean =
        ownerId in lottery.participantOwnerIds &&
            lottery.cancellation == null &&
            (lottery.publishedAt == null || clock.instant() < lottery.configuration.registrationOpensAt)

    /** Limits this focused editor to the actor's active published listings.
     * @param site Current listing fact.
     */
    private fun eligible(site: Campsite): Boolean =
        site.ownerId == ownerId && site.isPublished

    /** Retains small input primitives and projects permissions without overriding source failures.
     * @param value Latest known context and local input.
     * @param expose Whether a required-source read has established content again.
     */
    private fun publish(
        value: LotteryAllocationsEditUiState.Content,
        expose: Boolean = state.value is LotteryAllocationsEditUiState.Content,
    ) {
        val available = editable(value.lottery)
        val next =
            value.copy(
                isEditable = available,
                isSaving = isSaving.value,
                canSave =
                    available && !isSaving.value && value.allocation == null &&
                        value.offers.all { offer -> value.campsites.any { it.id == offer.campsiteId && eligible(it) } },
                isDirty = value.offers.toSet() != baseline.toSet(),
                savedOffers = baseline,
            )
        editor = next
        savedState["dirty"] = retainLocal
        savedState["offerIds"] = ArrayList(next.offers.map { it.campsiteId })
        for (offer in next.offers) {
            savedState["nights:${offer.campsiteId}"] =
                ArrayList(offer.nights.map(LocalDate::toString))
        }
        retainCalendar()
        if (expose) mutableState.value = next
    }

    /** Retains or clears working input even before the required parent read completes. */
    private fun retainCalendar() {
        savedState["workingSite"] = focus
        savedState["workingMonth"] = working?.month?.toString()
        savedState["workingDates"] = working?.selected?.map(LocalDate::toString)?.let(::ArrayList)
    }

    /** Reconstructs applied offers from primitives without restoring inventory or replaying Save. */
    private fun restoredOffers(): List<LotteryOffer> =
        savedState.get<ArrayList<String>>("offerIds").orEmpty().map { id ->
            LotteryOffer(id, savedState.get<ArrayList<String>>("nights:$id").orEmpty().map(LocalDate::parse))
        }

    /** Restores only the working identity, month and selected civil dates. */
    private fun restoredCalendar(): LotteryAllocationUiState? =
        savedState.get<String>("workingMonth")?.let { month ->
            LotteryAllocationUiState(
                YearMonth.parse(month),
                savedState
                    .get<ArrayList<String>>("workingDates")
                    .orEmpty()
                    .map(LocalDate::parse)
                    .toSet(),
            )
        }

    /** Releases only this editor's working-calendar subscription. */
    override fun onCleared() {
        calendar.close()
        super.onCleared()
    }
}

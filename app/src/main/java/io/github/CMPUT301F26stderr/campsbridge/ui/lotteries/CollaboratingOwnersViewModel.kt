package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.Locale

/** Owns one organizer's complete local membership set and explicit saved-lottery action.
 * @param lotteryId Existing saved lottery identity.
 * @param actorId Actual account opening this picker.
 * @param accounts Current owner choices and retained profile labels.
 * @param campsites Required allocation-ownership facts.
 * @param lotteries Saved membership, offers and coordinated replacement.
 * @param clock Published registration boundary.
 * @param savedState Small query and desired-membership primitives.
 */
class CollaboratingOwnersViewModel(
    val lotteryId: String,
    private val actorId: String,
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            CollaboratingOwnersUiState(
                query = savedState.get<String>("ownerQuery").orEmpty(),
                selectedOwnerIds = savedState.get<ArrayList<String>>("ownerIds").orEmpty().toSet(),
                saveFailed = savedState.get<Boolean>("attempting") == true,
            ),
        )
    val state = mutableState.asStateFlow()
    private val mutableCompletion = MutableStateFlow(lotteryId.takeIf { savedState.get<Boolean>("completed") == true })
    val completion = mutableCompletion.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()
    private var retainLocal = savedState.get<Boolean>("ownerSelection") == true
    private var profileRecords = emptyList<Account>()
    private var allocatedOwners = emptySet<String>()
    private var allocatedSitesPublished = true
    private var reading: Job? = null

    init {
        retry()
    }

    /** Restarts required reads while keeping the local membership and search. */
    fun retry() {
        if (state.value.isSaving) return
        publish(state.value.copy(targetLoad = LotteryTargetLoad.LOADING))
        observeContext()
    }

    /** Gives ordinary observation one lifetime, separate from an explicit write and its refresh. */
    private fun observeContext() {
        reading?.cancel()
        reading =
            viewModelScope.launch {
                try {
                    combine(lotteries.observe(lotteryId), lotteries.observeOffers(lotteryId), accounts.observeAll()) {
                        _,
                        _,
                        profiles,
                        ->
                        profiles
                    }.collectLatest { profiles ->
                        // Listener snapshots invalidate context; cached absence cannot remove a saved member.
                        refreshContext(profiles)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
                }
            }
    }

    /** Retains local search without changing membership.
     * @param value Raw search text.
     */
    fun setQuery(value: String) {
        if (state.value.isSaving) return
        publish(state.value.copy(query = value))
    }

    /** Changes only one permitted local checkbox value.
     * @param ownerId Target account identity.
     * @param selected Requested membership value.
     */
    fun setOwnerSelected(
        ownerId: String,
        selected: Boolean,
    ) {
        refreshTime()
        val current = state.value
        if (current.owners.none { it.id == ownerId && it.canChange }) return
        if ((ownerId in current.selectedOwnerIds) == selected) return
        retainLocal = true
        publish(
            current.copy(
                selectedOwnerIds =
                    if (selected) {
                        current.selectedOwnerIds + ownerId
                    } else {
                        current.selectedOwnerIds -
                            ownerId
                    },
                saveFailed = false,
            ),
        )
    }

    /** Submits the complete desired membership through the existing coordinated operation. */
    fun save() {
        refreshTime()
        val current = state.value
        if (!current.canSave || completion.value != null) return
        val desired = current.selectedOwnerIds.toSet()
        reading?.cancel()
        reading = null
        savedState["attempting"] = true
        publish(current.copy(isSaving = true, saveFailed = false, contextChanged = false))
        viewModelScope.launch {
            try {
                lotteries.updateParticipants(lotteryId, actorId, desired)
                currentCoroutineContext().ensureActive()
                retainLocal = false
                savedState["attempting"] = false
                savedState["completed"] = true
                publish(
                    state.value.copy(
                        lottery = state.value.lottery?.copy(participantOwnerIds = desired),
                        selectedOwnerIds = desired,
                        saveFailed = false,
                        contextChanged = false,
                    ),
                )
                mutableCompletion.value = lotteryId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                val conflict = failure is PersistenceFailure && failure.reason == PersistenceFailure.Reason.CONFLICT
                val inactive = failure is PersistenceFailure && failure.reason == PersistenceFailure.Reason.INACTIVE
                if (conflict || inactive) {
                    retainLocal = true
                    try {
                        refreshContext(profileRecords)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        publish(state.value.copy(targetLoad = LotteryTargetLoad.FAILURE))
                    }
                }
                publish(state.value.copy(contextChanged = conflict, saveFailed = !conflict))
            } finally {
                if (viewModelScope.isActive) {
                    publish(state.value.copy(isSaving = false))
                    if (completion.value == null) observeContext()
                }
            }
        }
    }

    /** Rechecks the published registration boundary without writing or dropping input. */
    fun refreshTime() {
        publish(state.value)
    }

    /** Supplies one resumed-route delay until published membership freezes. */
    fun millisUntilFreeze(): Long? {
        val current = state.value
        val lottery = current.lottery ?: return null
        if (!current.canEdit || lottery.publishedAt == null) return null
        val remaining = Duration.between(clock.instant(), lottery.configuration.registrationOpensAt)
        return (remaining.toMillis() + if (remaining.nano % 1_000_000 == 0) 0 else 1).coerceAtLeast(1)
    }

    /** Requests confirmation only when leaving would discard local membership edits. */
    fun requestExit() {
        val current = state.value
        if (current.isSaving) return
        if (current.selectedOwnerIds != current.savedOwnerIds) mutableDiscard.value = true else mutableExit.value = true
    }

    /** Keeps the local picker when discard confirmation is cancelled. */
    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    /** Abandons local membership after explicit confirmation without writing. */
    fun confirmDiscard() {
        if (state.value.isSaving) return
        mutableDiscard.value = false
        mutableExit.value = true
    }

    /** Consumes one explicit return to the saved reader. */
    fun acknowledgeExit() {
        mutableExit.value = false
    }

    /** Consumes a confirmed membership-save return once. */
    fun acknowledgeCompletion() {
        savedState["completed"] = false
        mutableCompletion.value = null
    }

    /** Reads membership and allocation ownership without comparing an editor revision.
     * @param profiles Latest readable profile table for labels and active new choices.
     */
    private suspend fun refreshContext(profiles: List<Account>) {
        val lottery = lotteries.get(lotteryId)
        currentCoroutineContext().ensureActive()
        if (lottery == null || lottery.id != lotteryId || actorId !in lottery.participantOwnerIds ||
            lottery.cancellation != null
        ) {
            publish(state.value.copy(lottery = lottery, targetLoad = LotteryTargetLoad.UNAVAILABLE))
            return
        }
        val offers = lotteries.getOffers(lotteryId)
        val sites = offers.map { checkNotNull(campsites.get(it.campsiteId)) }
        currentCoroutineContext().ensureActive()
        profileRecords = profiles
        allocatedOwners = sites.map { it.ownerId }.toSet()
        allocatedSitesPublished = sites.all { it.isPublished }
        val desired =
            if (retainLocal || state.value.isSaving) {
                state.value.selectedOwnerIds + lottery.ownerId
            } else {
                lottery.participantOwnerIds
            }
        publish(state.value.copy(lottery = lottery, targetLoad = LotteryTargetLoad.READY, selectedOwnerIds = desired))
    }

    /** Derives current permissions and row facts from confirmed context, then retains only primitives.
     * @param value Latest local input, saved context and operation outcome.
     */
    private fun publish(value: CollaboratingOwnersUiState) {
        val lottery = value.lottery
        val members = lottery?.participantOwnerIds.orEmpty()
        val canEdit =
            value.targetLoad == LotteryTargetLoad.READY && lottery != null &&
                lottery.ownerId == actorId && actorId in members && lottery.cancellation == null &&
                (lottery.publishedAt == null || clock.instant() < lottery.configuration.registrationOpensAt)
        val profiles = profileRecords.associateBy { it.id }
        val needsPublication = members.size == 1 && value.selectedOwnerIds.size > 1 && !allocatedSitesPublished
        val validAdditions = (value.selectedOwnerIds - members).all { validOwner(profiles[it]) }
        val owners = choices(value, profiles, canEdit)
        val next =
            value.copy(
                savedOwnerIds = members,
                owners = owners,
                canEdit = canEdit,
                canSave =
                    canEdit && !value.isSaving && !needsPublication && validAdditions &&
                        lottery.ownerId in value.selectedOwnerIds &&
                        value.selectedOwnerIds.containsAll(
                            allocatedOwners,
                        ),
                needsPublishedCampsites = needsPublication,
            )
        savedState["ownerQuery"] = next.query
        savedState["ownerIds"] = ArrayList(next.selectedOwnerIds)
        savedState["ownerSelection"] = retainLocal
        mutableState.value = next
    }

    /** Keeps saved members first and filters only additional active owners by the local search.
     * @param state Current desired membership and operation feedback.
     * @param profiles Profile table keyed by stable identity.
     * @param editable Whether the actual organizer can change this saved context.
     */
    private fun choices(
        state: CollaboratingOwnersUiState,
        profiles: Map<String, Account>,
        editable: Boolean,
    ): List<CollaboratingOwnerChoice> {
        val lottery = state.lottery
        val retained = lottery?.participantOwnerIds.orEmpty() + state.selectedOwnerIds
        val query = state.query.trim()
        val order =
            compareBy<String> { if (it == lottery?.ownerId) 0 else 1 }
                .thenBy { profiles[it]?.name?.lowercase(Locale.ROOT).orEmpty() }
                .thenBy { it }
        val current = lottery?.participantOwnerIds.orEmpty().sortedWith(order)
        val additional =
            profiles.values
                .filter {
                    validOwner(it) && it.id !in retained &&
                        (it.name.contains(query, true) || it.id.contains(query, true))
                }.map { it.id }
        val ids = current + ((state.selectedOwnerIds - current.toSet()) + additional).sortedWith(order)
        val names =
            profiles.values
                .filter {
                    validOwner(
                        it,
                    ) || it.id in retained
                }.groupBy { it.name.lowercase(Locale.ROOT) }
        return ids.map { id ->
            val profile = profiles[id]
            val selected = id in state.selectedOwnerIds
            CollaboratingOwnerChoice(
                id,
                profile?.takeIf { it.isActive }?.name,
                isOrganizer = id == lottery?.ownerId,
                hasAllocations = id in allocatedOwners,
                canChange =
                    editable && !state.isSaving && id != lottery?.ownerId &&
                        (!selected || id !in allocatedOwners) && (selected || id in current || validOwner(profile)),
                showId = profile != null && names[profile.name.lowercase(Locale.ROOT)].orEmpty().size > 1,
            )
        }
    }

    /** Accepts only active profiles carrying owner capability as new choices.
     * @param account Current readable profile, or null when unavailable.
     */
    private fun validOwner(account: Account?): Boolean =
        account?.isActive == true && Role.OWNER in account.roles
}

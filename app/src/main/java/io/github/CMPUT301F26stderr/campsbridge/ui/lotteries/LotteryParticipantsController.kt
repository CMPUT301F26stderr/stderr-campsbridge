package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** Bounded child reader owned and closed by the saved lottery ViewModel.
 * @param ownerId Current owner whose published parent may be read.
 * @param lotteryId Fixed saved parent identity.
 * @param lotteries Scoped entry observations.
 * @param accounts Referenced profile-label reads.
 * @param clock Shared display-boundary clock.
 * @param scope Parent's serialized presentation coroutine scope.
 * @param initialFilter Restored small state selection.
 * @param filterParticipants Optional reserved exact-state selector; All needs no helper.
 */
internal class LotteryParticipantsController(
    private val ownerId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    initialFilter: EntryState? = null,
    private val filterParticipants: (
        (
            List<LotteryEntry>,
            EntryState?,
        ) -> List<LotteryEntry>
    )? = null,
) : AutoCloseable {
    private val mutableState =
        MutableStateFlow<ParticipantListUiState>(ParticipantListUiState.Unavailable)
    val state = mutableState.asStateFlow()
    private val mutableFilter = MutableStateFlow(initialFilter)
    val selectedState = mutableFilter.asStateFlow()
    private var parent: Lottery? = null
    private var visible = false
    private var closed = false
    private var entries: List<LotteryEntry>? = null
    private var entriesFailed = false
    private var entriesJob: Job? = null
    private var entriesVersion = 0L
    private val names = mutableMapOf<String, ParticipantName>()
    private val nameJobs = mutableMapOf<String, Job>()
    private val nameVersions = mutableMapOf<String, Long>()
    private var nextNameVersion = 0L

    /** Supplies current parent facts without constructing another parent listener.
     * @param lottery Confirmed parent or null while missing/failed.
     */
    fun updateParent(lottery: Lottery?) {
        if (closed) return
        parent =
            lottery?.takeIf {
                it.id == lotteryId && it.ownerId == ownerId && it.publishedAt != null
            }
        if (parent == null) {
            stopReads()
        } else {
            if (mutableFilter.value !in allowedStates()) mutableFilter.value = null
            if (visible && entriesJob == null) startEntries() else render()
        }
    }

    /** Starts/releases child-scoped reads without changing the retained filter.
     * @param visible Whether the actual participant child is present.
     */
    fun setVisible(visible: Boolean) {
        if (closed || this.visible == visible) return
        this.visible = visible
        if (visible && parent != null) startEntries() else stopReads()
    }

    /** Applies a currently safe exact state selection without mutating an entry.
     * @param state Requested stored state or null/All.
     */
    fun selectState(state: EntryState?) {
        if (closed || parent == null || (state != null && state !in allowedStates())) return
        mutableFilter.value = state
        render()
    }

    /** Retries a failed entry source independently of parent and successful labels. */
    fun retryEntries() {
        if (state.value == ParticipantListUiState.Failure && visible &&
            parent != null
        ) {
            startEntries()
        }
    }

    /** Retries only failed profile labels in the current visible rows. */
    fun retryNames() {
        val content = state.value as? ParticipantListUiState.Content ?: return
        for (row in content.rows) {
            if (names[row.accountId] ==
                ParticipantName.Failure
            ) {
                names[row.accountId] = ParticipantName.Loading
            }
        }
        render()
    }

    /** Reprojects display state using the shared current clock. */
    fun refreshTime() {
        if (!closed) render()
    }

    /** Owns one entry listener while preserving independent label facts across source retry. */
    private fun startEntries() {
        val version = ++entriesVersion
        entriesJob?.cancel()
        entries = null
        entriesFailed = false
        mutableState.value = ParticipantListUiState.Loading
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observeEntries(lotteryId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (version != entriesVersion) return@collect
                        entries = rows
                        entriesFailed = false
                        val retained = rows.map { it.accountId }.toSet()
                        for (id in names.keys.toList()) {
                            if (id !in retained) retireName(id, forget = true)
                        }
                        render()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == entriesVersion) {
                        entriesFailed = true
                        render()
                    }
                }
            }
        entriesJob = pending
        pending.start()
    }

    /** Excludes provisional outcome filters until the parent confirms completion. */
    private fun allowedStates(): List<EntryState> =
        EntryState.entries.filter {
            parent?.drawnAt != null || (it != EntryState.OFFERED && it != EntryState.LOST)
        }

    /** Projects private facts; filtering receives no provisional winner/loser information. */
    private fun render() {
        val record = parent
        val current = entries
        when {
            closed || !visible || record == null -> {
                mutableState.value =
                    ParticipantListUiState.Unavailable
            }

            entriesFailed -> {
                mutableState.value = ParticipantListUiState.Failure
            }

            current == null -> {
                mutableState.value = ParticipantListUiState.Loading
            }

            else -> {
                val selected = mutableFilter.value
                val filter = filterParticipants
                if (selected != null && filter == null) {
                    reconcileNames(emptySet())
                    mutableState.value =
                        ParticipantListUiState.Content(
                            emptyList(),
                            allowedStates(),
                            filterPending = true,
                        )
                    return
                }
                val safe =
                    if (record.drawnAt != null) {
                        current
                    } else {
                        current.map { entry ->
                            if (entry.state == EntryState.OFFERED ||
                                entry.state == EntryState.LOST
                            ) {
                                entry.copy(state = EntryState.WAITING)
                            } else {
                                entry
                            }
                        }
                    }
                val filtered = if (selected == null) safe else checkNotNull(filter)(safe, selected)
                val byId = current.associateBy { it.accountId }
                reconcileNames(filtered.map { it.accountId }.toSet())
                mutableState.value =
                    ParticipantListUiState.Content(
                        filtered.map { entry ->
                            val canonical = byId.getValue(entry.accountId)
                            ParticipantRow(
                                canonical.accountId,
                                names[canonical.accountId] ?: ParticipantName.Loading,
                                lotteryMembershipStatus(record, canonical, clock.instant()),
                                canonical.joinedAt,
                            )
                        },
                        allowedStates(),
                    )
            }
        }
    }

    /** Starts only missing visible label reads and cancels reads outside the current selection.
     * @param visibleIds Referenced identities in the current displayed result.
     */
    private fun reconcileNames(visibleIds: Set<String>) {
        for (id in nameJobs.keys.toList()) {
            if (id !in visibleIds) retireName(id, forget = false)
        }
        // Install every job before starting any; immediate results may re-enter render.
        val pending = mutableListOf<Job>()
        for (id in visibleIds) {
            if (id !in names || (names[id] == ParticipantName.Loading && id !in nameJobs)) {
                names[id] = ParticipantName.Loading
                val version = ++nextNameVersion
                nameVersions[id] = version
                val job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val account = accounts.get(id)
                            currentCoroutineContext().ensureActive()
                            if (nameVersions[id] != version) return@launch
                            names[id] =
                                if (account == null || account.id != id || !account.isActive) {
                                    ParticipantName.Unavailable
                                } else {
                                    ParticipantName.Available(account.name)
                                }
                            render()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            if (nameVersions[id] == version) {
                                names[id] = ParticipantName.Failure
                                render()
                            }
                        } finally {
                            if (nameVersions[id] == version) nameJobs.remove(id)
                        }
                    }
                nameJobs[id] = job
                pending.add(job)
            }
        }
        pending.forEach { it.start() }
    }

    /** Invalidates an obsolete label result before cancelling its request.
     * @param id Retired referenced profile.
     * @param forget Whether its entry is no longer in the confirmed source.
     */
    private fun retireName(
        id: String,
        forget: Boolean,
    ) {
        nameVersions.remove(id)
        nameJobs.remove(id)?.cancel()
        if (forget) names.remove(id)
    }

    /** Releases child reads while retaining only the small selected filter and parent context. */
    private fun stopReads() {
        entriesVersion++
        entriesJob?.cancel()
        entriesJob = null
        entries = null
        entriesFailed = false
        nameJobs.values.forEach { it.cancel() }
        nameJobs.clear()
        nameVersions.clear()
        names.clear()
        mutableState.value = ParticipantListUiState.Unavailable
    }

    /** Cancels this child's jobs and prevents retired results from becoming visible. */
    override fun close() {
        closed = true
        stopReads()
    }
}

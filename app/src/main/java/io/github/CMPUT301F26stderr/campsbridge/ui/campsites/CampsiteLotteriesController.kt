package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.time.awaitDeadline
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipController
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipRead
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.lotteryMembershipStatus
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

/** Scoped associated-lottery reader owned by the existing saved campsite model.
 * @param accountId Fixed active entrant.
 * @param campsiteId Fixed saved campsite.
 * @param context Presentation capability supplied by the saved graph.
 * @param lotteries Scoped query and shared membership dependency.
 * @param clock Exact registration time source.
 * @param scope Parent-owned serialized coroutine scope.
 */
internal class CampsiteLotteriesController(
    private val accountId: String,
    private val campsiteId: String,
    private val context: CampsiteDetailContext,
    private val lotteries: LotteryRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val mutableState =
        MutableStateFlow<CampsiteLotteriesUiState>(CampsiteLotteriesUiState.Unavailable)
    val state = mutableState.asStateFlow()
    private var hasParent = false
    private var closed = false
    private var visible = false
    private var associated: List<Lottery>? = null
    private var associationFailed = false
    private var choices = emptyList<Lottery>()
    private var associationJob: Job? = null
    private var associationVersion = 0L
    private var deadlineJob: Job? = null
    private var deadlineVersion = 0L
    private var soleId: String? = null
    private var command: LotteryMembershipController? = null
    private var membershipJob: Job? = null
    private var membershipVersion = 0L
    private var ownEntry: LotteryEntry? = null
    private var membershipKnown = false
    private var membershipFailed = false
    private val offers = mutableMapOf<String, CampsiteLotteryOfferRead>()
    private val offerJobs = mutableMapOf<String, Job>()
    private val offerVersions = mutableMapOf<String, Long>()
    private var nextOfferVersion = 0L

    /** Supplies the presentable parent or retires its dependent readers.
     * @param campsite Actual parent; null when unavailable or failed.
     */
    fun updateCampsite(campsite: Campsite?) {
        if (closed) return
        val eligible =
            context == CampsiteDetailContext.VIEW &&
                campsite?.id == campsiteId && campsite.isPublished
        if (!eligible) {
            retire()
        } else if (hasParent) {
            refreshTime()
        } else {
            hasParent = true
            startAssociation()
        }
    }

    /** Refreshes derived eligibility after foreground or a registration deadline. */
    fun refreshTime() {
        if (!closed && hasParent && associated != null && !associationFailed) reconcile()
    }

    /** Retries only a failed association query. */
    fun retry() {
        if (!closed && hasParent && associationFailed) startAssociation()
    }

    /** Retries only the sole choice's failed own-entry source. */
    fun retryMembership() {
        if (!closed && soleId != null && membershipFailed) startMembership()
    }

    /** Owns allocation listeners only while the actual choices child is visible.
     * @param visible Whether that child is currently composed.
     */
    fun setChoicesVisible(visible: Boolean) {
        if (closed || context != CampsiteDetailContext.VIEW || this.visible == visible) return
        this.visible = visible
        reconcileOffers()
        publish()
    }

    /** Retries one failed visible allocation.
     * @param lotteryId Displayed row identity.
     */
    fun retryOffer(lotteryId: String) {
        if (closed || !visible || offers[lotteryId] != CampsiteLotteryOfferRead.Failure) return
        stopOffer(lotteryId)
        reconcileOffers()
        publish()
    }

    /** Rechecks and joins only the displayed sole target.
     * @param lotteryId Identity supplied by the rendered action.
     */
    fun join(lotteryId: String) {
        refreshTime()
        if (!closed && soleId == lotteryId) command?.join()
    }

    /** Requests withdrawal confirmation for the displayed sole target.
     * @param lotteryId Identity supplied by the rendered action.
     */
    fun requestLeave(lotteryId: String) {
        refreshTime()
        if (!closed && soleId == lotteryId) command?.requestLeave()
    }

    /** Dismisses the matching unsubmitted confirmation without a write.
     * @param lotteryId Identity supplied by the rendered dialog.
     */
    fun dismissLeave(lotteryId: String) {
        if (!closed && soleId == lotteryId) command?.dismissLeave()
    }

    /** Rechecks and submits the matching explicit withdrawal.
     * @param lotteryId Identity supplied by the rendered dialog.
     */
    fun confirmLeave(lotteryId: String) {
        refreshTime()
        if (!closed && soleId == lotteryId) command?.confirmLeave()
    }

    /** Releases every child reader and pending command. */
    override fun close() {
        if (closed) return
        retire()
        closed = true
    }

    /** Installs the scoped listener before starting it, preserving immediate-source safety. */
    private fun startAssociation() {
        val version = ++associationVersion
        associationJob?.cancel()
        associated = null
        associationFailed = false
        choices = emptyList()
        stopDeadline()
        replaceSole(null)
        stopOffers()
        mutableState.value = CampsiteLotteriesUiState.Loading
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observePublishedForCampsite(campsiteId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (version != associationVersion || closed) return@collect
                        associated = rows
                        reconcile()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version != associationVersion || closed) return@launch
                    associated = null
                    associationFailed = true
                    choices = emptyList()
                    stopDeadline()
                    replaceSole(null)
                    stopOffers()
                    publish()
                }
            }
        associationJob = pending
        pending.start()
    }

    /** Derives current choices once and supplies the shared command owner with its actual facts. */
    private fun reconcile() {
        val rows = associated ?: return
        val now = clock.instant()
        choices =
            rows
                .filter { it.registrationIsOpen(now) }
                .sortedWith(compareBy({ it.configuration.registrationClosesAt }, { it.id }))
        replaceSole(choices.singleOrNull()?.id)
        supplyMembershipFacts()
        reconcileOffers()
        scheduleDeadline()
        publish()
    }

    /** Retires a changed command tuple before creating its replacement.
     * @param id Current sole open choice, or null for zero/several choices.
     */
    private fun replaceSole(id: String?) {
        if (soleId == id) return
        membershipVersion++
        membershipJob?.cancel()
        membershipJob = null
        command?.close()
        command = null
        soleId = id
        ownEntry = null
        membershipKnown = false
        membershipFailed = false
        if (id != null) {
            command = LotteryMembershipController(accountId, id, lotteries, clock, scope, ::publish)
            startMembership()
        }
    }

    /** Reads only the active account for the sole current target; unknown never means absent. */
    private fun startMembership() {
        val id = soleId ?: return
        val version = ++membershipVersion
        membershipJob?.cancel()
        ownEntry = null
        membershipKnown = false
        membershipFailed = false
        supplyMembershipFacts()
        publish()
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    lotteries.observeForEntrant(accountId).collect { rows ->
                        currentCoroutineContext().ensureActive()
                        if (version != membershipVersion || id != soleId || closed) return@collect
                        ownEntry =
                            rows.firstOrNull { it.lotteryId == id && it.accountId == accountId }
                        membershipKnown = true
                        supplyMembershipFacts()
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version != membershipVersion || id != soleId || closed) return@launch
                    membershipKnown = false
                    membershipFailed = true
                    supplyMembershipFacts()
                    publish()
                }
            }
        membershipJob = pending
        pending.start()
    }

    /** Updates the fixed command's knowledge without starting a query or copying its guard. */
    private fun supplyMembershipFacts() {
        command?.updateFacts(choices.singleOrNull(), ownEntry, membershipKnown)
    }

    /** Reconciles actual visible row IDs; every job is installed before any starts. */
    private fun reconcileOffers() {
        val ids =
            if (visible && hasParent &&
                !associationFailed
            ) {
                choices.map { it.id }.toSet()
            } else {
                emptySet()
            }
        for (id in offerJobs.keys.toList()) if (id !in ids) stopOffer(id)
        val pending =
            ids.filter { it !in offerJobs }.map { id ->
                val version = ++nextOfferVersion
                offerVersions[id] = version
                offers[id] = CampsiteLotteryOfferRead.Loading
                val job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            lotteries.observeOffers(id).collect { rows ->
                                currentCoroutineContext().ensureActive()
                                if (offerVersions[id] != version || closed) return@collect
                                offers[id] = rows.firstOrNull { it.campsiteId == campsiteId }?.let {
                                    CampsiteLotteryOfferRead.Ready(it)
                                } ?: CampsiteLotteryOfferRead.Unavailable
                                publish()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            if (offerVersions[id] != version || closed) return@launch
                            offers[id] = CampsiteLotteryOfferRead.Failure
                            publish()
                        }
                    }
                offerJobs[id] = job
                job
            }
        pending.forEach { it.start() }
    }

    /** Retires one allocation token before cancelling its source.
     * @param id Actual row being removed or retried.
     */
    private fun stopOffer(id: String) {
        offerVersions.remove(id)
        offerJobs.remove(id)?.cancel()
        offers.remove(id)
    }

    /** Releases all visible-row sources without changing association knowledge. */
    private fun stopOffers() {
        offerJobs.keys.toList().forEach { stopOffer(it) }
    }

    /** Starts one exact-boundary wait and never triggers a persistence operation. */
    private fun scheduleDeadline() {
        stopDeadline()
        val now = clock.instant()
        val deadline =
            associated
                .orEmpty()
                .filter {
                    it.publishedAt != null && it.cancellation == null && it.drawnAt == null
                }.flatMap {
                    listOf(
                        it.configuration.registrationOpensAt,
                        it.configuration.registrationClosesAt,
                    )
                }.filter { it > now }
                .minOrNull() ?: return
        val version = deadlineVersion
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                if (version != deadlineVersion || closed) return@launch
                deadlineJob = null
                refreshTime()
            }
        deadlineJob = pending
        pending.start()
    }

    /** Invalidates a replaced registration wait before cancellation. */
    private fun stopDeadline() {
        deadlineVersion++
        deadlineJob?.cancel()
        deadlineJob = null
    }

    /** Projects changed read/command facts without feeding them back into a child controller. */
    private fun publish() {
        if (closed) return
        mutableState.value =
            when {
                !hasParent -> {
                    CampsiteLotteriesUiState.Unavailable
                }

                associationFailed -> {
                    CampsiteLotteriesUiState.Failure
                }

                associated == null -> {
                    CampsiteLotteriesUiState.Loading
                }

                else -> {
                    val sole = choices.singleOrNull()
                    val membership =
                        when {
                            sole == null -> {
                                LotteryMembershipRead.NotApplicable
                            }

                            membershipFailed -> {
                                LotteryMembershipRead.Failure
                            }

                            !membershipKnown -> {
                                LotteryMembershipRead.Loading
                            }

                            else -> {
                                LotteryMembershipRead.Content(
                                    lotteryMembershipStatus(sole, ownEntry, clock.instant()),
                                )
                            }
                        }
                    CampsiteLotteriesUiState.Content(
                        choices.map {
                            CampsiteLotteryChoice(
                                it,
                                offers[it.id] ?: CampsiteLotteryOfferRead.Loading,
                            )
                        },
                        membership,
                        command?.state?.value ?: LotteryMembershipUiState(),
                    )
                }
            }
    }

    /**
     * Releases dependent sources and commands while retaining actual child visibility for retry.
     */
    private fun retire() {
        hasParent = false
        associationVersion++
        associationJob?.cancel()
        associationJob = null
        associated = null
        associationFailed = false
        choices = emptyList()
        stopDeadline()
        replaceSole(null)
        stopOffers()
        mutableState.value = CampsiteLotteriesUiState.Unavailable
    }
}

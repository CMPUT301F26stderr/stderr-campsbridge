package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resolves display context for visible lottery allocations without owning their primary truth.
 * All calls use the parent ViewModel's main-thread scope. Each referenced campsite has one reader,
 * shared by visible lotteries; the parent remains the sole owner of allocation subscriptions.
 * @param campsites Source of current referenced campsite facts, without a global query.
 * @param scope Parent-owned coroutine lifetime.
 */
internal class LotteryListContextReader(
    private val campsites: CampsiteRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<Map<String, LotteryOffersState>>(emptyMap())
    val state = mutableState.asStateFlow()
    private var visibleIds = emptySet<String>()
    private var offers = emptyMap<String, List<LotteryOffer>>()
    private var offerIssues = emptyMap<String, LotteryReferenceIssue>()
    private val jobs = mutableMapOf<String, Job>()
    private val sites = mutableMapOf<String, Campsite?>()
    private val failed = mutableSetOf<String>()

    /** Reconciles current visible IDs against the parent's confirmed allocation snapshots.
     * @param ids Actual visible resolved lottery identities.
     * @param sources Parent-owned confirmed allocation lists; absence means unresolved.
     * @param issues Parent-owned loading/failure outcomes.
     */
    fun update(
        ids: Set<String>,
        sources: Map<String, List<LotteryOffer>>,
        issues: Map<String, LotteryReferenceIssue>,
    ) {
        visibleIds = ids.toSet()
        offers = sources.filterKeys { it in ids }.mapValues { it.value.toList() }
        offerIssues = issues.filterKeys { it in ids }
        val needed =
            offers.values
                .flatten()
                .map { it.campsiteId }
                .toSet()
        for (id in jobs.keys.toList()) {
            if (id !in needed) {
                jobs.remove(id)?.cancel()
                sites.remove(id)
                failed.remove(id)
            }
        }
        for (id in needed) if (id !in jobs) observe(id)
        publish()
    }

    /** Retries failed campsite sources without restarting successful sibling readers. */
    fun retry() {
        for (id in failed.toList()) observe(id)
    }

    /** Observes one referenced site; confirmed absence stays distinct from a failed read.
     * @param id Actual campsite identity from a current allocation.
     */
    private fun observe(id: String) {
        jobs.remove(id)?.cancel()
        sites.remove(id)
        failed.remove(id)
        publish()
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    campsites.observe(id).collect { site ->
                        currentCoroutineContext().ensureActive()
                        sites[id] = site?.takeIf { it.id == id }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    sites.remove(id)
                    failed.add(id)
                    publish()
                }
            }
        jobs[id] = pending
        pending.start()
    }

    /** Publishes display outcomes only; missing context never changes primary list membership. */
    private fun publish() {
        mutableState.value =
            visibleIds.associateWith { id ->
                val rows = offers[id]
                when {
                    offerIssues[id] == LotteryReferenceIssue.FAILURE -> {
                        LotteryOffersState.Failure
                    }

                    rows == null -> {
                        LotteryOffersState.Loading
                    }

                    rows.any { it.campsiteId in failed } -> {
                        LotteryOffersState.Failure
                    }

                    rows.any { !sites.containsKey(it.campsiteId) } -> {
                        LotteryOffersState.Loading
                    }

                    else -> {
                        LotteryOffersState.Content(
                            rows.map { LotteryOfferSummary(it, sites[it.campsiteId]) },
                        )
                    }
                }
            }
    }

    /** Releases all display subscriptions without cancelling the parent scope. */
    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        sites.clear()
        failed.clear()
        visibleIds = emptySet()
        offers = emptyMap()
        offerIssues = emptyMap()
        publish()
    }
}

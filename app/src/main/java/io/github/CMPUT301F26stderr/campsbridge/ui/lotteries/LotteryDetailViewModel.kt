package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
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

/** Reads an actual saved owner target without inventing a completion snapshot.
 * @param ownerId Current owner allowed to read this management target.
 * @param lotteryId Actual saved identity carried by navigation.
 * @param lotteries Saved parent and live allocation source.
 * @param clock Deterministic current time for derived display boundaries.
 * @param campsites Names and zones for referenced allocations.
 */
class LotteryDetailViewModel(
    private val ownerId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LotteryDetailUiState>(LotteryDetailUiState.Loading)
    val state = mutableState.asStateFlow()

    private var recordJob: Job? = null
    private var offerJob: Job? = null
    private var timeJob: Job? = null
    private var recordVersion = 0L
    private var offerVersion = 0L

    init {
        observeRecord()
    }

    /** Recalculates read-only lifecycle after foreground or deadline changes. */
    fun refreshTime() {
        val current = state.value as? LotteryDetailUiState.Content ?: return
        mutableState.value =
            current.copy(lifecycle = lotteryLifecycle(current.lottery, clock.instant()))
        scheduleTime()
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
        stopOffers()
        stopTime()
        mutableState.value = LotteryDetailUiState.Loading
        recordJob =
            viewModelScope.launch {
                try {
                    lotteries.observe(lotteryId).collect { record ->
                        if (version != recordVersion) return@collect
                        if (record == null || record.id != lotteryId || record.ownerId != ownerId) {
                            stopOffers()
                            stopTime()
                            mutableState.value = LotteryDetailUiState.Unavailable
                        } else {
                            val prior = state.value as? LotteryDetailUiState.Content
                            mutableState.value =
                                LotteryDetailUiState.Content(
                                    record,
                                    prior?.offers ?: LotteryOffersState.Loading,
                                    canContinue =
                                        record.publishedAt == null && record.cancellation == null,
                                    lifecycle = lotteryLifecycle(record, clock.instant()),
                                )
                            scheduleTime()
                            if (prior == null) observeOffers()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == recordVersion) {
                        stopOffers()
                        stopTime()
                        mutableState.value = LotteryDetailUiState.Failure
                    }
                }
            }
    }

    /**
     * Owns live allocations and reads their actual site labels without dropping missing
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
                                    site?.details?.name,
                                    site?.details?.timeZone,
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
        val configuration = current.lottery.configuration
        val deadline =
            when (current.lifecycle) {
                LotteryLifecycle.UPCOMING -> configuration.registrationOpensAt
                LotteryLifecycle.REGISTRATION_OPEN -> configuration.registrationClosesAt
                LotteryLifecycle.AWAITING_BOOKING -> configuration.bookingOpensAt
                LotteryLifecycle.BOOKING_OPEN -> configuration.bookingClosesAt
                else -> null
            } ?: return
        val pending =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                awaitDeadline(clock, deadline)
                refreshTime()
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
        recordVersion++
        recordJob?.cancel()
        stopOffers()
        stopTime()
        super.onCleared()
    }
}

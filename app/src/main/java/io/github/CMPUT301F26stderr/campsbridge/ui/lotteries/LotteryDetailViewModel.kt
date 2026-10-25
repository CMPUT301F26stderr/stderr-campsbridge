package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads an actual saved owner target without inventing a completion snapshot.
 * @param ownerId Current owner allowed to read this management target.
 * @param lotteryId Actual saved identity carried by navigation.
 * @param lotteries Saved parent and live allocation source.
 * @param campsites Names and zones for referenced allocations.
 */
class LotteryDetailViewModel(
    private val ownerId: String,
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LotteryDetailUiState>(LotteryDetailUiState.Loading)
    val state = mutableState.asStateFlow()

    private var recordJob: Job? = null
    private var offerJob: Job? = null
    private var recordVersion = 0L
    private var offerVersion = 0L

    init {
        observeRecord()
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
        mutableState.value = LotteryDetailUiState.Loading
        recordJob =
            viewModelScope.launch {
                try {
                    lotteries.observe(lotteryId).collect { record ->
                        if (version != recordVersion) return@collect
                        if (record == null || record.id != lotteryId || record.ownerId != ownerId) {
                            stopOffers()
                            mutableState.value = LotteryDetailUiState.Unavailable
                        } else {
                            val prior = state.value as? LotteryDetailUiState.Content
                            mutableState.value =
                                LotteryDetailUiState.Content(
                                    record,
                                    prior?.offers ?: LotteryOffersState.Loading,
                                    canContinue =
                                        record.publishedAt == null && record.cancellation == null,
                                )
                            if (prior == null) observeOffers()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version == recordVersion) {
                        stopOffers()
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
        super.onCleared()
    }
}

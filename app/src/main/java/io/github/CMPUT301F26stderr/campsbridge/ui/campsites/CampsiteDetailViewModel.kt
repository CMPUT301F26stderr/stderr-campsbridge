package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DateTimeException
import java.time.YearMonth
import java.time.ZoneId

/** Reads one saved campsite; each caller context has a separate graph-owned instance.
 * @param accountId Active identity.
 * @param campsiteId Saved listing identity.
 * @param context Caller-validated presentation.
 * @param campsites Listing read dependency.
 * @param images Attachment read dependency.
 * @param accounts Optional owner-label source.
 * @param reviews Optional rating source.
 * @param savedState Small retained calendar inputs.
 * @param clock Initial calendar month in the saved campsite's zone.
 * @param lotteries Scoped associated lotteries and entrant operations.
 */
class CampsiteDetailViewModel(
    private val accountId: String,
    private val campsiteId: String,
    val context: CampsiteDetailContext,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val accounts: AccountRepository,
    private val reviews: ReviewRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
    private val lotteries: LotteryRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<CampsiteDetailUiState>(CampsiteDetailUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutablePhoto = MutableStateFlow(ImageSlotState())
    val photoState = mutablePhoto.asStateFlow()
    private val mutableOwner = MutableStateFlow<CampsiteOwnerState>(CampsiteOwnerState.Loading)
    val ownerState = mutableOwner.asStateFlow()
    private val mutableRating = MutableStateFlow<RatingState>(RatingState.Loading)
    val ratingState = mutableRating.asStateFlow()
    private val mutableMonth = MutableStateFlow(restoredMonth())
    val month = mutableMonth.asStateFlow()
    private val mutableInventory =
        MutableStateFlow<CampsiteInventoryUiState>(CampsiteInventoryUiState.Loading)
    val inventory = mutableInventory.asStateFlow()
    private val campsiteLotteries =
        CampsiteLotteriesController(
            accountId,
            campsiteId,
            context,
            lotteries,
            clock,
            viewModelScope,
        )
    val lotteryState = campsiteLotteries.state
    private var observation: Job? = null
    private var photo: ImageSlotController? = null
    private var photoJob: Job? = null
    private var ownerJob: Job? = null
    private var ratingJob: Job? = null
    private var calendarJob: Job? = null
    private var ownerId: String? = null
    private var calendarMonth: YearMonth? = null
    private var section = CampsiteDetailSection.OVERVIEW
    private var sourceVersion = 0L
    private var ownerVersion = 0L
    private var ratingVersion = 0L
    private var calendarVersion = 0L

    init {
        observe()
    }

    /** Recalculates registration after foreground return. */
    fun refreshTime() =
        campsiteLotteries.refreshTime()

    /** Retries only the failed campsite-lottery association. */
    fun retryLotteries() =
        campsiteLotteries.retry()

    /** Retries only the sole choice's failed own-entry source. */
    fun retryLotteryMembership() =
        campsiteLotteries.retryMembership()

    /** Sets actual choice-child visibility.
     * @param visible Whether the child is composed.
     */
    fun setLotteryChoicesVisible(visible: Boolean) =
        campsiteLotteries.setChoicesVisible(visible)

    /** Retries one failed allocation label.
     * @param lotteryId Actual displayed row identity.
     */
    fun retryLotteryOffer(lotteryId: String) =
        campsiteLotteries.retryOffer(lotteryId)

    /** Joins the displayed sole choice after rechecking current facts.
     * @param lotteryId Identity supplied by the rendered action.
     */
    fun joinLottery(lotteryId: String) =
        campsiteLotteries.join(lotteryId)

    /** Requests explicit withdrawal confirmation for the displayed choice.
     * @param lotteryId Identity supplied by the rendered action.
     */
    fun requestLeaveLottery(lotteryId: String) =
        campsiteLotteries.requestLeave(lotteryId)

    /** Dismisses the matching unsubmitted confirmation.
     * @param lotteryId Identity supplied by the rendered dialog.
     */
    fun dismissLeaveLottery(lotteryId: String) =
        campsiteLotteries.dismissLeave(lotteryId)

    /** Rechecks and submits the matching withdrawal.
     * @param lotteryId Identity supplied by the rendered dialog.
     */
    fun confirmLeaveLottery(lotteryId: String) =
        campsiteLotteries.confirmLeave(lotteryId)

    /** Retries the failed record observation. */
    fun retry() {
        if (state.value == CampsiteDetailUiState.Failure) observe()
    }

    /** Retries an attachment or URL read without changing the listing. */
    fun retryPhoto() {
        photo?.retry()
    }

    /** Selects the actual registered child without changing persisted facts.
     * @param section Visible read-only destination.
     */
    fun selectSection(section: CampsiteDetailSection) {
        if (context == CampsiteDetailContext.MANAGE || this.section == section) return
        this.section = section
        if (section == CampsiteDetailSection.AVAILABILITY) startCalendar() else stopCalendar()
    }

    /** Changes the visible calendar range.
     * @param month Requested local calendar month.
     */
    fun changeMonth(month: YearMonth) {
        if (section != CampsiteDetailSection.AVAILABILITY ||
            state.value !is CampsiteDetailUiState.Content || mutableMonth.value == month
        ) {
            return
        }
        mutableMonth.value = month
        savedState[MONTH_KEY] = month.toString()
        startCalendar()
    }

    /** Retries only a failed supplemental owner read. */
    fun retryOwner() {
        if (ownerState.value == CampsiteOwnerState.Failure) {
            ownerId?.let { startOwner(it) }
        }
    }

    /** Retries only a failed rating source. */
    fun retryRatings() {
        if (ratingState.value == RatingState.Failure &&
            state.value is CampsiteDetailUiState.Content
        ) {
            startRatings()
        }
    }

    /** Retries the current visible-month read. */
    fun retryInventory() {
        if (inventory.value == CampsiteInventoryUiState.Failure) {
            calendarMonth = null
            startCalendar()
        }
    }

    /** Observes the saved identity without a global scan or placeholder data. */
    private fun observe() {
        val version = ++sourceVersion
        observation?.cancel()
        mutableState.value = CampsiteDetailUiState.Loading
        observation =
            viewModelScope.launch {
                try {
                    campsites.observe(campsiteId).collect { site ->
                        if (version != sourceVersion) return@collect
                        if (site == null || !canPresent(site)) {
                            stopPhoto()
                            stopSupplementalReads()
                            mutableState.value = CampsiteDetailUiState.Unavailable
                        } else {
                            mutableState.value = CampsiteDetailUiState.Content(site)
                            campsiteLotteries.updateCampsite(site)
                            startPhoto()
                            if (context != CampsiteDetailContext.MANAGE) {
                                if (mutableMonth.value == null) {
                                    mutableMonth.value =
                                        YearMonth.now(
                                            clock.withZone(ZoneId.of(site.details.timeZone)),
                                        )
                                }
                                if (ownerId != site.ownerId) startOwner(site.ownerId)
                                if (ratingJob == null) startRatings()
                                startCalendar()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version != sourceVersion) return@launch
                    stopPhoto()
                    stopSupplementalReads()
                    mutableState.value = CampsiteDetailUiState.Failure
                }
            }
    }

    /** Checks presentation compatibility; route capability checks are separate.
     * @param site Actual observed record.
     */
    private fun canPresent(site: Campsite): Boolean =
        site.id == campsiteId &&
            when (context) {
                CampsiteDetailContext.MANAGE, CampsiteDetailContext.PREVIEW -> {
                    site.ownerId == accountId && site.removal == null
                }

                CampsiteDetailContext.VIEW -> {
                    site.isPublished
                }

                CampsiteDetailContext.INSPECT -> {
                    true
                }
            }

    /** Starts one attachment owner only after the parent is known to be presentable. */
    private fun startPhoto() {
        if (photo != null) return
        val controller =
            ImageSlotController(
                images,
                ImageTarget(ImageTargetType.CAMPSITE, campsiteId),
                viewModelScope,
            )
        photo = controller
        photoJob =
            viewModelScope.launch {
                controller.state.collect { mutablePhoto.value = it }
            }
    }

    /** Releases stale attachment reads and their transient display reference. */
    private fun stopPhoto() {
        photoJob?.cancel()
        photo?.close()
        photo = null
        mutablePhoto.value = ImageSlotState()
    }

    /** Observes exactly the current owner and ignores callbacks from replaced identity reads.
     * @param id Authoritative owner identity from the saved listing.
     */
    private fun startOwner(id: String) {
        val version = ++ownerVersion
        ownerJob?.cancel()
        ownerId = id
        mutableOwner.value = CampsiteOwnerState.Loading
        ownerJob =
            viewModelScope.launch {
                try {
                    accounts.observe(id).collect { account ->
                        if (version != ownerVersion) return@collect
                        mutableOwner.value =
                            if (account?.id == id && account.isActive) {
                                CampsiteOwnerState.Ready(account.name)
                            } else {
                                CampsiteOwnerState.Unavailable
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version == ownerVersion) mutableOwner.value = CampsiteOwnerState.Failure
                }
            }
    }

    /** Summarizes this listing's real ratings independently of owner and photo results. */
    private fun startRatings() {
        val version = ++ratingVersion
        ratingJob?.cancel()
        mutableRating.value = RatingState.Loading
        ratingJob =
            viewModelScope.launch {
                try {
                    reviews.observeReviews(campsiteId).collect { rows ->
                        if (version == ratingVersion) {
                            mutableRating.value = RatingState.Ready(summarizeRatings(rows))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version == ratingVersion) mutableRating.value = RatingState.Failure
                }
            }
    }

    /** Reads one visible month only; failed and unresolved ranges retain their own outcomes. */
    private fun startCalendar() {
        if (section != CampsiteDetailSection.AVAILABILITY ||
            state.value !is CampsiteDetailUiState.Content
        ) {
            return
        }
        val month = mutableMonth.value ?: return
        if (calendarMonth == month) return
        stopCalendar()
        calendarMonth = month
        val version = calendarVersion
        calendarJob =
            viewModelScope.launch {
                try {
                    campsites
                        .observeCalendar(
                            campsiteId,
                            month.atDay(1),
                            month.plusMonths(1).atDay(1),
                        ).collect { nights ->
                            if (version == calendarVersion) {
                                mutableInventory.value = CampsiteInventoryUiState.Content(nights)
                            }
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (version == calendarVersion) {
                        mutableInventory.value = CampsiteInventoryUiState.Failure
                    }
                }
            }
    }

    /** Invalidates retired month callbacks before releasing their listener. */
    private fun stopCalendar() {
        calendarVersion++
        calendarJob?.cancel()
        calendarJob = null
        calendarMonth = null
        mutableInventory.value = CampsiteInventoryUiState.Loading
    }

    /** Clears supplemental readers when the authoritative saved parent cannot be presented. */
    private fun stopSupplementalReads() {
        campsiteLotteries.updateCampsite(null)
        ownerVersion++
        ratingVersion++
        ownerJob?.cancel()
        ratingJob?.cancel()
        ownerJob = null
        ratingJob = null
        ownerId = null
        mutableOwner.value = CampsiteOwnerState.Loading
        mutableRating.value = RatingState.Loading
        stopCalendar()
    }

    /**
     * Restores a small calendar input; malformed local state falls back to the actual site
     * zone.
     */
    private fun restoredMonth(): YearMonth? =
        try {
            savedState.get<String>(MONTH_KEY)?.let(YearMonth::parse)
        } catch (_: DateTimeException) {
            null
        }

    /** Cancels pending reads without replacing the final observable snapshot on clear. */
    override fun onCleared() {
        campsiteLotteries.close()
        sourceVersion++
        ownerVersion++
        ratingVersion++
        calendarVersion++
        observation?.cancel()
        ownerJob?.cancel()
        ratingJob?.cancel()
        calendarJob?.cancel()
        photoJob?.cancel()
        photo?.close()
        super.onCleared()
    }

    /** Primitive state keys owned by this saved-detail graph. */
    private companion object {
        const val MONTH_KEY = "campsiteDetailMonth"
    }
}

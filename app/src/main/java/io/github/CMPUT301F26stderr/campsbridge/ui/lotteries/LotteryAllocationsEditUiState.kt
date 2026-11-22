package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer

/** Keeps required saved context separate from local owned-offer editing. */
sealed interface LotteryAllocationsEditUiState {
    /** Required parent, offers and ownership facts are still being read. */
    data object Loading : LotteryAllocationsEditUiState

    /** A required read failed, retaining local input for explicit recovery. */
    data object Failure : LotteryAllocationsEditUiState

    /** The saved target is absent or unrelated to this editor. */
    data object Unavailable : LotteryAllocationsEditUiState

    /** Confirmed context and the complete local set owned by this actor.
     * @param lottery Latest saved membership and lifecycle facts.
     * @param offers Local applied owned offers, including an explicitly cleared set.
     * @param campsites Known owned choices, including retained unavailable referenced listings.
     * @param isEditable Whether membership and the registration boundary permit changes.
     * @param allocation Temporary calendar state, owned independently of applied offers.
     * @param selectedCampsiteId Current working calendar identity.
     * @param isSaving Whether replacement awaits acknowledgment.
     * @param saveFailed Whether the last explicit write failed.
     * @param contextChanged Whether conflict recovery requires review and explicit Save.
     * @param canSave Whether the complete desired set and context are known and eligible.
     * @param isDirty Whether local offers differ from the saved owned baseline.
     * @param savedOffers Confirmed current owned allocations, shown separately during conflict review.
     */
    data class Content(
        val lottery: Lottery,
        val offers: List<LotteryOffer>,
        val campsites: List<Campsite>,
        val isEditable: Boolean,
        val allocation: LotteryAllocationUiState? = null,
        val selectedCampsiteId: String? = null,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val contextChanged: Boolean = false,
        val canSave: Boolean = false,
        val isDirty: Boolean = false,
        val savedOffers: List<LotteryOffer> = emptyList(),
    ) : LotteryAllocationsEditUiState
}

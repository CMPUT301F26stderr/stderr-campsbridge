package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import java.time.Instant

/** One referenced profile label; failure or removal never removes its participation row. */
sealed interface ParticipantName {
    /** The referenced profile read is pending. */
    data object Loading : ParticipantName

    /** The referenced profile is absent, removed or has an unrelated identity. */
    data object Unavailable : ParticipantName

    /** The label read failed and can be retried separately from entries. */
    data object Failure : ParticipantName

    /** Current saved display name.
     * @param name Confirmed name belonging to this row's account identity.
     */
    data class Available(
        val name: String,
    ) : ParticipantName
}

/** Display-safe participation facts; provisional raw winner/loser states stay private.
 * @param accountId Canonical entry identity retained even without a profile.
 * @param name Independent referenced profile read.
 * @param status Displayable participation state gated by parent draw completion.
 * @param joinedAt Original current-entry joining time.
 */
data class ParticipantRow(
    val accountId: String,
    val name: ParticipantName,
    val status: LotteryMembershipStatus,
    val joinedAt: Instant,
)

/** Independent participant child outcome; parent and offers have their own read owners. */
sealed interface ParticipantListUiState {
    /** The child is hidden or has no readable owned published parent. */
    data object Unavailable : ParticipantListUiState

    /** No current confirmed entry emission yet. */
    data object Loading : ParticipantListUiState

    /** Entry observation failed; profile-label failures remain inside their rows. */
    data object Failure : ParticipantListUiState

    /** Confirmed rows and currently permitted filtering.
     * @param rows Display-safe visible rows in source order.
     * @param allowedStates Stored states currently safe to select; null/All is always available.
     * @param filterPending A nonnull filter awaits the reserved teammate function.
     */
    data class Content(
        val rows: List<ParticipantRow>,
        val allowedStates: List<EntryState>,
        val filterPending: Boolean = false,
    ) : ParticipantListUiState
}

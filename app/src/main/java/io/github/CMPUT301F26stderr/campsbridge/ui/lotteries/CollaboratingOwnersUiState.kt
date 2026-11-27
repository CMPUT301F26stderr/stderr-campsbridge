/*
 * A current or eligible new owner, with explicit identity and membership restrictions.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery

/** A current or eligible new owner, with explicit identity and membership restrictions.
 * @param id Saved account identity.
 * @param name Current display name, absent when the profile is unavailable.
 * @param isOrganizer Whether this identity must remain selected.
 * @param hasAllocations Whether saved campsite contributions prevent removal.
 * @param canChange Whether this row accepts the requested checkbox change.
 * @param showId Whether matching names need their distinct IDs displayed.
 */
data class CollaboratingOwnerChoice(
    val id: String,
    val name: String?,
    val isOrganizer: Boolean,
    val hasAllocations: Boolean,
    val canChange: Boolean,
    val showId: Boolean,
)

/** Keeps local membership/query input independent of required saved-context reads.
 * @param targetLoad Required parent, profile and allocation source outcome.
 * @param lottery Latest confirmed saved parent, absent when unavailable.
 * @param query Local search input, retained after a failed action.
 * @param selectedOwnerIds Complete local desired membership, including the organizer.
 * @param savedOwnerIds Confirmed membership shown separately when review is needed.
 * @param owners Current-member context followed by eligible filtered new choices.
 * @param canEdit Whether this organizer can change the current lottery.
 * @param canSave Whether all desired membership and allocation facts permit submission.
 * @param isSaving Whether the explicit membership operation is pending.
 * @param saveFailed Whether the latest explicit submission needs recovery.
 * @param contextChanged Whether refreshed membership/allocations need review.
 * @param needsPublishedCampsites Whether allocated listings must be published before first sharing.
 */
data class CollaboratingOwnersUiState(
    val targetLoad: LotteryTargetLoad = LotteryTargetLoad.LOADING,
    val lottery: Lottery? = null,
    val query: String = "",
    val selectedOwnerIds: Set<String> = emptySet(),
    val savedOwnerIds: Set<String> = emptySet(),
    val owners: List<CollaboratingOwnerChoice> = emptyList(),
    val canEdit: Boolean = false,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val contextChanged: Boolean = false,
    val needsPublishedCampsites: Boolean = false,
)

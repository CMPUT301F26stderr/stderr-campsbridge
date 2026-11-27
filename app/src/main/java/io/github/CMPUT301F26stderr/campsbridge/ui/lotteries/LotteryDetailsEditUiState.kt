/*
 * Separates the required lottery source from local details and save recovery.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery

/** Separates the required lottery source from local details and save recovery. */
sealed interface LotteryDetailsEditUiState {
    /** Reads the existing target before any editing is allowed. */
    data object Loading : LotteryDetailsEditUiState

    /** Keeps a failed source separate from confirmed absence. */
    data object Failure : LotteryDetailsEditUiState

    /** The target cannot be edited by this organizer. */
    data object Unavailable : LotteryDetailsEditUiState

    /** Raw details and the latest confirmed editing context.
     * @param lottery Latest saved facts shown beside any retained local input.
     * @param draft Raw title, description and configuration input.
     * @param configurationEditable Whether published registration has not opened, or this is a draft.
     * @param errors Existing validator/parser feedback.
     * @param isSaving Whether an explicit request is awaiting acknowledgment or recovery.
     * @param saveFailed Whether recovery or the attempted update failed.
     * @param contextChanged Whether refreshed facts require review before another explicit Save.
     * @param isDirty Whether local raw input differs from the editing baseline.
     */
    data class Content(
        val lottery: Lottery,
        val draft: LotteryDraft,
        val configurationEditable: Boolean,
        val errors: LotterySetupErrors = LotterySetupErrors(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val contextChanged: Boolean = false,
        val isDirty: Boolean = false,
    ) : LotteryDetailsEditUiState
}

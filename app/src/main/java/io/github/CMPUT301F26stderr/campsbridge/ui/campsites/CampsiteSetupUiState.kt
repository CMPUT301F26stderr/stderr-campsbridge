package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import java.time.YearMonth

/** Confirmed visible-month inventory, independent of the local requested edits. */
sealed interface CampsiteInventoryUiState {
    /** The displayed range has not loaded, so absence is not known inventory. */
    data object Loading : CampsiteInventoryUiState

    /** The displayed range failed and can be retried without losing form input. */
    data object Failure : CampsiteInventoryUiState

    /** Successfully read range; missing dates are known unoffered nights.
     * @param nights Confirmed records in the displayed month.
     */
    data class Content(
        val nights: List<CalendarNight>,
    ) : CampsiteInventoryUiState
}

/** Unsaved photo choice; upload is deferred until a real saved parent exists. */
sealed interface CampsitePhotoIntent {
    /** Retains the existing saved attachment. */
    data object Keep : CampsitePhotoIntent

    /** Requests removal of the saved attachment during a later explicit save. */
    data object Remove : CampsitePhotoIntent

    /** Holds lazy selected bytes for this ViewModel lifetime only.
     * @param photo Local preview and repeatable upload stream.
     */
    data class Replace(
        val photo: SelectedPhoto,
    ) : CampsitePhotoIntent
}

/** Explicit lead-owned save stages; inventory/publication arrive with their real consumers. */
enum class CampsiteSavePhase { IDLE, RECOVERING, SAVING_DETAILS, SAVING_PHOTO, SAVING_AVAILABILITY }

/** Recoverable action feedback, distinct from initial saved-target loading. */
enum class CampsiteSaveFailure {
    DETAILS,
    PHOTO,
    RECOVERY,
    PHOTO_RESELECTION,
    AVAILABILITY_PENDING,
    AVAILABILITY,
}

/** Saved-target loading, editable input and explicit save outcomes. */
sealed interface CampsiteSetupUiState {
    /** Protects restored dirty input while its initial authoritative read is unresolved.
     * @param previous Loading/failure state restored when the confirmation is dismissed.
     */
    data class ConfirmDiscard(
        val previous: CampsiteSetupUiState,
    ) : CampsiteSetupUiState

    /** Existing target is being read before editing. */
    data object Loading : CampsiteSetupUiState

    /** Target lookup failed; retry does not create another record. */
    data object Failure : CampsiteSetupUiState

    /** Target is absent, removed or belongs to another owner. */
    data object Unavailable : CampsiteSetupUiState

    /** Editable input and explicit draft-save outcomes.
     * @param draft Raw form input and step.
     * @param errors Feedback from the current explicit advancement.
     * @param photoIntent Unsaved attachment choice.
     * @param savedPhoto Confirmed saved attachment display.
     * @param photoReselectionRequired Whether transient replacement content needs reselection.
     * @param photoSelectionFailed Whether the latest picker result could not be read.
     * @param month Visible inventory month.
     * @param discardPrompt Whether explicit abandonment needs confirmation.
     * @param inventory Confirmed visible-month source outcome.
     * @param savePhase Current explicit save operation; non-idle stages block conflicting input.
     * @param saveFailure Last failed/pending stage, retaining the raw draft and confirmed work.
     * @param photoRemovalPending Whether an attempted removal still needs acknowledgement.
     * @param availabilityComparisonPending Whether requested date differences await F08 integration.
     */
    data class Content(
        val draft: CampsiteDraft,
        val errors: CampsiteDetailsInput.Errors = CampsiteDetailsInput.Errors(),
        val photoIntent: CampsitePhotoIntent = CampsitePhotoIntent.Keep,
        val savedPhoto: ImageSlotState = ImageSlotState(),
        val photoReselectionRequired: Boolean = false,
        val photoSelectionFailed: Boolean = false,
        val month: YearMonth,
        val discardPrompt: Boolean = false,
        val inventory: CampsiteInventoryUiState = CampsiteInventoryUiState.Loading,
        val savePhase: CampsiteSavePhase = CampsiteSavePhase.IDLE,
        val saveFailure: CampsiteSaveFailure? = null,
        val photoRemovalPending: Boolean = false,
        val availabilityComparisonPending: Boolean = true,
    ) : CampsiteSetupUiState {
        /** An unresolved attachment mutation must be retried before changing input or leaving. */
        val photoNeedsRecovery: Boolean
            get() = photoRemovalPending || savedPhoto.unconfirmedBlobId != null
    }
}

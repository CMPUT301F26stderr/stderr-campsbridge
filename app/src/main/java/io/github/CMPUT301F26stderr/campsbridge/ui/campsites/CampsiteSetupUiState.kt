package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import java.time.YearMonth

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

/** Explicit save stages retain progress and block conflicting input until the outcome is known. */
enum class CampsiteSavePhase {
    IDLE,
    RECOVERING,
    SAVING_DETAILS,
    SAVING_PHOTO,
    SAVING_AVAILABILITY,
    PUBLISHING,
}

/** Identifies the requested outcome and the footer action that owns progress/retry. */
enum class CampsiteSaveAction { DRAFT, PUBLISH }

/** Recoverable action feedback, distinct from initial saved-target loading. */
enum class CampsiteSaveFailure {
    DETAILS,
    PHOTO,
    RECOVERY,
    PHOTO_RESELECTION,
    AVAILABILITY_PENDING,
    AVAILABILITY,
    PUBLICATION,
}

/** Saved-target loading, editable input and explicit save outcomes. */
sealed interface CampsiteSetupUiState {
    /** Restores a possibly published target before permitting ordinary editing or abandonment.
     * @param isLoading Whether the authoritative target read is active rather than failed.
     */
    data class PublicationRecovery(
        val isLoading: Boolean,
    ) : CampsiteSetupUiState

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
     * @param saveAction Frozen requested outcome, retained for an explicit retry.
     * @param publicationPending Whether server confirmation is required before editing or leaving.
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
        val inventory: CalendarInventoryRead = CalendarInventoryRead.Loading,
        val savePhase: CampsiteSavePhase = CampsiteSavePhase.IDLE,
        val saveFailure: CampsiteSaveFailure? = null,
        val photoRemovalPending: Boolean = false,
        val availabilityComparisonPending: Boolean = true,
        val saveAction: CampsiteSaveAction = CampsiteSaveAction.DRAFT,
        val publicationPending: Boolean = false,
    ) : CampsiteSetupUiState {
        /** An unresolved attachment mutation must be retried before changing input or leaving. */
        val photoNeedsRecovery: Boolean
            get() = photoRemovalPending || savedPhoto.unconfirmedBlobId != null
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput

/** Saved-target reads stay separate from a retained raw details form. */
sealed interface CampsiteEditUiState {
    /** The required saved listing is still loading. */
    data object Loading : CampsiteEditUiState

    /** The listing read failed and can be retried. */
    data object Failure : CampsiteEditUiState

    /** The saved listing cannot be edited by this owner. */
    data object Unavailable : CampsiteEditUiState

    /** Raw input and the explicit details-save outcome.
     * @param draft Local details, never a new listing identity.
     * @param errors Current field validation feedback.
     * @param isSaving Whether an update is awaiting acknowledgment.
     * @param saveFailed Whether the last requested update failed.
     * @param isDirty Whether raw details differ from the editor's saved baseline.
     */
    data class Content(
        val draft: CampsiteDraft,
        val errors: CampsiteDetailsInput.Errors = CampsiteDetailsInput.Errors(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val isDirty: Boolean = false,
    ) : CampsiteEditUiState
}

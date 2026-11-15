package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput

/** A required saved listing read, distinct from an editable local draft. */
sealed interface CampsiteEditUiState {
    data object Loading : CampsiteEditUiState

    data object Failure : CampsiteEditUiState

    data object Unavailable : CampsiteEditUiState

    /** Raw details survive validation and persistence failures; photos save independently. */
    data class Content(
        val draft: CampsiteDraft,
        val errors: CampsiteDetailsInput.Errors = CampsiteDetailsInput.Errors(),
        val isDirty: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : CampsiteEditUiState
}

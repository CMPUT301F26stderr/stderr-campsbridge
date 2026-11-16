package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Supplies actual saved-target states without network access or simulated writes. */
class CampsiteEditPreviewProvider : PreviewParameterProvider<CampsiteEditUiState> {
    override val values: Sequence<CampsiteEditUiState> =
        sequenceOf(
            CampsiteEditUiState.Loading,
            CampsiteEditUiState.Failure,
            CampsiteEditUiState.Unavailable,
            CampsiteEditUiState.Content(campsiteDraft(previewCampsite()), isDirty = true),
            CampsiteEditUiState.Content(campsiteDraft(previewCampsite()), isDirty = true, isSaving = true),
            CampsiteEditUiState.Content(campsiteDraft(previewCampsite()), isDirty = true, saveFailed = true),
        )
}

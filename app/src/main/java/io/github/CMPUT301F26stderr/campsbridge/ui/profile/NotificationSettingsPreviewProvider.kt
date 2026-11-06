package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Supplies passive normal, held, failed and unread preference states without repositories. */
class NotificationSettingsPreviewProvider : PreviewParameterProvider<NotificationPreferenceUiState> {
    override val values: Sequence<NotificationPreferenceUiState> =
        sequenceOf(
            NotificationPreferenceUiState(enabled = true, canChange = true),
            NotificationPreferenceUiState(
                enabled = true,
                requestedEnabled = false,
                isSaving = true,
            ),
            NotificationPreferenceUiState(
                enabled = true,
                requestedEnabled = false,
                saveFailed = true,
                canChange = true,
            ),
            NotificationPreferenceUiState(),
        )
}

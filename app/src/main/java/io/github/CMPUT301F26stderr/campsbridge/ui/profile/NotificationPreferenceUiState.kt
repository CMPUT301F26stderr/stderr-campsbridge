package io.github.CMPUT301F26stderr.campsbridge.ui.profile

/** Acknowledged app-alert preference independent of OS permission and profile/photo drafts.
 * @param enabled Last confirmed display value, or null before an account is available.
 * @param requestedEnabled Pending value or retained explicit retry target.
 * @param isSaving Whether one settings request is awaiting acknowledgement.
 * @param saveFailed Whether the retained target requires explicit retry.
 * @param canChange Whether the current account source permits another request.
 */
data class NotificationPreferenceUiState(
    val enabled: Boolean? = null,
    val requestedEnabled: Boolean? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val canChange: Boolean = false,
)

package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermissionState

/**
 * UI state for notification preferences and Android permission status.
 * @param notificationsEnabled Confirmed saved notification preference.
 * @param requestedNotificationsEnabled Unsaved or in-flight preference toggle value.
 * @param permissionState Live device permission state.
 * @param isSaving Whether a preference write is in progress.
 * @param saveFailed Whether the last preference write failed.
 * @param canOpenSettings Whether the system settings activity can be resolved and launched.
 * @param canChange Whether preference changes are permitted.
 */
data class NotificationPreferenceUiState(
    val notificationsEnabled: Boolean = true,
    val requestedNotificationsEnabled: Boolean? = null,
    val permissionState: NotificationPermissionState? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val canOpenSettings: Boolean = true,
    val canChange: Boolean = true,
) {
    val enabled: Boolean
        get() = notificationsEnabled

    val requestedEnabled: Boolean?
        get() = requestedNotificationsEnabled
}

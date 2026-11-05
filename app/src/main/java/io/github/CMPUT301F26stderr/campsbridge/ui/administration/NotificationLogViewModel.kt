package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Declares the read-only log's list, selected-event and recipient contracts.
 * @param notifications All events and recorded recipients.
 * @param loader Common immutable event/context reader.
 * @param savedState Small retained filters and selection.
 */
class NotificationLogViewModel(
    notifications: NotificationRepository,
    loader: NotificationContentLoader,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val state = MutableStateFlow(NotificationLogUiState()).asStateFlow()
    val detail = MutableStateFlow(NotificationLogDetailUiState()).asStateFlow()
    val audience = MutableStateFlow<NotificationLogAudienceState>(NotificationLogAudienceState.Idle).asStateFlow()

    /** Retains raw query input.
     * @param query User-entered text.
     */
    fun editQuery(query: String) {}

    /** Selects a category without restarting the source.
     * @param type Category, or null for all.
     */
    fun selectType(type: NotificationType?) {}

    /** Retries the failed all-event source. */
    fun retry() {}

    /** Selects the actual navigation identity.
     * @param id Event ID, or null to retire selection.
     */
    fun selectEvent(id: String?) {}

    /** Retries only selected-event content. */
    fun retryDetail() {}

    /** Starts or retires the audience page's read.
     * @param visible Whether the selected audience page is displayed.
     */
    fun setAudienceVisible(visible: Boolean) {}

    /** Retries only the current audience query. */
    fun retryAudience() {}
}

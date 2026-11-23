/*
 * Participant conversation-list outcome, keeping per-conversation failures separate from the reservation source.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic.ConversationSummary

/** Participant conversation-list outcome. */
sealed interface ConversationListUiState {
    /** The reader's reservations haven't loaded yet. */
    data object Loading : ConversationListUiState

    /** The reader's reservations couldn't be read; no partial list is claimed. */
    data object Failure : ConversationListUiState

    /** Loaded reservations and the conversations read so far.
     * @param rows Conversations with at least one saved message, newest first.
     * @param failedReservationIds Conversations that failed to load and can be retried.
     * @param loading Whether some conversations are still loading.
     * @param names The other participant's name by reservation, where it could be read.
     */
    data class Content(
        val rows: List<ConversationSummary>,
        val failedReservationIds: Set<String> = emptySet(),
        val loading: Boolean = false,
        val names: Map<String, String> = emptyMap(),
    ) : ConversationListUiState {
        /** Unread messages across every loaded conversation. */
        val unread: Int get() = rows.sumOf { it.unread }
    }
}

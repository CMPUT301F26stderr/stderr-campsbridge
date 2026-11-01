package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

/** Presentation states for the account inbox. */
sealed interface InboxUiState {
    data object Loading : InboxUiState
    data object Failure : InboxUiState
    data object Unavailable : InboxUiState

    data class Content(
        val items: List<InboxItem>,
        val unavailable: List<UnavailableInboxReceipt> = emptyList(),
        val unreadCount: Int = 0,
        val operation: InboxOperation = InboxOperation.IDLE,
        val openingItem: InboxItem? = null,
        val saveFailed: Boolean = false,
    ) : InboxUiState
}

enum class InboxOperation {
    IDLE,
    OPENING,
    MARKING_READ,
}

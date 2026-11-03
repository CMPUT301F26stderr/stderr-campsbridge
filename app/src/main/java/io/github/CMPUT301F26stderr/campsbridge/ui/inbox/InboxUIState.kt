package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

/** Presentation states for the account inbox. */
sealed interface InboxUiState {
    /** Nothing has loaded yet. */
    data object Loading : InboxUiState

    /** The inbox failed before any rows loaded. */
    data object Failure : InboxUiState

    /** The inbox isn't available for this account. */
    data object Unavailable : InboxUiState

    /** Loaded rows plus the current action state.
     * @param items Readable rows, newest first.
     * @param unavailable Receipts whose event couldn't be loaded.
     * @param unreadCount Unread receipts, including ones without loaded content.
     * @param operation Action in progress.
     * @param openingItem Row waiting for the route to navigate; cleared by consumeOpen.
     * @param saveFailed The last read acknowledgement failed.
     * @param sourceFailed The live source failed, and these rows may be out of date.
     */
    data class Content(
        val items: List<InboxItem>,
        val unavailable: List<UnavailableInboxReceipt> = emptyList(),
        val unreadCount: Int = 0,
        val operation: InboxOperation = InboxOperation.IDLE,
        val openingItem: InboxItem? = null,
        val saveFailed: Boolean = false,
        val sourceFailed: Boolean = false,
    ) : InboxUiState
}

/** Inbox action in progress. */
enum class InboxOperation {
    IDLE,
    OPENING,
    MARKING_READ,
}

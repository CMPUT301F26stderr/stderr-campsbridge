package io.github.CMPUT301F26stderr.campsbridge.ui.app

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry

/** Root tap outcomes, independent of bootstrap state and the current screen's repository reads. */
sealed interface NotificationEntryUiState {
    /** No pending tap; normal navigation stays unchanged. */
    data object None : NotificationEntryUiState

    /** Retained identity waits for an available matching ready session.
     * @param entry Original typed envelope, with no copied application records.
     */
    data class Waiting(
        val entry: NotificationEntry,
    ) : NotificationEntryUiState

    /** One explicit event/context read is running.
     * @param entry Original typed envelope being resolved.
     */
    data class Loading(
        val entry: NotificationEntry,
    ) : NotificationEntryUiState

    /** A read failed and can be explicitly retried without changing identity.
     * @param entry Same retained envelope, never another transaction request.
     */
    data class Failure(
        val entry: NotificationEntry,
    ) : NotificationEntryUiState

    /** Event/target is absent, inconsistent or unsupported by available capabilities.
     * @param entry Original envelope retained for retry or dismissal.
     */
    data class Unavailable(
        val entry: NotificationEntry,
    ) : NotificationEntryUiState

    /** The current ready identity differs; no automatic account switch or event read is allowed.
     * @param entry Intended original recipient and saved target.
     */
    data class WrongAccount(
        val entry: NotificationEntry,
    ) : NotificationEntryUiState

    /** The exact target can be opened after the actual workspace/navigation host is ready.
     * @param entry Original envelope whose subject was checked against the saved event.
     * @param workspace Presentation chosen from saved relationship and current capabilities.
     */
    data class Ready(
        val entry: NotificationEntry,
        val workspace: Role,
    ) : NotificationEntryUiState
}

/*
 * Defines data state and frozen attempt contract for announcement composition.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery

/** Immutable request tuple frozen during send attempt. */
data class AnnouncementAttempt(
    val id: String,
    val lotteryId: String,
    val senderId: String,
    val title: String,
    val body: String,
)

/** Composition and send attempt lifecycle phases. */
enum class AnnouncementPhase {
    IDLE,
    SENDING,
    CHECKING,
    ABSENT,
    RETRY,
    CONFLICT,
}

/** Complete UI state for announcement composition. */
data class AnnouncementComposeUiState(
    val title: String = "",
    val body: String = "",
    val titleInvalid: Boolean = false,
    val bodyInvalid: Boolean = false,
    val phase: AnnouncementPhase = AnnouncementPhase.IDLE,
    val sent: Boolean = false,
    val attempt: AnnouncementAttempt? = null,
    val canSend: Boolean = false,
    val isLoading: Boolean = true,
    val lottery: Lottery? = null,
    val showDiscardConfirmation: Boolean = false,
    val showLeaveUnknownConfirmation: Boolean = false,
)

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message

/** Required saved relationship outcome, separate from empty message history. */
enum class ConversationRead { LOADING, READY, UNAVAILABLE, FAILURE }

/** Current profile evidence, separate from the saved conversation relationship and history. */
enum class ConversationAvailability { LOADING, AVAILABLE, UNAVAILABLE, FAILURE }

/** Explicit retained send attempt progress, without a fabricated saved message row. */
enum class MessageSendPhase { IDLE, SENDING, CHECKING, RETRY, ABSENT, CONFLICT }

/** Complete request identity retained for same-ID reconciliation/retry.
 * @param id Allocated message identity.
 * @param reservationId Original saved conversation.
 * @param senderId Original sending account.
 * @param body Exact nonblank submitted content.
 */
data class MessageAttempt(
    val id: String,
    val reservationId: String,
    val senderId: String,
    val body: String,
)

/** Saved history and independently retained composition/recovery facts.
 * @param read Required relationship read state.
 * @param context Actual saved participants, present only after authorized resolution.
 * @param messages Ordered observed records, never tentative send rows.
 * @param messagesLoading Primary message source has not emitted.
 * @param messagesFailed Primary source failed; old content is not claimed complete.
 * @param labels Independent participant display names; null retains canonical identity.
 * @param canSend Current participants are available for sending.
 * @param availability Profile loading, failure and confirmed unavailability remain distinct.
 * @param sendingUnavailable An inactive/unauthorized operation outcome blocks further sends.
 * @param input Raw draft or frozen attempt body.
 * @param attempt Complete frozen request for explicit recovery.
 * @param phase Current command/reconciliation stage.
 * @param failedReadIds Received-message acknowledgements needing explicit Retry.
 * @param confirmingDiscard An explicit unsent-draft exit confirmation is open.
 */
data class ConversationUiState(
    val read: ConversationRead = ConversationRead.LOADING,
    val context: ConversationContext? = null,
    val messages: List<Message> = emptyList(),
    val messagesLoading: Boolean = true,
    val messagesFailed: Boolean = false,
    val labels: Map<String, String?> = emptyMap(),
    val canSend: Boolean = false,
    val availability: ConversationAvailability = ConversationAvailability.LOADING,
    val sendingUnavailable: Boolean = false,
    val input: String = "",
    val attempt: MessageAttempt? = null,
    val phase: MessageSendPhase = MessageSendPhase.IDLE,
    val failedReadIds: Set<String> = emptySet(),
    val confirmingDiscard: Boolean = false,
)

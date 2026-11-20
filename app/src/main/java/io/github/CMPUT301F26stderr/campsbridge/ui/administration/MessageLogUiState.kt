package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message

/** Primary log read; supplemental failures never replace saved messages with this state. */
enum class MessageLogRead { LOADING, READY, FAILURE }

/** Descriptive saved participant direction, granting no conversation authority. */
enum class MessageLogDirection { OWNER_TO_ENTRANT, ENTRANT_TO_OWNER, UNAVAILABLE }

/** Saved message with independently resolved descriptive context.
 * @param message Original immutable message.
 * @param reservation Saved stay, retained even if later label reads fail.
 * @param ownerId Actual campsite owner's identity when known.
 * @param senderLabel Optional profile label, separate from saved sender identity.
 * @param recipientId Opposite saved participant when known.
 * @param recipientLabel Optional opposite profile label.
 * @param direction Descriptive participant direction.
 * @param isLoading Linked reads are still pending.
 * @param contextFailed A linked read failed and can be retried.
 */
data class MessageLogRow(
    val message: Message,
    val reservation: Reservation? = null,
    val ownerId: String? = null,
    val senderLabel: String? = null,
    val recipientId: String? = null,
    val recipientLabel: String? = null,
    val direction: MessageLogDirection = MessageLogDirection.UNAVAILABLE,
    val isLoading: Boolean = false,
    val contextFailed: Boolean = false,
)

/** Raw query and filtered saved records, without participant receipt state.
 * @param query Retained local search text.
 * @param read Primary message-source outcome.
 * @param rows Filtered newest-first rows.
 */
data class MessageLogUiState(
    val query: String = "",
    val read: MessageLogRead = MessageLogRead.LOADING,
    val rows: List<MessageLogRow> = emptyList(),
)

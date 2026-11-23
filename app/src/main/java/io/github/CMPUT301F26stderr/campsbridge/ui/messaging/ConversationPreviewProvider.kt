package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import java.time.Instant

/** Supplies actual-shaped participant history and explicit command recovery, without repositories. */
class ConversationPreviewProvider : PreviewParameterProvider<ConversationUiState> {
    override val values: Sequence<ConversationUiState> =
        run {
            val stay = previewReservation()
            val now = Instant.parse("2027-05-10T18:00:00Z")
            val context = ConversationContext(stay, "owner", stay.entrantId)
            val rows =
                listOf(
                    Message(
                        "incoming",
                        stay.id,
                        "owner",
                        "The riverside path is open.\nPlease let me know if you have any questions before your stay.",
                        now,
                    ),
                    Message(
                        "outgoing",
                        stay.id,
                        stay.entrantId,
                        "Thanks! We expect to arrive around four in the afternoon.",
                        now.plusSeconds(60),
                    ),
                )
            val ready =
                ConversationUiState(
                    read = ConversationRead.READY,
                    context = context,
                    messages = rows,
                    messagesLoading = false,
                    labels =
                        mapOf("owner" to "Blair with a longer display name", stay.entrantId to "Jamie Lee"),
                    canSend = true,
                    availability = ConversationAvailability.AVAILABLE,
                )
            val attempt =
                MessageAttempt(
                    "pending",
                    stay.id,
                    stay.entrantId,
                    "Is there firewood available at the campsite?\nWe can bring some if needed.",
                )
            sequenceOf(
                ready,
                ready.copy(messages = emptyList()),
                ready.copy(messagesLoading = true),
                ready.copy(messages = emptyList(), messagesFailed = true),
                ready.copy(input = attempt.body, attempt = attempt, phase = MessageSendPhase.SENDING),
                ready.copy(input = attempt.body, attempt = attempt, phase = MessageSendPhase.CHECKING),
                ready.copy(input = attempt.body, attempt = attempt, phase = MessageSendPhase.RETRY),
                ready.copy(input = attempt.body, attempt = attempt, phase = MessageSendPhase.ABSENT),
                ready.copy(input = attempt.body, attempt = attempt, phase = MessageSendPhase.CONFLICT),
                ready.copy(
                    canSend = false,
                    sendingUnavailable = true,
                    availability = ConversationAvailability.UNAVAILABLE,
                    input = attempt.body,
                    attempt = attempt,
                    phase = MessageSendPhase.ABSENT,
                ),
                ready.copy(failedReadIds = setOf("incoming")),
                ready.copy(input = "Retain this unsent draft", confirmingDiscard = true),
                ConversationUiState(),
                ConversationUiState(read = ConversationRead.FAILURE),
                ConversationUiState(read = ConversationRead.UNAVAILABLE),
                ready.copy(
                    input = attempt.body,
                    attempt = attempt,
                    phase = MessageSendPhase.RETRY,
                    confirmingDiscard = true,
                ),
            )
        }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import java.time.Instant

/** Passive real-message layouts, including missing/failed context and primary source outcomes. */
class MessageLogPreviewProvider : PreviewParameterProvider<MessageLogUiState> {
    private val stay = previewReservation()
    private val row =
        MessageLogRow(
            Message(
                "saved-message",
                stay.id,
                "owner",
                "Please use the east entrance.\nWe will meet you by the river trail after lunch.",
                Instant.parse("2027-05-10T18:00:00Z"),
            ),
            stay,
            "owner",
            "Jordan Lee with a long campsite owner name",
            "entrant",
            "Alex Chen",
            MessageLogDirection.OWNER_TO_ENTRANT,
        )
    override val values =
        sequenceOf(
            MessageLogUiState(
                read = MessageLogRead.READY,
                rows =
                    listOf(
                        row,
                        row.copy(
                            message =
                                row.message.copy(
                                    id = "reply",
                                    senderId = "entrant",
                                    body = "Thanks! We will arrive together after lunch.",
                                ),
                            senderLabel = "Alex Chen",
                            recipientId = "owner",
                            recipientLabel = "Jordan Lee",
                            direction = MessageLogDirection.ENTRANT_TO_OWNER,
                        ),
                    ),
            ),
            MessageLogUiState(read = MessageLogRead.READY),
            MessageLogUiState(),
            MessageLogUiState(read = MessageLogRead.FAILURE),
            MessageLogUiState(
                read = MessageLogRead.READY,
                rows = listOf(row.copy(senderLabel = null, recipientLabel = null, contextFailed = true)),
            ),
            MessageLogUiState(read = MessageLogRead.READY, rows = listOf(MessageLogRow(row.message))),
            MessageLogUiState(read = MessageLogRead.READY, rows = listOf(row.copy(isLoading = true))),
        )
}

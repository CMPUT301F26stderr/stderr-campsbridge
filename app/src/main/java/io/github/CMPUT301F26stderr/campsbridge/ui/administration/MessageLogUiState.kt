package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message

/** Describes the result of reading saved messages. */
enum class MessageLogReadState {
    LOADING,
    READY,
    FAILURE,
}

/** Holds the message log's display state and search input. */
data class MessageLogUiState(
    val readState: MessageLogReadState = MessageLogReadState.LOADING,
    val messages: List<Message> = emptyList(),
    val query: String = "",
)

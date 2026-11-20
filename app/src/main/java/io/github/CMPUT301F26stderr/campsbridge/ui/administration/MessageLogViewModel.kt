package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads saved messages for the administrator log.
 * @param messages Repository supplying saved messages.
 * @param savedState Retains the search query across screen recreation.
 */
class MessageLogViewModel(
    private val messages: MessageRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            MessageLogUiState(
                query = savedState.get<String>("messageLogQuery") ?: "",
            ),
        )
    val state = mutableState.asStateFlow()

    private var allMessages: List<Message> = emptyList()

    init {
        observeMessages()
    }

    /** Observes saved messages without sending or acknowledging them. */
    private fun observeMessages() {
        mutableState.value =
            mutableState.value.copy(
                readState = MessageLogReadState.LOADING,
            )

        viewModelScope.launch {
            try {
                messages.observeAll().collect { savedMessages ->
                    allMessages = savedMessages
                    mutableState.value =
                        mutableState.value.copy(
                            readState = MessageLogReadState.READY,
                        )
                    filterMessages()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value =
                    mutableState.value.copy(
                        readState = MessageLogReadState.FAILURE,
                    )
            }
        }
    }

    /** Restarts message observation after a failed read. */
    fun retry() {
        if (mutableState.value.readState == MessageLogReadState.FAILURE) {
            observeMessages()
        }
    }

    /** Saves the search text and refreshes the displayed messages.
     * @param query Text entered in the search field.
     */
    fun editQuery(query: String) {
        savedState["messageLogQuery"] = query
        mutableState.value = mutableState.value.copy(query = query)
        filterMessages()
    }

    /** Filters saved messages using the current search text. */
    private fun filterMessages() {
        val query = mutableState.value.query.trim()
        val filtered =
            allMessages.filter { message ->
                query.isBlank() ||
                    message.body.contains(query, ignoreCase = true) ||
                    message.senderId.contains(query, ignoreCase = true) ||
                    message.reservationId.contains(query, ignoreCase = true)
            }

        mutableState.value = mutableState.value.copy(messages = filtered)
    }
}

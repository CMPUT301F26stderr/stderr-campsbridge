package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns one participant conversation, explicit retained-ID sends and visible recipient reads.
 * @param accountId Actual actor, fixed for this model lifetime.
 * @param reservationId Saved conversation identity.
 * @param bookings Saved stay source.
 * @param resolver Actual campsite-owner relationship resolver shared with notification entry.
 * @param messages Saved content and atomic send/read operations.
 * @param accounts Participant availability and independent labels.
 * @param savedState Small draft/frozen request primitives.
 * @param newId Allocates a message ID only for a new explicit valid send.
 */
class ConversationViewModel(
    private val accountId: String,
    private val reservationId: String,
    private val bookings: BookingRepository,
    private val resolver: ConversationContextResolver,
    private val messages: MessageRepository,
    private val accounts: AccountRepository,
    private val savedState: SavedStateHandle,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConversationUiState())
    val state = mutableState.asStateFlow()

    /** Retains raw input.
     * @param body Unsubmitted text.
     */
    fun edit(body: String) =
        Unit

    /** Freezes one explicit send request. */
    fun send() =
        Unit

    /** Retries the same retained request. */
    fun retrySend() =
        Unit

    /** Releases a confirmed absent or conflicting attempt. */
    fun editAttempt() =
        Unit

    /** Retries required reads. */
    fun retry() =
        Unit

    /** Sets the recipient read boundary.
     * @param active Actual resumed route visibility.
     */
    fun setVisible(active: Boolean) =
        Unit

    /** Retries failed recipient acknowledgements. */
    fun retryReads() =
        Unit

    /** Protects unsent input before returning. */
    fun requestBack(): Boolean =
        false

    /** Keeps the current draft. */
    fun dismissDiscard() =
        Unit

    /** Discards only local input. */
    fun confirmDiscard(): Boolean =
        false
}

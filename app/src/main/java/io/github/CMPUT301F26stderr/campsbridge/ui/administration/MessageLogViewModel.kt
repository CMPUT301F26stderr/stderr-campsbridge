package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads saved messages with descriptive context; never sends or acknowledges participant content.
 * @param messages Existing saved all-message observation.
 * @param bookings Saved reservation context.
 * @param campsites Actual campsite owner facts.
 * @param accounts Independent supplemental profile labels.
 * @param saved Retained local query only.
 */
class MessageLogViewModel(
    private val messages: MessageRepository,
    private val bookings: BookingRepository,
    private val campsites: CampsiteRepository,
    private val accounts: AccountRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MessageLogUiState(query = saved.get<String>(QUERY).orEmpty()))
    val state = mutableState.asStateFlow()
    private val mutableSavedRows = MutableStateFlow<List<MessageLogRow>>(emptyList())
    val savedRows = mutableSavedRows.asStateFlow()
    private val records = linkedMapOf<String, MessageLogRow>()
    private val contextJobs = mutableMapOf<String, Job>()
    private val contextTokens = mutableMapOf<String, Long>()
    private var nextToken = 0L
    private var source: Job? = null
    private var sourceToken = 0L

    init {
        observe()
    }

    /** Retains and applies local text search without restarting observation.
     * @param query Raw search input.
     */
    fun editQuery(query: String) {
        saved[QUERY] = query
        mutableState.value = state.value.copy(query = query)
        project()
    }

    /** Retries a failed primary observation without writing. */
    fun retry() {
        if (state.value.read == MessageLogRead.FAILURE) observe()
    }

    /** Retries only one failed linked context.
     * @param id Original saved message identity.
     */
    fun retryContext(id: String) {
        val row = records[id] ?: return
        if (row.contextFailed && !row.isLoading) loadContext(row.message)
    }

    /** Resolves a selected saved record independently of list filtering.
     * @param id Original message identity.
     */
    fun find(id: String): MessageLogRow? =
        records[id]

    /** Reads only the selected record's saved reservation history, independently of search.
     * @param id Original selected message identity; a missing target exposes no other history.
     */
    fun conversation(id: String): List<MessageLogRow> {
        val rows = savedRows.value
        val selected = rows.firstOrNull { it.message.id == id } ?: return emptyList()
        return rows
            .filter { it.message.reservationId == selected.message.reservationId }
            .sortedWith(compareBy<MessageLogRow> { it.message.createdAt }.thenBy { it.message.id })
    }

    /** Observes actual messages; a source failure never becomes an empty successful result. */
    private fun observe() {
        source?.cancel()
        val token = ++sourceToken
        mutableState.value = state.value.copy(read = MessageLogRead.LOADING)
        source =
            viewModelScope.launch {
                try {
                    messages.observeAll().collect { values ->
                        currentCoroutineContext().ensureActive()
                        if (token != sourceToken) return@collect
                        val ids = values.map { it.id }.toSet()
                        for (id in records.keys.toList().filter { it !in ids }) {
                            records.remove(id)
                            contextTokens.remove(id)
                            contextJobs.remove(id)?.cancel()
                        }
                        for (message in values) {
                            val previous = records[message.id]
                            if (previous == null || previous.message.reservationId != message.reservationId ||
                                previous.message.senderId != message.senderId
                            ) {
                                records[message.id] = MessageLogRow(message)
                                loadContext(message)
                            } else {
                                records[message.id] = previous.copy(message = message)
                            }
                        }
                        mutableState.value = state.value.copy(read = MessageLogRead.READY)
                        project()
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (token ==
                        sourceToken
                    ) {
                        mutableState.value = state.value.copy(read = MessageLogRead.FAILURE)
                    }
                }
            }
    }

    /** Resolves stay, actual owner and profile labels independently of participant authorization.
     * @param message Original saved record; missing linked facts cannot remove it.
     */
    private fun loadContext(message: Message) {
        contextJobs.remove(message.id)?.cancel()
        val token = ++nextToken
        contextTokens[message.id] = token
        records[message.id] = records.getValue(message.id).copy(isLoading = true, contextFailed = false)
        project()
        contextJobs[message.id] =
            viewModelScope.launch {
                var failed = false
                val reservation =
                    try {
                        bookings.get(message.reservationId)?.takeIf { it.id == message.reservationId }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                val site =
                    try {
                        reservation?.let {
                            campsites.get(it.campsiteId)?.takeIf { site ->
                                site.id == it.campsiteId
                            }
                        }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                val ownerId = site?.ownerId
                val direction =
                    when {
                        reservation != null && ownerId != null && message.senderId == ownerId -> {
                            MessageLogDirection.OWNER_TO_ENTRANT
                        }

                        reservation != null && ownerId != null && message.senderId == reservation.entrantId -> {
                            MessageLogDirection.ENTRANT_TO_OWNER
                        }

                        else -> {
                            MessageLogDirection.UNAVAILABLE
                        }
                    }
                val recipient =
                    when (direction) {
                        MessageLogDirection.OWNER_TO_ENTRANT -> reservation?.entrantId
                        MessageLogDirection.ENTRANT_TO_OWNER -> ownerId
                        MessageLogDirection.UNAVAILABLE -> null
                    }
                val senderLabel =
                    try {
                        accounts
                            .get(message.senderId)
                            ?.takeIf { it.id == message.senderId }
                            ?.name
                            ?.takeIf { it.isNotBlank() }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                val recipientLabel =
                    try {
                        recipient?.let { id ->
                            accounts
                                .get(id)
                                ?.takeIf { it.id == id }
                                ?.name
                                ?.takeIf { it.isNotBlank() }
                        }
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        null
                    }
                currentCoroutineContext().ensureActive()
                if (contextTokens[message.id] == token) {
                    val actual = records[message.id]?.message ?: return@launch
                    records[message.id] =
                        MessageLogRow(
                            actual,
                            reservation,
                            ownerId,
                            senderLabel,
                            recipient,
                            recipientLabel,
                            direction,
                            false,
                            failed,
                        )
                    contextJobs.remove(message.id)
                    project()
                }
            }
    }

    /** Filters descriptive facts while preserving the original source order and identities. */
    private fun project() {
        val query = state.value.query.trim()
        val allRows =
            records.values
                .sortedWith(
                    compareByDescending<MessageLogRow> { it.message.createdAt }.thenBy { it.message.id },
                )
        mutableSavedRows.value = allRows
        val rows =
            allRows.filter { row ->
                query.isBlank() ||
                    listOfNotNull(
                        row.message.id,
                        row.message.body,
                        row.message.reservationId,
                        row.message.senderId,
                        row.recipientId,
                        row.ownerId,
                        row.senderLabel,
                        row.recipientLabel,
                        row.reservation?.bookedCampsite?.name,
                        row.direction.name.replace('_', ' '),
                        when (row.direction) {
                            MessageLogDirection.OWNER_TO_ENTRANT -> "owner to camper"
                            MessageLogDirection.ENTRANT_TO_OWNER -> "camper to owner"
                            MessageLogDirection.UNAVAILABLE -> null
                        },
                    ).any { it.contains(query, ignoreCase = true) }
            }
        mutableState.value = state.value.copy(rows = rows)
    }

    /** Only the local search input is restored; saved records stay repository-owned. */
    private companion object {
        const val QUERY = "adminMessageLogQuery"
    }
}

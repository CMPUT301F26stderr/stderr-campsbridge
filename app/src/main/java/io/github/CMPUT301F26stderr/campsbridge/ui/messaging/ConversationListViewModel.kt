/*
 * Observes a participant's reservations and each reservation's conversation for the Messages list.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic.conversationSummaries
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Read-only list of the actor's reservation conversations; opening one uses the existing conversation screen.
 * @param actorId Account viewing its own conversations.
 * @param context Entrant reservations or reservations at the owner's campsites.
 * @param bookings Saved reservations the actor takes part in.
 * @param messages Saved conversations, one listener per reservation.
 * @param resolver Confirms each reservation's campsite owner, so the other participant is known.
 * @param accounts Reads the other participant's display name.
 */
class ConversationListViewModel(
    private val actorId: String,
    private val context: ReservationContext,
    private val bookings: BookingRepository,
    private val messages: MessageRepository,
    private val resolver: ConversationContextResolver,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ConversationListUiState>(ConversationListUiState.Loading)
    val state = mutableState.asStateFlow()
    private var reservations: List<Reservation>? = null
    private var sourceFailed = false
    private var sourceJob: Job? = null
    private val conversations = mutableMapOf<String, List<Message>>()
    private val failed = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()
    private val names = mutableMapOf<String, String>()
    private val nameJobs = mutableMapOf<String, Job>()

    init {
        observe()
    }

    /** Retries the reservation source if it failed, otherwise only the conversations that failed. */
    fun retry() {
        if (sourceFailed) {
            observe()
            return
        }
        for (id in failed.toList()) {
            failed -= id
            listen(id)
        }
        publish()
    }

    /** Starts one reservation listener; each reservation then gets its own conversation listener. */
    private fun observe() {
        sourceJob?.cancel()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        nameJobs.values.forEach { it.cancel() }
        nameJobs.clear()
        names.clear()
        conversations.clear()
        failed.clear()
        reservations = null
        sourceFailed = false
        publish()
        val source =
            if (context == ReservationContext.OWNER) {
                bookings.observeForOwner(actorId)
            } else {
                bookings.observeForEntrant(actorId)
            }
        sourceJob =
            viewModelScope.launch {
                try {
                    source.collect { rows ->
                        reservations = rows
                        val ids = rows.map { it.id }.toSet()
                        // Stop listening to reservations that left the source, start the new ones.
                        for (gone in jobs.keys - ids) {
                            jobs.remove(gone)?.cancel()
                            nameJobs.remove(gone)?.cancel()
                            conversations -= gone
                            failed -= gone
                            names -= gone
                        }
                        for (added in rows.filter { it.id !in jobs }) {
                            listen(added.id)
                            loadName(added)
                        }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    jobs.values.forEach { it.cancel() }
                    jobs.clear()
                    sourceFailed = true
                    publish()
                }
            }
    }

    /** Listens to one conversation; a failure marks only that reservation for Retry.
     * @param reservationId Conversation identity.
     */
    private fun listen(reservationId: String) {
        jobs[reservationId]?.cancel()
        jobs[reservationId] =
            viewModelScope.launch {
                try {
                    messages.observeConversation(reservationId).collect { saved ->
                        conversations[reservationId] = saved
                        failed -= reservationId
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    conversations -= reservationId
                    failed += reservationId
                    publish()
                }
            }
    }

    /** Reads the other participant's name once; a missing or failed read leaves the campsite as the label.
     * @param reservation Saved reservation whose other participant is shown.
     */
    private fun loadName(reservation: Reservation) {
        nameJobs[reservation.id] =
            viewModelScope.launch {
                try {
                    val other = resolver.resolve(reservation, actorId)?.otherId ?: return@launch
                    val name = accounts.get(other)?.name?.takeIf { it.isNotBlank() } ?: return@launch
                    names[reservation.id] = name
                    publish()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The name is supplemental; the row still shows the campsite and the conversation.
                }
            }
    }

    /** Projects the current reads; an empty list is only confirmed once every conversation has loaded. */
    private fun publish() {
        val rows = reservations
        mutableState.value =
            when {
                sourceFailed -> {
                    ConversationListUiState.Failure
                }

                rows == null -> {
                    ConversationListUiState.Loading
                }

                else -> {
                    ConversationListUiState.Content(
                        conversationSummaries(rows, conversations, actorId),
                        failed.toSet(),
                        loading = rows.any { it.id !in conversations && it.id !in failed },
                        names = names.toMap(),
                    )
                }
            }
    }
}

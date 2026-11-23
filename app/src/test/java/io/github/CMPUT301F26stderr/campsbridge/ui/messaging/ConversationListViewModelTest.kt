/*
 * Checks the participant conversation list built from saved reservations and their messages.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** The list reads only the actor's own reservations and their conversations; it never writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val bookings = FakeBookingRepository()
    private val saved = FakeMessageRepository()
    private val campsites = FakeCampsiteRepository(listOf(CampingFixtures.campsite()))
    private val accounts =
        FakeAccountRepository().apply {
            getHandler = { id ->
                sampleAccount().copy(id = id, name = if (id == "owner") "Jordan Lee" else "Alex Chen")
            }
        }
    private val at = Instant.parse("2027-05-10T12:00:00Z")
    private val failing = mutableSetOf<String>()
    private val observed = mutableListOf<String>()
    private val messages =
        object : MessageRepository by saved {
            /** Records each conversation read and fails the ones the test marks as failing.
             * @param reservationId Conversation identity.
             */
            override fun observeConversation(reservationId: String): Flow<List<Message>> =
                flow {
                    observed += reservationId
                    if (reservationId in failing) throw IOException("conversation unavailable")
                    saved.observeConversation(reservationId).collect { emit(it) }
                }
        }

    /** An entrant sees their conversations, the owner's name and a total unread count; nothing is written. */
    @Test fun entrantSeesOwnConversationsAndUnreadTotal() =
        runTest {
            bookings.records.value = listOf(stay("first"), stay("second"))
            saved.records.value = listOf(message("a", "first", "owner", 1), message("b", "second", "owner", 2))
            val model = model(ReservationContext.ENTRANT)
            runCurrent()
            val content = model.state.value as ConversationListUiState.Content
            assertEquals(listOf("second", "first"), content.rows.map { it.reservation.id })
            assertEquals(2, content.unread)
            assertEquals("Jordan Lee", content.names["first"])
            assertEquals(listOf("entrant"), bookings.entrantRequests)
            assertTrue(saved.readRequests.isEmpty())
            store.clear()
        }

    /** One failed conversation keeps the others visible; Retry re-reads only that conversation. */
    @Test fun failedConversationRetriesWithoutHidingTheOthers() =
        runTest {
            bookings.records.value = listOf(stay("ok"), stay("broken"))
            saved.records.value = listOf(message("a", "ok", "owner", 1), message("b", "broken", "owner", 2))
            failing += "broken"
            val model = model(ReservationContext.ENTRANT)
            runCurrent()
            val partial = model.state.value as ConversationListUiState.Content
            assertEquals(listOf("ok"), partial.rows.map { it.reservation.id })
            assertEquals(setOf("broken"), partial.failedReservationIds)
            failing.clear()
            observed.clear()
            model.retry()
            runCurrent()
            val recovered = model.state.value as ConversationListUiState.Content
            assertEquals(listOf("broken", "ok"), recovered.rows.map { it.reservation.id })
            assertTrue(recovered.failedReservationIds.isEmpty())
            assertEquals(listOf("broken"), observed)
            store.clear()
        }

    /** An owner's list comes from the reservations at their campsites and names the camper. */
    @Test fun ownerReadsReservationsAtTheirCampsites() =
        runTest {
            bookings.records.value = listOf(stay("hosted"))
            bookings.ownerHandler = { bookings.records }
            saved.records.value = listOf(message("a", "hosted", "entrant", 1))
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertEquals(listOf("owner"), bookings.ownerRequests)
            val content = model.state.value as ConversationListUiState.Content
            assertEquals(1, content.unread)
            assertEquals("Alex Chen", content.names["hosted"])
            store.clear()
        }

    /** A saved stay for the shared fixture entrant and owner.
     * @param id Reservation identity.
     */
    private fun stay(id: String) =
        CampingFixtures.reservation().copy(id = id)

    /** One unread saved message at a fixed offset from the shared anchor.
     * @param id Message identity.
     * @param reservationId Conversation identity.
     * @param sender Sending account.
     * @param minutes Minutes after the anchor.
     */
    private fun message(
        id: String,
        reservationId: String,
        sender: String,
        minutes: Long,
    ) =
        Message(id, reservationId, sender, "Message $id", at.plusSeconds(minutes * 60))

    /** Builds the list owner for one workspace.
     * @param context Entrant or owner presentation.
     */
    private fun model(context: ReservationContext) =
        ConversationListViewModel(
            if (context == ReservationContext.OWNER) "owner" else "entrant",
            context,
            bookings,
            messages,
            ConversationContextResolver(campsites),
            accounts,
        ).also { store.put(context.name, it) }
}

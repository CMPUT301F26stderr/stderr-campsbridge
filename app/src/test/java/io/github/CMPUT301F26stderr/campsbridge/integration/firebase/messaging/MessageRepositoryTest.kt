package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.messaging

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.messaging.MessageDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns reservation conversations and atomic alert creation; notification tests own delivery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class MessageRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Either participant can send; each message queues exactly one alert to the other participant. */
    @Test(timeout = 60000)
    fun sendsAreScopedOrderedAndQueueTheOtherParticipant() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val reservationId = seedConversation(owner, entrant)
            val now = firebase.clock.instant()
            val first = entrant.container.messages.send(reservationId, entrant.id, "Arrival at six")
            firebase.clock.current = now.plusSeconds(60)
            val second = owner.container.messages.send(reservationId, owner.id, "See you then")
            firebase.seed(
                "messages/elsewhere",
                MessageDocument.encode(
                    Message("elsewhere", "other", entrant.id, "Another stay", now.minusSeconds(60)),
                ),
            )
            val conversation =
                entrant.container.messages
                    .observeConversation(reservationId)
                    .first()
            assertEquals(listOf(first, second), conversation.map { it.id })
            assertEquals(listOf("Arrival at six", "See you then"), conversation.map { it.body })
            assertEquals(listOf(entrant.id, owner.id), conversation.map { it.senderId })
            assertEquals(
                listOf(
                    second,
                    first,
                    "elsewhere",
                ),
                admin.container.messages.observeAll().first().map {
                    it.id
                },
            )
            for ((id, recipient) in listOf(first to owner.id, second to entrant.id)) {
                val eventId = DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, id)
                val event =
                    admin.clients.firestore
                        .document("notifications/$eventId")
                        .get()
                        .await()
                val job =
                    admin.clients.firestore
                        .document(
                            "_notificationDeliveries/$eventId",
                        ).get()
                        .await()
                assertEquals("NEW_MESSAGE", event.getString("type"))
                assertEquals(id, event.get("subject.id"))
                assertEquals(listOf(recipient), job.get("recipientIds"))
            }
        }

    /** Read acknowledgement belongs to the recipient and retains the first acknowledgement time. */
    @Test(timeout = 60000)
    fun onlyTheRecipientCanRecordTheFirstRead() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val reservationId = seedConversation(owner, entrant)
            val id = entrant.container.messages.send(reservationId, entrant.id, "Hello")
            assertPersistenceFailure(
                NOT_ALLOWED,
            ) { entrant.container.messages.markRead(id, entrant.id) }
            assertNull(
                entrant.container.messages
                    .observeConversation(reservationId)
                    .first()
                    .single()
                    .readAt,
            )
            val firstRead = firebase.clock.instant().plusSeconds(60)
            firebase.clock.current = firstRead
            owner.container.messages.markRead(id, owner.id)
            firebase.clock.current = firstRead.plusSeconds(60)
            owner.container.messages.markRead(id, owner.id)
            assertEquals(
                firstRead,
                entrant.container.messages
                    .observeConversation(reservationId)
                    .first()
                    .single()
                    .readAt,
            )
        }

    /** Invalid participants or a removed recipient cannot leave a message or an alert behind. */
    @Test(timeout = 60000)
    fun failedSendDoesNotCreatePartialMessageOrNotification() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val reservationId = seedConversation(owner, entrant)
            assertPersistenceFailure(NOT_ALLOWED) {
                admin.container.messages.send(reservationId, admin.id, "Intruder")
            }
            val profile = checkNotNull(admin.container.accounts.get(owner.id))
            firebase.seed(
                "accounts/${owner.id}",
                AccountDocument.encode(
                    profile.copy(removal = Removal(firebase.clock.instant(), admin.id)),
                ),
            )
            assertPersistenceFailure(INACTIVE) {
                entrant.container.messages.send(reservationId, entrant.id, "No recipient")
            }
            for (collection in listOf("messages", "notifications", "_notificationDeliveries")) {
                assertTrue(
                    admin.clients.firestore
                        .collection(collection)
                        .get()
                        .await()
                        .isEmpty,
                )
            }
        }

    /**
     * Seeds participants and a reservation without exercising the booking transaction.
     * @param owner Listing owner and conversation participant.
     * @param entrant Reservation holder and conversation participant.
     */
    private fun seedConversation(
        owner: EmulatorActor,
        entrant: EmulatorActor,
    ): String {
        val reservationId = DocumentKeys.reservation("draw", entrant.id)
        firebase.seed(
            "campsites/site",
            CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)),
        )
        firebase.seed(
            "reservations/$reservationId",
            ReservationDocument.encode(
                CampingFixtures.reservation().copy(id = reservationId, entrantId = entrant.id),
            ),
        )
        return reservationId
    }
}

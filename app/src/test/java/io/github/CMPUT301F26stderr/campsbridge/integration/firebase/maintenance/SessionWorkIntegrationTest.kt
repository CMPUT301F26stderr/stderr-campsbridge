package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.maintenance

import com.google.firebase.firestore.MetadataChanges
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking.bookingFixture
import io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery.drawFixture
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real, authenticated foreground discovery and operations; pure tests own scheduling time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SessionWorkIntegrationTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** An owner draws due work and delivers results/announcements without admin scans. */
    @Test(timeout = 60000)
    fun ownerSessionDrawsAndDeliversResults() =
        runBlocking<Unit> {
            val fixture = firebase.drawFixture(entrants = 2, winners = 1)
            val announcement =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Reminder", "Bring water"),
                )
            val failures = mutableListOf<Exception>()
            val worker =
                launch {
                    fixture.owner.container.pendingWork.run(
                        WorkSession(fixture.owner.id, setOf(Role.OWNER)),
                        failures::add,
                    )
                }
            try {
                withTimeout(8000) {
                    fixture.owner.container.lotteries
                        .observe("draw")
                        .first { it?.drawnAt != null }
                }
                awaitEmptyQueue(fixture.owner, "lotteries/draw")
                assertEquals(
                    0L,
                    fixture.owner.container.lotteries
                        .get("draw")!!
                        .waitingCount,
                )
                for (entrant in fixture.entrants) {
                    val inbox =
                        withTimeout(5000) {
                            entrant.container.notifications
                                .observeInbox(entrant.id)
                                .first { it.size == 2 }
                        }
                    assertTrue(inbox.any { it.notificationId == announcement })
                    for (receipt in inbox) {
                        val event = entrant.container.notifications.get(receipt.notificationId)!!
                        assertEquals("draw", event.subject.id)
                    }
                }
                assertTrue(failures.toString(), failures.isEmpty())
            } finally {
                worker.cancelAndJoin()
            }
        }

    /** Entrant and owner sessions discover interrupted stay/message jobs without global scans. */
    @Test(timeout = 60000)
    fun entrantSessionRecoversReservationAndMessageJobs() =
        runBlocking<Unit> {
            val fixture = firebase.bookingFixture()
            val reservationId =
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            val fromEntrant =
                fixture.entrant.container.messages.send(
                    reservationId,
                    fixture.entrant.id,
                    "Arrival at six",
                )
            val fromOwner =
                fixture.owner.container.messages.send(
                    reservationId,
                    fixture.owner.id,
                    "See you then",
                )
            val otherOwner = firebase.registeredActor(Role.OWNER)
            firebase.seed(
                "lotteries/other",
                LotteryDocument.encode(
                    fixture.lottery.copy(id = "other", ownerId = otherOwner.id),
                ),
            )
            val unrelated =
                otherOwner.container.notifications.announce(
                    "other",
                    otherOwner.id,
                    Announcement("Other", "Other owner's work"),
                )
            val confirmation = DocumentKeys.compound("RESERVATION_CONFIRMED", reservationId)
            assertTrue(
                firebase.documentExists(
                    "reservations/$reservationId/_notificationDeliveries/$confirmation",
                ),
            )
            val failures = mutableListOf<Exception>()
            val worker =
                launch {
                    fixture.entrant.container.pendingWork.run(
                        WorkSession(fixture.entrant.id, setOf(Role.ENTRANT)),
                        failures::add,
                    )
                }
            try {
                awaitEmptyQueue(fixture.entrant, "reservations/$reservationId")
                val entrantInbox =
                    withTimeout(5000) {
                        fixture.entrant.container.notifications
                            .observeInbox(fixture.entrant.id)
                            .first { it.size == 2 }
                    }
                assertEquals(
                    setOf(confirmation, DocumentKeys.compound("NEW_MESSAGE", fromOwner)),
                    entrantInbox.map { it.notificationId }.toSet(),
                )
                val ownerInbox =
                    withTimeout(5000) {
                        fixture.owner.container.notifications
                            .observeInbox(fixture.owner.id)
                            .first { it.size == 1 }
                    }
                assertEquals(
                    DocumentKeys.compound("NEW_MESSAGE", fromEntrant),
                    ownerInbox.single().notificationId,
                )
                assertTrue(
                    firebase.documentExists("lotteries/other/_notificationDeliveries/$unrelated"),
                )
                assertFalse(
                    firebase.documentExists(
                        "reservations/$reservationId/_notificationDeliveries/$confirmation",
                    ),
                )
                assertTrue(failures.toString(), failures.isEmpty())
            } finally {
                worker.cancelAndJoin()
            }

            // A later owner session must discover the same stay through campsite ownership.
            val laterMessage =
                fixture.entrant.container.messages.send(
                    reservationId,
                    fixture.entrant.id,
                    "Updated arrival",
                )
            val ownerWorker =
                launch {
                    fixture.owner.container.pendingWork.run(
                        WorkSession(fixture.owner.id, setOf(Role.OWNER)),
                        failures::add,
                    )
                }
            try {
                awaitEmptyQueue(fixture.owner, "reservations/$reservationId")
                val inbox =
                    withTimeout(5000) {
                        fixture.owner.container.notifications
                            .observeInbox(fixture.owner.id)
                            .first { it.size == 2 }
                    }
                assertTrue(
                    inbox.any {
                        it.notificationId ==
                            DocumentKeys.compound("NEW_MESSAGE", laterMessage)
                    },
                )
                assertTrue(
                    firebase.documentExists("lotteries/other/_notificationDeliveries/$unrelated"),
                )
                assertTrue(failures.toString(), failures.isEmpty())
            } finally {
                ownerWorker.cancelAndJoin()
            }
        }

    /** Waits for a server-confirmed empty queue, excluding cache and unacknowledged local deletes.
     * @param actor Authenticated parent participant.
     * @param parent Canonical lottery or reservation path.
     */
    private suspend fun awaitEmptyQueue(
        actor: EmulatorActor,
        parent: String,
    ) {
        withTimeout(8000) {
            callbackFlow {
                val registration =
                    actor.clients.firestore
                        .collection("$parent/_notificationDeliveries")
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                            if (failure != null) {
                                close(failure)
                            } else if (snapshot != null) {
                                trySend(
                                    snapshot.isEmpty && !snapshot.metadata.isFromCache &&
                                        !snapshot.metadata.hasPendingWrites(),
                                )
                            }
                        }
                awaitClose { registration.remove() }
            }.first { it }
        }
    }
}

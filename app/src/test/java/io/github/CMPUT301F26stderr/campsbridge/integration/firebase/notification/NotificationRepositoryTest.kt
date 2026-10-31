package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.notification

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.DeliveryScope
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDeliveries
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery.drawFixture
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns captured inbox delivery, first-read acknowledgements and event history queries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class NotificationRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Announcements include joined outcomes after a draw, exclude withdrawals and share one event. */
    @Test(timeout = 60000)
    fun announcementCapturesNonwithdrawnMembersAfterDraw() =
        runBlocking {
            val fixture = firebase.drawFixture()
            val states = listOf(EntryState.OFFERED, EntryState.DECLINED, EntryState.LOST)
            for ((actor, state) in fixture.entrants.zip(states)) {
                firebase.seed(
                    "lotteries/draw/entries/${actor.id}",
                    LotteryEntryDocument.encode(
                        LotteryEntry(
                            "draw",
                            actor.id,
                            state,
                            firebase.clock.instant(),
                            firebase.clock.instant(),
                        ),
                    ),
                )
            }
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(
                    fixture.lottery.copy(
                        waitingCount = 0,
                        drawnAt = firebase.clock.instant(),
                    ),
                ) + ("_revision" to 0L),
            )
            val withdrawn = firebase.registeredActor(Role.ENTRANT)
            firebase.seed(
                "lotteries/draw/entries/${withdrawn.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "draw",
                        withdrawn.id,
                        EntryState.WITHDRAWN,
                        firebase.clock.instant(),
                        firebase.clock.instant(),
                    ),
                ),
            )
            val id =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Reminder", "Bring water"),
                )
            val job =
                fixture.owner.clients.firestore
                    .document(
                        "lotteries/draw/_notificationDeliveries/$id",
                    ).get(Source.SERVER)
                    .await()
            assertEquals(
                fixture.entrants.map { it.id }.toSet(),
                (job.get("recipientIds") as List<*>).toSet(),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            for (actor in fixture.entrants) {
                assertEquals(
                    listOf(InboxReceipt(id, null)),
                    actor.container.notifications
                        .observeInbox(actor.id)
                        .first(),
                )
                assertEquals(
                    "Bring water",
                    actor.container.notifications
                        .get(id)!!
                        .announcement!!
                        .body,
                )
            }
            assertTrue(
                withdrawn.container.notifications
                    .observeInbox(withdrawn.id)
                    .first()
                    .isEmpty(),
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            assertEquals(
                1,
                admin.container.notifications
                    .observeAll()
                    .first()
                    .size,
            )
            assertFalse(firebase.documentExists("lotteries/draw/_notificationDeliveries/$id"))
        }

    /** A draw result remains pending until the final completion marker is stored. */
    @Test(timeout = 60000)
    fun drawResultDeliveryWaitsForCompletion() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 1, winners = 1)
            val recipient = fixture.entrants.single()
            val now = firebase.clock.instant()
            firebase.seed(
                "lotteries/draw/entries/${recipient.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "draw",
                        recipient.id,
                        EntryState.OFFERED,
                        now.minusSeconds(7200),
                        now,
                    ),
                ),
            )
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(
                    fixture.lottery.copy(waitingCount = 0),
                ) + ("_revision" to 0L),
            )
            val eventId = DocumentKeys.compound("LOTTERY_WON", "draw", recipient.id)
            val event =
                Notification(
                    eventId,
                    null,
                    NotificationType.LOTTERY_WON,
                    NotificationSubject(SubjectType.LOTTERY, "draw"),
                    null,
                    now,
                )
            firebase.seed("notifications/$eventId", NotificationDocument.encode(event))
            firebase.seed(
                "lotteries/draw/_notificationDeliveries/$eventId",
                mapOf(
                    "recipientIds" to listOf(recipient.id),
                ),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertTrue(
                recipient.container.notifications
                    .observeInbox(recipient.id)
                    .first()
                    .isEmpty(),
            )
            assertTrue(firebase.documentExists("lotteries/draw/_notificationDeliveries/$eventId"))
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(fixture.lottery.copy(waitingCount = 0, drawnAt = now)) +
                    ("_revision" to 0L),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertEquals(
                listOf(InboxReceipt(eventId, null)),
                recipient.container.notifications
                    .observeInbox(recipient.id)
                    .first(),
            )
            assertFalse(firebase.documentExists("lotteries/draw/_notificationDeliveries/$eventId"))
        }

    /** Resuming partial fan-out preserves existing read receipts and delivers the remaining ones. */
    @Test(timeout = 60000)
    fun partialDeliveryAndReplayPreserveAcknowledgements() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 2, winners = 1)
            val id =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Reminder", "Bring water"),
                )
            val first = fixture.entrants.first()
            val readAt = firebase.clock.instant().minusSeconds(30)
            // Delivery uses the captured audience even if membership changes after sending.
            val second = fixture.entrants.last()
            firebase.seed(
                "lotteries/draw/entries/${second.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "draw",
                        second.id,
                        EntryState.WITHDRAWN,
                        firebase.clock.instant(),
                        firebase.clock.instant(),
                    ),
                ),
            )
            firebase.seed(
                "accounts/${first.id}/inbox/$id",
                InboxReceiptDocument.encode(InboxReceipt(id, readAt)),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertEquals(
                listOf(InboxReceipt(id, readAt)),
                first.container.notifications
                    .observeInbox(first.id)
                    .first(),
            )
            assertEquals(
                listOf(InboxReceipt(id, null)),
                second.container.notifications
                    .observeInbox(second.id)
                    .first(),
            )
            firebase.seed(
                "lotteries/draw/_notificationDeliveries/$id",
                mapOf("recipientIds" to fixture.entrants.map { it.id }),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertEquals(
                listOf(InboxReceipt(id, readAt)),
                first.container.notifications
                    .observeInbox(first.id)
                    .first(),
            )
            assertEquals(
                1,
                second.container.notifications
                    .observeInbox(second.id)
                    .first()
                    .size,
            )
            assertFalse(firebase.documentExists("lotteries/draw/_notificationDeliveries/$id"))
        }

    /** A parent-scoped replay leaves another owner's queued announcement untouched. */
    @Test(timeout = 60000)
    fun scopedReplayDoesNotProcessUnrelatedWork() =
        runBlocking<Unit> {
            val fixture = firebase.drawFixture(entrants = 1, winners = 1)
            val otherOwner = firebase.registeredActor(Role.OWNER)
            firebase.seed(
                "lotteries/other",
                LotteryDocument.encode(
                    fixture.lottery.copy(id = "other", ownerId = otherOwner.id),
                ),
            )
            val id =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Here", "This lottery"),
                )
            val otherId =
                otherOwner.container.notifications.announce(
                    "other",
                    otherOwner.id,
                    Announcement("There", "Other lottery"),
                )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertFalse(firebase.documentExists("lotteries/draw/_notificationDeliveries/$id"))
            assertTrue(firebase.documentExists("lotteries/other/_notificationDeliveries/$otherId"))
        }

    /** A failed fan-out retains its job; repaired data lets a later attempt finish delivery. */
    @Test(timeout = 60000)
    fun deliveryFailureRetainsWorkForRetry() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 2, winners = 1)
            val id =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Reminder", "Bring water"),
                )
            val first = fixture.entrants.first()
            firebase.seed("accounts/${first.id}/inbox/$id", mapOf("readAt" to "invalid timestamp"))
            val admin = firebase.registeredActor(Role.ADMIN)
            try {
                NotificationDeliveries(
                    fixture.owner.clients.firestore,
                ).resume(DeliveryScope.Lottery("draw"))
                error("Expected receipt decoding failure")
            } catch (_: DocumentDecodingException) {
                assertTrue(firebase.documentExists("lotteries/draw/_notificationDeliveries/$id"))
            }
            firebase.seed(
                "accounts/${first.id}/inbox/$id",
                InboxReceiptDocument.encode(InboxReceipt(id, null)),
            )
            NotificationDeliveries(
                fixture.owner.clients.firestore,
            ).resume(DeliveryScope.Lottery("draw"))
            assertFalse(firebase.documentExists("lotteries/draw/_notificationDeliveries/$id"))
            for (recipient in fixture.entrants) {
                assertEquals(
                    listOf(InboxReceipt(id, null)),
                    recipient.container.notifications
                        .observeInbox(recipient.id)
                        .first(),
                )
            }
        }

    /** Marking a receipt read twice retains the first acknowledgement rather than moving its time. */
    @Test(timeout = 60000)
    fun markReadRetainsFirstTimestamp() =
        runBlocking {
            val recipient = firebase.registeredActor(Role.ENTRANT)
            firebase.seed(
                "accounts/${recipient.id}/inbox/event",
                InboxReceiptDocument.encode(InboxReceipt("event", null)),
            )
            val firstRead = firebase.clock.instant()
            recipient.container.notifications.markRead(recipient.id, "event")
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            recipient.container.notifications.markRead(recipient.id, "event")
            assertEquals(
                listOf(InboxReceipt("event", firstRead)),
                recipient.container.notifications
                    .observeInbox(recipient.id)
                    .first(),
            )
        }

    /** Sent history filters by sender; the administrative event log is newest first. */
    @Test(timeout = 60000)
    fun historiesFilterAndOrderEvents() =
        runBlocking {
            val fixture = firebase.drawFixture()
            val firstId =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("First", "Earlier"),
                )
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            val secondId =
                fixture.owner.container.notifications.announce(
                    "draw",
                    fixture.owner.id,
                    Announcement("Second", "Later"),
                )
            val other = firebase.registeredActor(Role.OWNER)
            firebase.seed(
                "notifications/other",
                NotificationDocument.encode(
                    Notification(
                        "other",
                        other.id,
                        NotificationType.OWNER_ANNOUNCEMENT,
                        NotificationSubject(SubjectType.LOTTERY, "other-draw"),
                        Announcement("Other", "Other owner"),
                        firebase.clock.instant().minusSeconds(120),
                    ),
                ),
            )
            assertEquals(
                listOf(
                    secondId,
                    firstId,
                ),
                fixture.owner.container.notifications
                    .observeSent(
                        fixture.owner.id,
                    ).first()
                    .map {
                        it.id
                    },
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            assertEquals(
                listOf(
                    secondId,
                    firstId,
                    "other",
                ),
                admin.container.notifications.observeAll().first().map {
                    it.id
                },
            )
        }

    /** An existing account with no receipts has a confirmed empty server history. */
    @Test(timeout = 60000)
    fun serverInboxConfirmsEmptyHistory() =
        runBlocking {
            val recipient = firebase.registeredActor(Role.ENTRANT)
            assertEquals(
                emptyList<InboxReceipt>(),
                recipient.container.notifications.getInbox(recipient.id),
            )
        }

    /** All receipt facts belong to one account and reflect server changes after caching. */
    @Test(timeout = 60000)
    fun serverInboxKeepsAccountScopeAndCurrentReceiptFacts() =
        runBlocking {
            val recipient = firebase.registeredActor(Role.ENTRANT)
            val other = firebase.registeredActor(Role.ENTRANT)
            val first = InboxReceipt("first", firebase.clock.instant().minusSeconds(60))
            val second = InboxReceipt("second", null)
            for (receipt in listOf(first, second)) {
                firebase.seed(
                    "accounts/${recipient.id}/inbox/${receipt.notificationId}",
                    InboxReceiptDocument.encode(receipt),
                )
            }
            firebase.seed(
                "accounts/${other.id}/inbox/foreign",
                InboxReceiptDocument.encode(InboxReceipt("foreign", null)),
            )
            val repository = recipient.container.notifications
            assertEquals(setOf(first, second), repository.getInbox(recipient.id).toSet())
            val updated = first.copy(readAt = firebase.clock.instant())
            firebase.seed(
                "accounts/${recipient.id}/inbox/first",
                InboxReceiptDocument.encode(updated),
            )
            firebase.request(
                "${firebase.firestoreEndpoint}/v1/projects/${firebase.projectId}" +
                    "/databases/(default)/documents/accounts/${recipient.id}/inbox/second",
                "DELETE",
            )
            assertEquals(listOf(updated), repository.getInbox(recipient.id))
        }

    /** Neither cached receipts nor cached empty history may satisfy a failed server baseline. */
    @Test(timeout = 60000)
    fun serverInboxFailureNeverReturnsCachedPresenceOrEmptyHistory() =
        runBlocking {
            val recipient = firebase.registeredActor(Role.ENTRANT)
            val db = recipient.clients.firestore
            val collection = db.collection("accounts/${recipient.id}/inbox")
            for (populated in listOf(false, true)) {
                if (populated) {
                    firebase.seed(
                        "accounts/${recipient.id}/inbox/cached",
                        InboxReceiptDocument.encode(InboxReceipt("cached", null)),
                    )
                }
                collection.get(Source.SERVER).await()
                db.disableNetwork().await()
                try {
                    val failure =
                        runCatching {
                            recipient.container.notifications.getInbox(recipient.id)
                        }.exceptionOrNull()
                    assertTrue(failure is FirebaseFirestoreException)
                    assertEquals(
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                        (failure as FirebaseFirestoreException).code,
                    )
                } finally {
                    db.enableNetwork().await()
                }
            }
        }

    /** Invalid receipt data fails with document context, never a successful partial baseline. */
    @Test(timeout = 60000)
    fun malformedServerInboxRetainsDocumentFailure() =
        runBlocking {
            val recipient = firebase.registeredActor(Role.ENTRANT)
            val path = "accounts/${recipient.id}/inbox/broken"
            firebase.seed(path, mapOf("readAt" to "not a timestamp"))
            val failure =
                runCatching {
                    recipient.container.notifications.getInbox(recipient.id)
                }.exceptionOrNull()
            assertTrue(failure is DocumentDecodingException)
            assertEquals(path, (failure as DocumentDecodingException).documentPath)
        }
}

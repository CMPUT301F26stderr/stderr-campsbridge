package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.notification

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
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
import org.junit.Assert.assertNull
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
                        "_notificationDeliveries/$id",
                    ).get(Source.SERVER)
                    .await()
            assertEquals(
                fixture.entrants.map { it.id }.toSet(),
                (job.get("recipientIds") as List<*>).toSet(),
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            admin.container.notifications.resumeDeliveries()
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
            assertEquals(
                1,
                admin.container.notifications
                    .observeAll()
                    .first()
                    .size,
            )
            assertFalse(firebase.documentExists("_notificationDeliveries/$id"))
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
                "_notificationDeliveries/$eventId",
                mapOf(
                    "recipientIds" to listOf(recipient.id),
                    "waitForDraw" to "draw",
                ),
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            admin.container.notifications.resumeDeliveries()
            assertTrue(
                recipient.container.notifications
                    .observeInbox(recipient.id)
                    .first()
                    .isEmpty(),
            )
            assertTrue(firebase.documentExists("_notificationDeliveries/$eventId"))
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(fixture.lottery.copy(waitingCount = 0, drawnAt = now)) +
                    ("_revision" to 0L),
            )
            admin.container.notifications.resumeDeliveries()
            assertEquals(
                listOf(InboxReceipt(eventId, null)),
                recipient.container.notifications
                    .observeInbox(recipient.id)
                    .first(),
            )
            assertFalse(firebase.documentExists("_notificationDeliveries/$eventId"))
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
            firebase.seed(
                "accounts/${first.id}/inbox/$id",
                InboxReceiptDocument.encode(InboxReceipt(id, readAt)),
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            admin.container.notifications.resumeDeliveries()
            assertEquals(
                listOf(InboxReceipt(id, readAt)),
                first.container.notifications
                    .observeInbox(first.id)
                    .first(),
            )
            val second = fixture.entrants.last()
            assertEquals(
                listOf(InboxReceipt(id, null)),
                second.container.notifications
                    .observeInbox(second.id)
                    .first(),
            )
            firebase.seed(
                "_notificationDeliveries/$id",
                mapOf("recipientIds" to fixture.entrants.map { it.id }, "waitForDraw" to null),
            )
            admin.container.notifications.resumeDeliveries()
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
            assertFalse(firebase.documentExists("_notificationDeliveries/$id"))
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
                admin.container.notifications.resumeDeliveries()
                error("Expected receipt decoding failure")
            } catch (_: DocumentDecodingException) {
                assertTrue(firebase.documentExists("_notificationDeliveries/$id"))
            }
            firebase.seed(
                "accounts/${first.id}/inbox/$id",
                InboxReceiptDocument.encode(InboxReceipt(id, null)),
            )
            admin.container.notifications.resumeDeliveries()
            assertFalse(firebase.documentExists("_notificationDeliveries/$id"))
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
}

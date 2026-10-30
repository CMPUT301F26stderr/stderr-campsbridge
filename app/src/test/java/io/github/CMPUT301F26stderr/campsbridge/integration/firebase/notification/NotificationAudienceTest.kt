package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.notification

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationAudienceQueries
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudience
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceDelivery
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceMember
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationAudienceUnavailable
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.time.Instant

/** Twelve fixed real-Firestore contracts for read-only captured and delivered audience facts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class NotificationAudienceTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Captured identities survive missing accounts and repeated IDs without invented members. */
    @Test(timeout = 60000)
    fun capturedPendingRecipientsAreUniqueAndRetainMissingAccounts() =
        runBlocking {
            val f = fixture()
            f.job(listOf(f.second.id, "missing-account", f.first.id, f.second.id))
            f.unchanged {
                assertEquals(
                    recorded(
                        member(f.first.id),
                        member(f.second.id),
                        member("missing-account"),
                    ),
                    f.read(),
                )
            }
        }

    /** Confirmed receipt delivery/read state wins over captured pending membership. */
    @Test(timeout = 60000)
    fun partialDeliveryPreservesFirstReadAndReceiptPrecedence() =
        runBlocking {
            val f = fixture()
            val readAt = firebase.clock.instant().minusSeconds(30)
            f.job(listOf(f.first.id, f.second.id, "not-delivered"))
            f.receipt(f.first.id, readAt)
            f.receipt(f.second.id, null)
            f.unchanged {
                assertEquals(
                    recorded(
                        member(f.first.id, true, readAt),
                        member(f.second.id, true),
                        member("not-delivered"),
                    ),
                    f.read(),
                )
            }
        }

    /** Completed jobs leave retained receipts, including scrubbed accounts, as the evidence. */
    @Test(timeout = 60000)
    fun completedJobReturnsDeliveredAndReadReceiptsFromRetainedAccounts() =
        runBlocking {
            val f = fixture()
            val readAt = firebase.clock.instant().minusSeconds(30)
            val removed =
                CampingFixtures.account(f.second.id).copy(
                    name = "",
                    roles = emptySet(),
                    removal = Removal(firebase.clock.instant(), f.admin.id),
                )
            firebase.seed(FirestorePaths.account(f.second.id), AccountDocument.encode(removed))
            f.receipt(f.first.id, null)
            f.receipt(f.second.id, readAt)
            f.unchanged {
                assertEquals(
                    recorded(member(f.first.id, true), member(f.second.id, true, readAt)),
                    f.read(),
                )
            }
        }

    /** Actual job deletion after capture cannot erase an intended identity without an account. */
    @Test(timeout = 60000)
    fun completedDuringAssemblyKeepsCapturedMissingAccountIdentity() =
        runBlocking {
            val f = fixture()
            f.job(listOf(f.first.id, "missing-account"))
            f.receipt(f.first.id, null)
            val entered = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            val db = f.admin.clients.firestore
            val reader =
                NotificationAudienceQueries(db) { accountId, eventId ->
                    if (entered.complete(Unit)) proceed.await()
                    db
                        .document(
                            FirestorePaths.receipt(accountId, eventId),
                        ).read(InboxReceiptDocument)
                }
            val pending = async { reader.get(f.event.id) }
            try {
                val reachedReceipt =
                    select {
                        entered.onAwait { true }
                        pending.onAwait { false }
                    }
                assertTrue(reachedReceipt)
                firebase.erase(f.jobPath)
                f.unchanged {
                    proceed.complete(Unit)
                    assertEquals(
                        recorded(member(f.first.id, true), member("missing-account")),
                        pending.await(),
                    )
                }
            } finally {
                proceed.complete(Unit)
                pending.cancel()
            }
        }

    /** Missing job and receipts are unknown intended membership, never a confirmed empty set. */
    @Test(timeout = 60000)
    fun absentJobAndReceiptsReturnNoRecordedInformation() =
        runBlocking {
            val f = fixture()
            f.unchanged { assertEquals(NotificationAudience.NoRecordedInformation, f.read()) }
        }

    /** Captured empty membership is explicit evidence of an empty intended audience. */
    @Test(timeout = 60000)
    fun capturedEmptyJobIsARecordedEmptyAudience() =
        runBlocking {
            val f = fixture()
            f.job(emptyList())
            f.unchanged { assertEquals(NotificationAudience.Recorded(emptyList()), f.read()) }
        }

    /** Absent original events and supported parents have distinct typed unavailable outcomes. */
    @Test(timeout = 60000)
    fun missingEventAndMissingParentAreUnavailable() =
        runBlocking {
            val f = fixture()
            f.unchanged {
                assertEquals(
                    NotificationAudience.Unavailable(NotificationAudienceUnavailable.EVENT_MISSING),
                    f.admin.container.notifications
                        .getAudience("absent-event"),
                )
            }
            firebase.erase("lotteries/draw")
            f.unchanged {
                assertEquals(
                    NotificationAudience.Unavailable(
                        NotificationAudienceUnavailable.SUBJECT_MISSING,
                    ),
                    f.read(),
                )
            }
        }

    /** Final Message events never guess that their message identity is a reservation scope. */
    @Test(timeout = 60000)
    fun messageSubjectIsTypedUnsupportedWithoutReservationGuess() =
        runBlocking {
            val f = fixture()
            val message =
                f.event.copy(
                    type = NotificationType.NEW_MESSAGE,
                    subject = NotificationSubject(SubjectType.MESSAGE, "message-id"),
                )
            firebase.seed(
                FirestorePaths.notification(message.id),
                NotificationDocument.encode(message),
            )
            firebase.seed(
                "reservations/message-id/_notificationDeliveries/${message.id}",
                mapOf("recipientIds" to listOf(f.first.id)),
            )
            f.unchanged {
                assertEquals(
                    NotificationAudience.Unavailable(
                        NotificationAudienceUnavailable.UNSUPPORTED_SUBJECT,
                    ),
                    f.read(),
                )
            }
        }

    /** Reservation events read their own job and exact event receipts, ignoring other scopes. */
    @Test(timeout = 60000)
    fun reservationEventsUseTheActualReservationScope() =
        runBlocking {
            val f = fixture()
            val reservation = CampingFixtures.reservation().copy(entrantId = f.first.id)
            val event =
                f.event.copy(
                    type = NotificationType.RESERVATION_CONFIRMED,
                    subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
                )
            firebase.seed(
                FirestorePaths.reservation(reservation.id),
                ReservationDocument.encode(reservation),
            )
            firebase.seed(FirestorePaths.notification(event.id), NotificationDocument.encode(event))
            f.track(FirestorePaths.reservation(reservation.id))
            val job = "reservations/${reservation.id}/_notificationDeliveries/${event.id}"
            f.track(job)
            firebase.seed(job, mapOf("recipientIds" to listOf(f.first.id)))
            f.job(listOf(f.second.id))
            firebase.seed(
                FirestorePaths.receipt(f.second.id, "different-event"),
                InboxReceiptDocument.encode(InboxReceipt("different-event", null)),
            )
            f.unchanged { assertEquals(recorded(member(f.first.id)), f.read()) }
        }

    /** Cached records cannot replace any required confirmed server audience read. */
    @Test(timeout = 60000)
    fun networkFailureDoesNotReturnCachedAudienceFacts() =
        runBlocking {
            val f = fixture()
            f.job(listOf(f.first.id))
            val db = f.admin.clients.firestore
            db.document(FirestorePaths.notification(f.event.id)).get(Source.SERVER).await()
            db.document(f.jobPath).get(Source.SERVER).await()
            f.unchanged {
                db.disableNetwork().await()
                try {
                    val failure = runCatching { f.read() }.exceptionOrNull()
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

    /** Invalid intended identities or receipt data fail rather than returning a partial result. */
    @Test(timeout = 60000)
    fun malformedJobAndReceiptFailWithoutPartialSuccess() =
        runBlocking {
            val f = fixture()
            for (ids in listOf(listOf("bad/id"), listOf(""), listOf(1L))) {
                firebase.seed(f.jobPath, mapOf("recipientIds" to ids))
                f.unchanged {
                    assertTrue(
                        runCatching { f.read() }.exceptionOrNull() is DocumentDecodingException,
                    )
                }
            }
            f.job(listOf(f.first.id, f.second.id))
            f.receipt(f.first.id, null)
            firebase.seed(FirestorePaths.receipt(f.second.id, f.event.id), mapOf("readAt" to "bad"))
            f.unchanged {
                assertTrue(runCatching { f.read() }.exceptionOrNull() is DocumentDecodingException)
            }
        }

    /** One required receipt failure remains failure after real server event/job/account reads. */
    @Test(timeout = 60000)
    fun receiptReadFailureCannotProduceASuccessfulPartialAudience() =
        runBlocking {
            val f = fixture()
            f.job(listOf(f.first.id))
            val reader =
                NotificationAudienceQueries(f.admin.clients.firestore) { _, _ ->
                    throw IOException("required receipt unavailable")
                }
            f.unchanged {
                assertTrue(runCatching { reader.get(f.event.id) }.exceptionOrNull() is IOException)
            }
        }

    /** Seeds only actual canonical records with no business mutation or delivery invocation. */
    private suspend fun fixture(): Fixture {
        val admin = firebase.registeredActor(Role.ADMIN)
        val first = firebase.registeredActor(Role.ENTRANT)
        val second = firebase.registeredActor(Role.ENTRANT)
        val lottery = CampingFixtures.lottery(ownerId = admin.id)
        firebase.seed("lotteries/draw", LotteryDocument.encode(lottery))
        val event =
            Notification(
                "event",
                null,
                NotificationType.LOTTERY_WON,
                NotificationSubject(SubjectType.LOTTERY, lottery.id),
                null,
                firebase.clock.instant(),
            )
        firebase.seed(FirestorePaths.notification(event.id), NotificationDocument.encode(event))
        return Fixture(admin, first, second, event)
    }

    /** Builds stable expected membership independently of query assembly.
     * @param members Exact expected factual members.
     */
    private fun recorded(vararg members: NotificationAudienceMember): NotificationAudience =
        NotificationAudience.Recorded(members.sortedBy { it.accountId })

    /** Builds one expected pending or confirmed Inbox delivery fact.
     * @param id Original intended/receipt account identity.
     * @param delivered Whether a receipt exists.
     * @param readAt Original first-read time, when confirmed.
     */
    private fun member(
        id: String,
        delivered: Boolean = false,
        readAt: Instant? = null,
    ): NotificationAudienceMember =
        NotificationAudienceMember(
            id,
            if (delivered) {
                NotificationAudienceDelivery.DELIVERED
            } else {
                NotificationAudienceDelivery.PENDING
            },
            readAt,
        )

    /** Owns real actors and a fixed relevant-document snapshot for no-write checks.
     * @param admin Actual provisioned admin client executing production reads.
     * @param first Existing recipient account.
     * @param second Existing recipient account.
     * @param event Original immutable event fixture.
     */
    private inner class Fixture(
        val admin: EmulatorActor,
        val first: EmulatorActor,
        val second: EmulatorActor,
        val event: Notification,
    ) {
        val jobPath = "lotteries/draw/_notificationDeliveries/${event.id}"
        private val paths =
            mutableSetOf(
                FirestorePaths.notification(event.id),
                "lotteries/draw",
                jobPath,
                FirestorePaths.account(admin.id),
                FirestorePaths.account(first.id),
                FirestorePaths.account(second.id),
                FirestorePaths.receipt(admin.id, event.id),
                FirestorePaths.receipt(first.id, event.id),
                FirestorePaths.receipt(second.id, event.id),
                FirestorePaths.receipt("missing-account", event.id),
                FirestorePaths.receipt("not-delivered", event.id),
                "reservations/message-id/_notificationDeliveries/${event.id}",
                FirestorePaths.receipt(second.id, "different-event"),
            )

        /** Reads through the actual public repository contract. */
        suspend fun read(): NotificationAudience =
            admin.container.notifications.getAudience(event.id)

        /** Seeds captured membership independently of delivery.
         * @param ids Original intended identities, including deliberate invalid fixtures.
         */
        fun job(ids: List<String>) {
            firebase.seed(jobPath, mapOf("recipientIds" to ids))
        }

        /** Seeds one already delivered/read receipt without calling production acknowledgements.
         * @param accountId Exact recipient identity.
         * @param readAt Confirmed first-read timestamp or null.
         */
        fun receipt(
            accountId: String,
            readAt: Instant?,
        ) {
            firebase.seed(
                FirestorePaths.receipt(accountId, event.id),
                InboxReceiptDocument.encode(InboxReceipt(event.id, readAt)),
            )
        }

        /** Includes a scenario-specific record in its no-write snapshot.
         * @param path Actual canonical document path.
         */
        fun track(path: String) {
            paths.add(path)
        }

        /** Compares every relevant actual stored field and document absence after inspection.
         * @param block Fixed contract actions/assertions without fixture writes.
         */
        suspend fun unchanged(block: suspend () -> Unit) {
            val before = snapshot()
            block()
            assertEquals(before, snapshot())
        }

        /** Reads server snapshots without decoding away unexpected stored-field changes. */
        private suspend fun snapshot(): Map<String, Map<String, Any?>?> =
            paths.associateWith {
                admin.clients.firestore
                    .document(it)
                    .get(Source.SERVER)
                    .await()
                    .data
            }
    }
}

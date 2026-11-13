/*
 * Receipt/event assembly and explicit opening never invent time, unread counts or acknowledgements.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Receipt/event assembly and explicit opening never invent time, unread counts or
 * acknowledgements.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InboxActionTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val lottery =
        CampingFixtures.lottery().copy(
            configuration =
                LotteryConfiguration(
                    CampingFixtures.now.minusSeconds(600),
                    CampingFixtures.now.minusSeconds(300),
                    CampingFixtures.now.minusSeconds(240),
                    CampingFixtures.now.plusSeconds(300),
                    1,
                    null,
                ),
            createdAt = CampingFixtures.now.minusSeconds(900),
            publishedAt = CampingFixtures.now.minusSeconds(900),
            drawnAt = CampingFixtures.now.minusSeconds(250),
        )
    private val lostLottery = lottery.copy(id = "other-draw")
    private val won = event("won", NotificationType.LOTTERY_WON)
    private val lost = event("lost", NotificationType.LOTTERY_LOST)
    private val notifications = FakeNotificationRepository(listOf(won, lost))
    private val lotteries = FakeLotteryRepository(listOf(lottery, lostLottery))
    private val bookings = FakeBookingRepository(listOf(CampingFixtures.reservation()))

    /**
     * Opening a loaded row emits navigation synchronously while its independent read write is
     * held.
     */
    @Test fun openRequestDoesNotWaitForReadAcknowledgement() =
        withModel { model ->
            val gate = CompletableDeferred<Unit>()
            try {
                notifications.markReadHandler = { _, _ -> gate.await() }
                receipts(InboxReceipt(won.id, null))
                runCurrent()
                model.openItem(won.id)
                assertEquals(InboxOpenRequest(won.id, won.subject), model.pendingOpen.value)
                runCurrent()
                assertEquals(listOf("entrant" to won.id), notifications.readRequests)
                model.acknowledgeOpen("other-event")
                assertEquals(won.id, model.pendingOpen.value?.notificationId)
                model.acknowledgeOpen(won.id)
                assertNull(model.pendingOpen.value)
                assertNull(sole(model).readAt)
                gate.complete(Unit)
                runCurrent()
                assertNull(sole(model).readAt)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Repeated opens cannot stack route requests or duplicate an already held acknowledgement. */
    @Test fun duplicateOpenAndHeldAcknowledgementUseSeparateGuards() =
        withModel { model ->
            val gate = CompletableDeferred<Unit>()
            try {
                notifications.markReadHandler = { _, _ -> gate.await() }
                receipts(InboxReceipt(won.id, null), InboxReceipt(lost.id, null))
                runCurrent()
                model.openItem(won.id)
                model.openItem(won.id)
                model.openItem(lost.id)
                runCurrent()
                assertEquals(won.id, model.pendingOpen.value?.notificationId)
                model.acknowledgeOpen(won.id)
                model.openItem(won.id)
                runCurrent()
                assertEquals(listOf("entrant" to won.id), notifications.readRequests)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Failed acknowledgement remains separately retryable after navigation has been consumed. */
    @Test fun failedReadAcknowledgementDoesNotBlockOpeningOrRetryNavigation() =
        withModel { model ->
            notifications.markReadHandler = { _, _ -> throw IOException("read write offline") }
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            model.openItem(won.id)
            model.acknowledgeOpen(won.id)
            runCurrent()
            assertNull(model.pendingOpen.value)
            assertEquals(setOf(won.id), model.state.value.readFailures)
            assertEquals(won, sole(model).notification)
            notifications.markReadHandler = { _, _ -> }
            model.retryReadAcknowledgement(won.id)
            model.retryReadAcknowledgement(won.id)
            runCurrent()
            assertTrue(
                model.state.value.readFailures
                    .isEmpty(),
            )
            assertNull(model.pendingOpen.value)
            assertEquals(List(2) { "entrant" to won.id }, notifications.readRequests)
        }

    /** A pre-existing first-read timestamp needs no acknowledgement and still permits opening. */
    @Test fun alreadyReadReceiptOpensWithoutReplacingItsTimestamp() =
        withModel { model ->
            receipts(InboxReceipt(won.id, CampingFixtures.now))
            runCurrent()
            model.openItem(won.id)
            runCurrent()
            assertEquals(won.subject, model.pendingOpen.value?.subject)
            assertEquals(CampingFixtures.now, sole(model).readAt)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /**
     * A confirmed read observation defeats a late transport failure from the same
     * acknowledgement.
     */
    @Test fun observedReadKeepsLateWriteFailureOutOfTheRow() =
        withModel { model ->
            val gate = CompletableDeferred<Unit>()
            try {
                notifications.markReadHandler =
                    { _, _ ->
                        gate.await()
                        throw IOException("late acknowledgement")
                    }
                receipts(InboxReceipt(won.id, null))
                runCurrent()
                model.openItem(won.id)
                runCurrent()
                receipts(InboxReceipt(won.id, CampingFixtures.now))
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertTrue(
                    model.state.value.readFailures
                        .isEmpty(),
                )
                model.retryReadAcknowledgement(won.id)
                runCurrent()
                assertEquals(1, notifications.readRequests.size)
                assertEquals(CampingFixtures.now, sole(model).readAt)
            } finally {
                gate.complete(Unit)
            }
        }

    /**
     * Missing, failed and unsupported context cannot open or write just because a receipt
     * exists.
     */
    @Test fun unavailableSubjectsAndEventsDoNotCreateDeadTargets() =
        withModel { model ->
            val message = event("message", NotificationType.NEW_MESSAGE)
            notifications.records.value += message
            lotteries.records.value = emptyList()
            receipts(
                InboxReceipt(won.id, null),
                InboxReceipt(message.id, null),
                InboxReceipt("missing", null),
            )
            runCurrent()
            for (id in listOf(won.id, message.id, "missing", "not-a-receipt")) model.openItem(id)
            assertNull(model.pendingOpen.value)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /**
     * Loaded reservation notices carry their canonical typed destination without reconstructing
     * keys.
     */
    @Test fun reservationNoticeOpensTheActualSavedReference() =
        withModel { model ->
            val saved = event("reservation-event", NotificationType.RESERVATION_CONFIRMED)
            notifications.records.value += saved
            notifications.markReadHandler = { _, _ -> }
            receipts(InboxReceipt(saved.id, null))
            runCurrent()
            model.openItem(saved.id)
            assertEquals(saved.subject, model.pendingOpen.value?.subject)
            runCurrent()
            assertEquals(listOf("entrant" to saved.id), notifications.readRequests)
        }

    /**
     * Retiring selected receipt clears its pending open and rejects a late acknowledgement
     * error.
     */
    @Test fun removedReceiptRetiresItsOpenAndReadWrite() =
        withModel { model ->
            val gate = CompletableDeferred<Unit>()
            try {
                notifications.markReadHandler =
                    { _, _ ->
                        withContext(NonCancellable) { gate.await() }
                        throw IOException("late")
                    }
                receipts(InboxReceipt(won.id, null))
                runCurrent()
                model.openItem(won.id)
                runCurrent()
                receipts()
                runCurrent()
                assertNull(model.pendingOpen.value)
                gate.complete(Unit)
                runCurrent()
                assertTrue(
                    model.state.value.readFailures
                        .isEmpty(),
                )
            } finally {
                gate.complete(Unit)
            }
        }

    /** Caller cancellation is not a failed receipt write and does not erase loaded history. */
    @Test fun cancelledAcknowledgementDoesNotShowFailure() =
        withModel { model ->
            notifications.markReadHandler = { _, _ -> throw CancellationException("cancelled") }
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            model.openItem(won.id)
            model.acknowledgeOpen(won.id)
            runCurrent()
            assertTrue(
                model.state.value.readFailures
                    .isEmpty(),
            )
            assertEquals(won, sole(model).notification)
        }

    /**
     * Graph/account retirement cancels receipt observation and cannot be revived by stale
     * actions.
     */
    @Test fun clearedModelCannotRestartReadsOrOpenRoutes() =
        withModel { model ->
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            store.clear()
            model.retry()
            model.retryItem(won.id)
            model.openItem(won.id)
            model.retryReadAcknowledgement(won.id)
            runCurrent()
            assertEquals(0, notifications.activeInboxObservers)
            assertNull(model.pendingOpen.value)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /** Requires one actual loaded row before inspecting its facts.
     * @param model Account owner under test.
     */
    private fun sole(model: InboxViewModel): InboxItem {
        assertEquals("Expected exactly one loaded event", 1, model.state.value.items.size)
        return model.state.value.items
            .single()
    }

    /** Requires one actual unknown-event receipt before inspecting its outcome.
     * @param model Account owner under test.
     */
    private fun unknown(model: InboxViewModel): UnavailableInboxReceipt {
        assertEquals(
            "Expected exactly one unresolved receipt",
            1,
            model.state.value.unavailable.size,
        )
        return model.state.value.unavailable
            .single()
    }

    /** Supplies one account's explicit receipt facts without simulating write outcomes.
     * @param rows Original unique receipt records.
     */
    private fun receipts(vararg rows: InboxReceipt) {
        notifications.receipts.value = mapOf("entrant" to rows.toList())
    }

    /** Creates a valid immutable event pointing at existing subject fixtures.
     * @param id Actual event identity.
     * @param type Template category and subject kind.
     */
    private fun event(
        id: String,
        type: NotificationType,
    ): Notification {
        val subject =
            when (type) {
                NotificationType.NEW_MESSAGE -> {
                    NotificationSubject(SubjectType.MESSAGE, "message")
                }

                NotificationType.LOTTERY_LOST -> {
                    NotificationSubject(
                        SubjectType.LOTTERY,
                        lostLottery.id,
                    )
                }

                NotificationType.RESERVATION_CONFIRMED -> {
                    NotificationSubject(
                        SubjectType.RESERVATION,
                        "reservation",
                    )
                }

                else -> {
                    NotificationSubject(SubjectType.LOTTERY, "draw")
                }
            }
        return Notification(id, null, type, subject, null, CampingFixtures.now)
    }

    /** Retires every graph job before virtual time drains controlled work.
     * @param sort Fixed supplied ordering outcome; null leaves F19 pending.
     * @param count Fixed supplied unread outcome; null leaves F20 pending.
     * @param body Observable controller contract.
     */
    private fun withModel(
        sort: ((List<InboxItem>) -> List<InboxItem>)? = null,
        count: ((List<InboxReceipt>) -> Int)? = null,
        body: suspend TestScope.(InboxViewModel) -> Unit,
    ) =
        runTest {
            val model =
                InboxViewModel(
                    "entrant",
                    notifications,
                    NotificationContentLoader(notifications, lotteries, bookings, FakeMessageRepository()),
                    sort,
                    count,
                )
            store.put("inbox", model)
            try {
                body(model)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

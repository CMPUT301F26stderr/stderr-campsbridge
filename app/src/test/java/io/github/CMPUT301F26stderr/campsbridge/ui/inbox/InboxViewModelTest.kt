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
class InboxViewModelTest {
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
     * Initial loading and confirmed empty are distinct; no helper fallback or write is invented.
     */
    @Test fun confirmedEmptyStillKeepsMissingHelpersExplicit() =
        withModel { model ->
            assertEquals(InboxSourceState.LOADING, model.state.value.source)
            runCurrent()
            assertEquals(InboxSourceState.READY, model.state.value.source)
            assertTrue(
                model.state.value.items
                    .isEmpty(),
            )
            assertTrue(
                model.state.value.unavailable
                    .isEmpty(),
            )
            assertTrue(model.state.value.orderingPending)
            assertTrue(model.state.value.unreadCountPending)
            assertNull(model.state.value.unreadCount)
            assertEquals(listOf("entrant"), notifications.inboxRequests)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /**
     * One account source ignores foreign receipts and arriving content has no navigation/write
     * effect.
     */
    @Test fun receiptArrivalOnlyLoadsItsOwnEvents() =
        withModel { model ->
            receipts(InboxReceipt(won.id, null))
            notifications.receipts.value += "other-account" to listOf(InboxReceipt(lost.id, null))
            runCurrent()
            assertEquals(
                listOf(won.id),
                model.state.value.items
                    .map { it.notification.id },
            )
            assertEquals(listOf(won.id), notifications.getRequests)
            assertEquals(1, notifications.activeInboxObservers)
            assertNull(model.pendingOpen.value)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /**
     * Receipt read-state changes reuse loaded event/context and retain the observed first
     * timestamp.
     */
    @Test fun readStateChangesDoNotReloadOrRewriteContent() =
        withModel { model ->
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            val firstRead = CampingFixtures.now.plusSeconds(5)
            receipts(InboxReceipt(won.id, firstRead))
            runCurrent()
            assertEquals(firstRead, sole(model).readAt)
            assertEquals(listOf(won.id), notifications.getRequests)
            assertEquals(listOf("get:${lottery.id}"), lotteries.events)
            assertTrue(notifications.readRequests.isEmpty())
        }

    /**
     * Missing/failed event receipts retain real read state after sortable known events, without
     * time.
     */
    @Test fun unknownEventReceiptsAreSeparateAndStableByIdentity() =
        withModel { model ->
            receipts(
                InboxReceipt("missing-z", null),
                InboxReceipt(won.id, null),
                InboxReceipt("missing-a", CampingFixtures.now),
            )
            runCurrent()
            assertEquals(
                listOf(won.id),
                model.state.value.items
                    .map { it.notification.id },
            )
            assertEquals(
                listOf("missing-a", "missing-z"),
                model.state.value.unavailable.map {
                    it.receipt.notificationId
                },
            )
            assertTrue(
                model.state.value.unavailable
                    .all { it.status == InboxEventStatus.UNAVAILABLE },
            )
            assertEquals(
                CampingFixtures.now,
                model.state.value.unavailable
                    .first()
                    .receipt.readAt,
            )
        }

    /**
     * A held event remains an unknown-time loading receipt rather than a missing or empty
     * result.
     */
    @Test fun heldEventKeepsItsLoadingReceiptVisible() =
        withModel { model ->
            val gate = CompletableDeferred<Notification?>()
            try {
                notifications.getHandler = { gate.await() }
                receipts(InboxReceipt(won.id, null))
                runCurrent()
                assertTrue(
                    model.state.value.items
                        .isEmpty(),
                )
                assertEquals(InboxEventStatus.LOADING, unknown(model).status)
                assertEquals(InboxSourceState.READY, model.state.value.source)
                model.openItem(won.id)
                assertNull(model.pendingOpen.value)
                assertTrue(notifications.readRequests.isEmpty())
            } finally {
                gate.complete(won)
            }
        }

    /** Loaded-event context failures retain original identity/time in the reserved sorter input. */
    @Test fun failedSubjectRemainsAKnownEventForSuppliedOrdering() =
        withModel(
            sort = { rows ->
                listOf(lost.id, won.id).mapNotNull { id ->
                    rows.firstOrNull {
                        it.notification.id ==
                            id
                    }
                }
            },
        ) { model ->
            lotteries.getHandler = { throw IOException("subject offline") }
            receipts(
                InboxReceipt(won.id, null),
                InboxReceipt("missing", null),
                InboxReceipt(lost.id, null),
            )
            runCurrent()
            assertEquals(
                listOf(lost, won),
                model.state.value.items
                    .map { it.notification },
            )
            assertTrue(
                model.state.value.items
                    .all { it.subject == NotificationSubjectContent.Failure },
            )
            assertFalse(model.state.value.orderingPending)
            assertEquals(
                listOf("missing"),
                model.state.value.unavailable
                    .map { it.receipt.notificationId },
            )
        }

    /** The reserved counter receives every actual receipt, including unavailable event content. */
    @Test fun suppliedUnreadCountReceivesTheOriginalReceipts() =
        withModel(
            count = { rows ->
                assertEquals(
                    setOf(won.id, lost.id, "missing"),
                    rows.map { it.notificationId }.toSet(),
                )
                2
            },
        ) { model ->
            receipts(
                InboxReceipt(won.id, null),
                InboxReceipt(lost.id, CampingFixtures.now),
                InboxReceipt("missing", null),
            )
            runCurrent()
            assertEquals(2, model.state.value.unreadCount)
            assertFalse(model.state.value.unreadCountPending)
            assertTrue(model.state.value.orderingPending)
            assertEquals(1, model.state.value.unavailable.size)
        }

    /**
     * Source failure is not confirmed empty; explicit duplicate Retry installs one replacement
     * source.
     */
    @Test fun failedReceiptSourceRetriesWithoutDuplicateObservers() =
        withModel { model ->
            notifications.inboxFailure.value = IOException("receipts offline")
            runCurrent()
            assertEquals(InboxSourceState.FAILURE, model.state.value.source)
            notifications.inboxFailure.value = null
            receipts(InboxReceipt(won.id, null))
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(InboxSourceState.READY, model.state.value.source)
            assertEquals(2, notifications.inboxRequests.size)
            assertEquals(1, notifications.activeInboxObservers)
            assertEquals(won, sole(model).notification)
        }

    /** Previously loaded facts stay readable through source retry without new event queries. */
    @Test fun failedLiveSourceRetainsItsKnownRows() =
        withModel { model ->
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            notifications.inboxFailure.value = IOException("receipts offline")
            runCurrent()
            assertEquals(InboxSourceState.FAILURE, model.state.value.source)
            assertEquals(won, sole(model).notification)
            notifications.inboxFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(listOf(won.id), notifications.getRequests)
        }

    /** Event failure and explicit retry affect only that retained identity. */
    @Test fun eventRetryDoesNotRestartTheAccountOrOtherEvents() =
        withModel { model ->
            notifications.getHandler =
                { id -> if (id == won.id) throw IOException("one event offline") else lost }
            receipts(InboxReceipt(won.id, null), InboxReceipt(lost.id, null))
            runCurrent()
            assertEquals(InboxEventStatus.FAILURE, unknown(model).status)
            notifications.getHandler = null
            model.retryItem(won.id)
            model.retryItem(won.id)
            runCurrent()
            assertTrue(
                model.state.value.unavailable
                    .isEmpty(),
            )
            assertEquals(2, notifications.getRequests.count { it == won.id })
            assertEquals(1, notifications.getRequests.count { it == lost.id })
            assertEquals(1, notifications.inboxRequests.size)
        }

    /** Retrying failed context retains its real event/time while replacement context is held. */
    @Test fun subjectRetryRetainsTheKnownEventWhileLoading() =
        withModel { model ->
            lotteries.getHandler = { throw IOException("subject offline") }
            receipts(InboxReceipt(won.id, null))
            runCurrent()
            val gate =
                CompletableDeferred<Lottery?>()
            try {
                lotteries.getHandler = { gate.await() }
                model.retryItem(won.id)
                runCurrent()
                val row = sole(model)
                assertEquals(won, row.notification)
                assertTrue(row.isLoading)
                assertTrue(
                    model.state.value.unavailable
                        .isEmpty(),
                )
                model.openItem(won.id)
                assertNull(model.pendingOpen.value)
                gate.complete(lottery)
                runCurrent()
                assertFalse(sole(model).isLoading)
                assertEquals(
                    NotificationSubjectContent.LotteryContent(lottery),
                    sole(model).subject,
                )
            } finally {
                gate.complete(lottery)
            }
        }

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

    /** Removing a receipt retires its held event read and later re-addition gets a fresh load. */
    @Test fun removedReceiptRejectsLateContentAndReloadsWhenReadded() =
        withModel { model ->
            val gate = CompletableDeferred<Notification?>()
            try {
                notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
                receipts(InboxReceipt(won.id, null))
                runCurrent()
                receipts()
                runCurrent()
                notifications.getHandler = null
                receipts(InboxReceipt(lost.id, null))
                runCurrent()
                gate.complete(won)
                runCurrent()
                assertEquals(
                    listOf(lost.id),
                    model.state.value.items
                        .map { it.notification.id },
                )
                receipts(InboxReceipt(lost.id, null), InboxReceipt(won.id, null))
                runCurrent()
                assertEquals(2, notifications.getRequests.count { it == won.id })
            } finally {
                gate.complete(won)
            }
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
                    NotificationContentLoader(notifications, lotteries, bookings),
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

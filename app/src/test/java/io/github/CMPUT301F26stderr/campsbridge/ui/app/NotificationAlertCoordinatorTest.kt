package io.github.CMPUT301F26stderr.campsbridge.ui.app

import android.content.Intent
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationAlert
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermissionState
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPublisher
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationText
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.AlertDeliveryStore
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Eighteen fixed session contracts; platform publication and copy are controlled effects. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationAlertCoordinatorTest {
    /** The explicit cutoff uses the server history, never an observed cache snapshot. */
    @Test fun prepareHistoryUsesOnlyConfirmedServerReceiptIds() =
        scenario { f ->
            f.receipts(f.won.id)
            f.notifications.getInboxHandler = {
                listOf(InboxReceipt("history", null), InboxReceipt("read", CampingFixtures.now))
            }
            assertEquals(setOf("history", "read"), f.coordinator.prepareHistory(f.account.id))
            assertEquals(listOf(f.account.id), f.notifications.inboxReadRequests)
            assertTrue(f.notifications.inboxRequests.isEmpty())
            assertEquals(0, f.accounts.observations)
            assertTrue(f.publisher.posts.isEmpty())
            assertTrue(f.handled.requests.isEmpty())
        }

    /** Failed or cancelled server reads propagate without becoming an empty success. */
    @Test fun prepareFailureAndCancellationRemainFailures() =
        scenario { f ->
            for (failure in listOf(IOException("offline"), CancellationException("cancelled"))) {
                f.notifications.getInboxHandler = { throw failure }
                val observed =
                    runCatching {
                        f.coordinator.prepareHistory(
                            f.account.id,
                        )
                    }.exceptionOrNull()
                assertSame(failure, observed)
            }
            assertTrue(f.notifications.inboxRequests.isEmpty())
            assertTrue(f.publisher.posts.isEmpty())
            assertTrue(f.handled.requests.isEmpty())
        }

    /** A supplied empty cutoff starts one observer per source without querying history again. */
    @Test fun suppliedEmptyCutoffDoesNotReadOrWriteAnEmptyHistory() =
        scenario { f ->
            f.start(this)
            runCurrent()
            assertEquals(listOf(f.account.id), f.notifications.inboxRequests)
            assertEquals(1, f.notifications.activeInboxObservers)
            assertEquals(1, f.accounts.activeObservers)
            assertTrue(f.notifications.inboxReadRequests.isEmpty())
            assertTrue(f.notifications.readRequests.isEmpty())
            assertTrue(f.handled.requests.isEmpty())
            assertTrue(f.publisher.posts.isEmpty())
        }

    /** Cached emissions cannot replace the cutoff or suppress a newly delivered receipt. */
    @Test fun historyRemainsExcludedWhileNewReceiptsCanPost() =
        scenario { f ->
            f.receipts(f.won.id)
            f.start(this, setOf(f.won.id))
            runCurrent()
            assertTrue(f.notifications.getRequests.isEmpty())
            f.receipts(f.won.id, f.lost.id)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.notifications.getRequests)
            assertEquals(listOf(f.lost.id), f.publisher.posts.map { it.eventId })
            assertEquals(setOf(f.lost.id), f.handled.values[f.account.id])
            assertTrue(f.notifications.inboxReadRequests.isEmpty())
        }

    /** Durable markers and repeated/read-state snapshots cannot repeat a completed event. */
    @Test fun handledAndRepeatedReceiptsDoNotReloadOrRepost() =
        scenario { f ->
            f.handled.values[f.account.id] = setOf(f.won.id)
            f.receipts(f.won.id, f.lost.id)
            f.start(this)
            runCurrent()
            f.notifications.receipts.value =
                mapOf(
                    f.account.id to
                        listOf(
                            InboxReceipt(f.won.id, CampingFixtures.now),
                            InboxReceipt(f.lost.id, CampingFixtures.now),
                        ),
                )
            runCurrent()
            assertEquals(listOf(f.lost.id), f.notifications.getRequests)
            assertEquals(
                listOf(
                    NotificationAlert(
                        f.account.id,
                        f.lost.id,
                        f.lost.subject,
                        NotificationText("Result", f.lost.subject.id),
                    ),
                ),
                f.publisher.posts,
            )
            assertEquals(listOf(f.account.id to f.lost.id), f.handled.requests)
        }

    /** Opt-out records suppression but leaves history/read state and later opt-in independent. */
    @Test fun optOutDoesNotReplaySuppressedEventsAfterOptIn() =
        scenario { f ->
            f.setEnabled(false)
            f.receipts(f.won.id)
            f.start(this)
            runCurrent()
            assertTrue(f.publisher.posts.isEmpty())
            assertEquals(setOf(f.won.id), f.handled.values[f.account.id])
            f.setEnabled(true)
            runCurrent()
            assertTrue(f.publisher.posts.isEmpty())
            f.receipts(f.won.id, f.lost.id)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.publisher.posts.map { it.eventId })
            assertEquals(
                listOf(
                    f.won.id,
                    f.lost.id,
                ),
                f.notifications.receipts.value[f.account.id]!!.map {
                    it.notificationId
                },
            )
            assertTrue(f.notifications.readRequests.isEmpty())
            assertTrue(f.accounts.settingsRequests.isEmpty())
        }

    /** Runtime, app-wide and canonical channel blocking each cause deliberate suppression. */
    @Test fun eachAndroidBlockSuppressesWithoutPosting() =
        scenario { f ->
            val blocked =
                listOf(
                    NotificationPermissionState(true, false, true, null),
                    NotificationPermissionState(true, true, false, null),
                    NotificationPermissionState(true, true, true, false),
                )
            val ids = listOf(f.won.id, f.lost.id, f.confirmed.id)
            f.start(this)
            for (index in blocked.indices) {
                f.permission.current = blocked[index]
                f.receipts(*ids.take(index + 1).toTypedArray())
                runCurrent()
                assertEquals(ids.take(index + 1).toSet(), f.handled.values[f.account.id])
                assertTrue(f.publisher.posts.isEmpty())
            }
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Unknown, foreign, removed or failed preference sources wait rather than guess opt-out. */
    @Test fun unknownPreferenceWaitsForAValidAccountAndSourceRecovery() =
        scenario { f ->
            f.accounts.account.value = null
            f.receipts(f.won.id)
            f.start(this)
            runCurrent()
            val removed = f.account.copy(removal = Removal(CampingFixtures.now, f.account.id))
            for (account in listOf(f.account.copy(id = "foreign"), removed, null)) {
                f.accounts.account.value = account
                runCurrent()
                assertTrue(f.publisher.posts.isEmpty())
                assertTrue(f.handled.requests.isEmpty())
            }
            f.accounts.streamFailure.value = IOException("account offline")
            runCurrent()
            f.accounts.account.value = f.account
            runCurrent()
            assertTrue(f.publisher.posts.isEmpty())
            f.accounts.streamFailure.value = null
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf(f.won.id), f.publisher.posts.map { it.eventId })
            assertTrue(f.accounts.settingsRequests.isEmpty())
            assertTrue(f.failures.any { it is IOException })
        }

    /** A preference change while context is loading is honored before the actual OS effect. */
    @Test fun latestOptOutWinsAfterHeldContentRead() =
        scenario { f ->
            val gate = CompletableDeferred<Unit>()
            f.lotteries.getHandler = {
                gate.await()
                f.lottery
            }
            f.receipts(f.won.id)
            f.start(this)
            try {
                runCurrent()
                assertEquals(listOf("get:${f.lottery.id}"), f.lotteries.events)
                assertTrue(f.publisher.posts.isEmpty())
                f.setEnabled(false)
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertTrue(f.publisher.posts.isEmpty())
                assertEquals(setOf(f.won.id), f.handled.values[f.account.id])
            } finally {
                gate.complete(Unit)
            }
        }

    /** Missing/failed event and failed subject reads retry without blocking a successful peer. */
    @Test fun contentFailuresRetryIndependently() =
        runTest {
            for (failureKind in 0..2) {
                val f = AlertFixture()
                when (failureKind) {
                    0 -> {
                        f.notifications.records.value = listOf(f.lost)
                    }

                    1 -> {
                        f.notifications.getHandler = { id ->
                            if (id == f.won.id) throw IOException("event offline")
                            f.notifications.records.value
                                .firstOrNull { it.id == id }
                        }
                    }

                    2 -> {
                        f.lotteries.getHandler = { id ->
                            if (id == f.lottery.id) throw IOException("subject offline")
                            f.otherLottery
                        }
                    }
                }
                f.receipts(f.won.id, f.lost.id)
                f.start(this)
                try {
                    runCurrent()
                    assertEquals(listOf(f.lost.id), f.publisher.posts.map { it.eventId })
                    assertEquals(setOf(f.lost.id), f.handled.values[f.account.id])
                    f.notifications.records.value = listOf(f.won, f.lost)
                    f.notifications.getHandler = null
                    f.lotteries.getHandler = null
                    advanceTimeBy(1000)
                    runCurrent()
                    assertEquals(listOf(f.lost.id, f.won.id), f.publisher.posts.map { it.eventId })
                    assertTrue(f.failures.isNotEmpty())
                } finally {
                    f.running?.cancelAndJoin()
                }
            }
        }

    /** Missing supported records keep true targets; Final message subjects do not invent taps. */
    @Test fun unavailableContextKeepsTargetAndMessageIsSuppressed() =
        scenario { f ->
            f.lotteries.records.value = emptyList()
            f.receipts(f.won.id)
            f.start(this)
            runCurrent()
            assertEquals(
                f.won.subject,
                f.publisher.posts
                    .single()
                    .subject,
            )
            assertEquals(NotificationSubjectContent.Unavailable, f.rendered.single().subject)
            val message =
                f.won.copy(
                    id = "message-event",
                    type = NotificationType.NEW_MESSAGE,
                    subject = NotificationSubject(SubjectType.MESSAGE, "message"),
                )
            f.notifications.records.value += message
            f.receipts(f.won.id, message.id)
            runCurrent()
            assertEquals(listOf(f.won.id), f.publisher.posts.map { it.eventId })
            assertEquals(listOf(f.won.id), f.rendered.map { it.notification.id })
            assertEquals(setOf(f.won.id, message.id), f.handled.values[f.account.id])
        }

    /** Failed posts remain unhandled and use capped backoff while unrelated events succeed. */
    @Test fun postFailureRetriesIndependentlyWithBoundedBackoff() =
        scenario { f ->
            f.publisher.failIds += f.won.id
            f.receipts(f.won.id, f.lost.id)
            f.start(this)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.publisher.posts.map { it.eventId })
            var attempts = 1
            for (delay in listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)) {
                assertEquals(setOf(f.lost.id), f.handled.values[f.account.id])
                advanceTimeBy(delay - 1)
                runCurrent()
                assertEquals(attempts, f.publisher.attempts.count { it.eventId == f.won.id })
                advanceTimeBy(1)
                runCurrent()
                attempts++
                assertEquals(attempts, f.publisher.attempts.count { it.eventId == f.won.id })
            }
            f.publisher.failIds.clear()
            advanceTimeBy(60000)
            runCurrent()
            assertEquals(listOf(f.lost.id, f.won.id), f.publisher.posts.map { it.eventId })
            assertEquals(setOf(f.won.id, f.lost.id), f.handled.values[f.account.id])
        }

    /** A failed marker retries only bookkeeping after either completed post or suppression. */
    @Test fun markerFailureDoesNotRepeatCompletedAction() =
        runTest {
            for (enabled in listOf(true, false)) {
                val f = AlertFixture()
                f.setEnabled(enabled)
                f.handled.markFailures = 1
                f.receipts(f.won.id)
                f.start(this)
                try {
                    runCurrent()
                    assertEquals(if (enabled) 1 else 0, f.publisher.posts.size)
                    assertEquals(1, f.handled.requests.size)
                    assertTrue(f.handled.values.isEmpty())
                    f.setEnabled(!enabled)
                    advanceTimeBy(1000)
                    runCurrent()
                    assertEquals(if (enabled) 1 else 0, f.publisher.posts.size)
                    assertEquals(2, f.handled.requests.size)
                    assertEquals(setOf(f.won.id), f.handled.values[f.account.id])
                } finally {
                    f.running?.cancelAndJoin()
                }
            }
        }

    /** Only eventual server success defines history after initialization failure. */
    @Test fun failedBaselineRetriesBeforeObservingAndUsesEventualCutoff() =
        scenario { f ->
            f.notifications.getInboxHandler = { throw IOException("baseline offline") }
            f.receipts(f.won.id)
            f.start(this, null)
            runCurrent()
            assertEquals(listOf(f.account.id), f.notifications.inboxReadRequests)
            assertTrue(f.notifications.inboxRequests.isEmpty())
            assertTrue(f.publisher.posts.isEmpty())
            f.notifications.getInboxHandler = { listOf(InboxReceipt(f.won.id, null)) }
            advanceTimeBy(999)
            runCurrent()
            assertEquals(1, f.notifications.inboxReadRequests.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, f.notifications.inboxReadRequests.size)
            assertEquals(1, f.notifications.activeInboxObservers)
            assertTrue(f.publisher.posts.isEmpty())
            f.receipts(f.won.id, f.lost.id)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.publisher.posts.map { it.eventId })
        }

    /** Resubscribing a failed live source retains the original cutoff and handled exclusions. */
    @Test fun inboxFailureResubscribesWithoutRebaselining() =
        scenario { f ->
            f.receipts(f.won.id)
            f.start(this)
            runCurrent()
            assertEquals(1, f.publisher.posts.size)
            f.notifications.inboxFailure.value = IOException("Inbox offline")
            runCurrent()
            assertEquals(0, f.notifications.activeInboxObservers)
            f.receipts(f.won.id, f.lost.id)
            f.notifications.inboxFailure.value = null
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf(f.won.id, f.lost.id), f.publisher.posts.map { it.eventId })
            assertEquals(2, f.notifications.inboxRequests.size)
            assertTrue(f.notifications.inboxReadRequests.isEmpty())
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Removing a pending receipt retires its noncooperative load before post or bookkeeping. */
    @Test fun removedReceiptCannotPublishLateContent() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            f.receipts(f.won.id)
            f.start(this)
            try {
                runCurrent()
                assertEquals(listOf(f.won.id), f.notifications.getRequests)
                f.receipts()
                runCurrent()
                gate.complete(f.won)
                runCurrent()
                assertTrue(f.publisher.posts.isEmpty())
                assertTrue(f.handled.requests.isEmpty())
                assertTrue(f.lotteries.events.isEmpty())
            } finally {
                gate.complete(f.won)
            }
        }

    /** A late cancelled baseline cannot establish a cutoff or create account/Inbox listeners. */
    @Test fun cancelledNoncooperativeBaselineCannotStartObservation() =
        scenario { f ->
            val gate = CompletableDeferred<List<InboxReceipt>>()
            f.notifications.getInboxHandler = { withContext(NonCancellable) { gate.await() } }
            val job = f.start(this, null)
            try {
                runCurrent()
                assertEquals(listOf(f.account.id), f.notifications.inboxReadRequests)
                job.cancel()
                gate.complete(emptyList())
                runCurrent()
                job.join()
                assertTrue(job.isCancelled)
                assertEquals(0, f.accounts.observations)
                assertTrue(f.notifications.inboxRequests.isEmpty())
                assertTrue(f.publisher.posts.isEmpty())
                assertTrue(f.handled.requests.isEmpty())
                assertTrue(f.failures.isEmpty())
            } finally {
                gate.complete(emptyList())
            }
        }

    /** Session cancellation retires both sources and rejects a noncooperative subject result. */
    @Test fun cancelledSessionCannotPublishLateContext() =
        scenario { f ->
            val gate = CompletableDeferred<Unit>()
            f.lotteries.getHandler = {
                withContext(NonCancellable) { gate.await() }
                f.lottery
            }
            f.receipts(f.won.id)
            val job = f.start(this)
            try {
                runCurrent()
                assertEquals(1, f.accounts.activeObservers)
                assertEquals(1, f.notifications.activeInboxObservers)
                job.cancel()
                gate.complete(Unit)
                runCurrent()
                job.join()
                assertEquals(0, f.accounts.activeObservers)
                assertEquals(0, f.notifications.activeInboxObservers)
                assertTrue(f.publisher.posts.isEmpty())
                assertTrue(f.handled.requests.isEmpty())
                assertTrue(f.failures.none { it is CancellationException })
            } finally {
                gate.complete(Unit)
            }
        }

    /** Keeps scenario jobs inside the test's virtual-time lifetime and closes them before drain.
     * @param block Fixed scenario using one independent account/receipt/effect fixture.
     */
    private fun scenario(block: suspend TestScope.(AlertFixture) -> Unit) =
        runTest {
            val fixture = AlertFixture()
            try {
                block(fixture)
            } finally {
                fixture.running?.cancelAndJoin()
            }
        }
}

/** Fixed facts and independently controlled effects, without implementing production copy. */
private class AlertFixture {
    val account = CampingFixtures.account()
    val lottery = CampingFixtures.lottery()
    val otherLottery = CampingFixtures.lottery("other-draw")
    val won =
        Notification(
            "won",
            null,
            NotificationType.LOTTERY_WON,
            NotificationSubject(SubjectType.LOTTERY, lottery.id),
            null,
            CampingFixtures.now,
        )
    val lost =
        won.copy(
            id = "lost",
            type = NotificationType.LOTTERY_LOST,
            subject = NotificationSubject(SubjectType.LOTTERY, otherLottery.id),
        )
    val confirmed =
        won.copy(
            id = "confirmed",
            type = NotificationType.RESERVATION_CONFIRMED,
            subject = NotificationSubject(SubjectType.RESERVATION, "reservation"),
        )
    val accounts = FakeAccountRepository(account)
    val notifications = FakeNotificationRepository(listOf(won, lost, confirmed))
    val lotteries = FakeLotteryRepository(listOf(lottery, otherLottery))
    val bookings = FakeBookingRepository(listOf(CampingFixtures.reservation()))
    val handled = RecordingHandledEvents()
    val publisher = RecordingNotificationPublisher()
    val permission = RecordingNotificationPermission()
    val failures = mutableListOf<Exception>()
    val rendered = mutableListOf<NotificationContent.Loaded>()
    var running: Job? = null
    val coordinator =
        NotificationAlertCoordinator(
            notifications,
            accounts,
            NotificationContentLoader(notifications, lotteries, bookings),
            permission,
            handled,
            publisher,
        ) { loaded ->
            rendered += loaded
            NotificationText("Result", loaded.notification.subject.id)
        }

    /** Starts exactly one session with the scenario's confirmed or unknown cutoff.
     * @param scope Virtual-time owner that retires the coroutine before test drain.
     * @param initialIds Confirmed history identities; null requests retryable initialization.
     */
    fun start(
        scope: TestScope,
        initialIds: Set<String>? = emptySet(),
    ): Job {
        check(running == null)
        return scope.backgroundScope
            .launch {
                coordinator.run(WorkSession(account.id, account.roles), initialIds, failures::add)
            }.also { running = it }
    }

    /** Supplies the complete current account receipt list without modifying shared events.
     * @param ids Actual event identities in this scenario's current Inbox.
     */
    fun receipts(vararg ids: String) {
        notifications.receipts.value = mapOf(account.id to ids.map { InboxReceipt(it, null) })
    }

    /** Changes only observed saved preference; no simulated repository mutation is invoked.
     * @param enabled Current confirmed application opt-in value.
     */
    fun setEnabled(enabled: Boolean) {
        accounts.account.value =
            account.copy(settings = account.settings.copy(notificationsEnabled = enabled))
    }
}

/** Explicit handled-marker fixture with independently failed persistence acknowledgements. */
private class RecordingHandledEvents : AlertDeliveryStore {
    val values = mutableMapOf<String, Set<String>>()
    val requests = mutableListOf<Pair<String, String>>()
    var markFailures = 0

    /** Returns an account-specific immutable fixture snapshot.
     * @param accountId Current session account.
     */
    override suspend fun handledIds(accountId: String): Set<String> =
        values[accountId].orEmpty().toSet()

    /** Captures the exact marker request and only persists after a configured successful outcome.
     * @param accountId Current session account.
     * @param eventId Event whose post/suppression already completed.
     */
    override suspend fun markHandled(
        accountId: String,
        eventId: String,
    ) {
        requests += accountId to eventId
        if (markFailures > 0) {
            markFailures--
            throw IOException("marker offline")
        }
        values[accountId] = values[accountId].orEmpty() + eventId
    }
}

/** Records submitted effects and can reject selected events independently. */
private class RecordingNotificationPublisher : NotificationPublisher {
    val attempts = mutableListOf<NotificationAlert>()
    val posts = mutableListOf<NotificationAlert>()
    val failIds = mutableSetOf<String>()

    /** Fails only selected fixture events without changing another event's successful effect.
     * @param alert Exact already resolved platform request.
     */
    override fun post(alert: NotificationAlert) {
        attempts += alert
        if (alert.eventId in failIds) throw IOException("post offline")
        posts += alert
    }
}

/** Current permission facts only; no OS settings launch is part of this controller test. */
private class RecordingNotificationPermission : NotificationPermission {
    var current = NotificationPermissionState(true, true, true, null)

    /** Returns the current controlled runtime/app/channel facts. */
    override fun status(): NotificationPermissionState =
        current

    /** Rejects an unexpected launcher request from a non-UI coordinator. */
    override fun settingsIntent(): Intent =
        error("Coordinator must not launch settings")
}

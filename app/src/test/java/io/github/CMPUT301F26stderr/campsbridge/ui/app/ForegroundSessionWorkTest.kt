package io.github.CMPUT301F26stderr.campsbridge.ui.app

import android.content.Intent
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.lifecycle.ForegroundWorkRunner
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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Six fixed real-coordinator compositions; no fake cutoff implementation replaces the caller. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundSessionWorkTest {
    /** Initial history must finish before one underlying lane can create eligible new receipts. */
    @Test fun heldSuccessfulBaselinePrecedesUnderlyingDelivery() =
        scenario { f ->
            val gate = CompletableDeferred<List<InboxReceipt>>()
            f.notifications.getInboxHandler = { gate.await() }
            f.pending.onStart = { f.receipts(f.won.id) }
            f.start(this)
            try {
                runCurrent()
                assertEquals(listOf(f.session.accountId), f.notifications.inboxReadRequests)
                assertTrue(f.pending.started.isEmpty())
                gate.complete(emptyList())
                runCurrent()
                assertEquals(listOf(f.session), f.pending.started)
                assertEquals(listOf(f.won.id), f.posts.map { it.eventId })
                assertEquals(1, f.pending.maximumActive)
                assertTrue(f.notifications.readRequests.isEmpty())
                assertTrue(f.accounts.settingsRequests.isEmpty())
            } finally {
                gate.complete(emptyList())
            }
        }

    /** Failed initialization cannot hold due work; later successful history supplies the cutoff. */
    @Test fun failedBaselineLeavesDueWorkRunningAndLaterHistoryExcluded() =
        scenario { f ->
            var offline = true
            f.notifications.getInboxHandler = {
                if (offline) throw IOException("baseline offline")
                listOf(InboxReceipt(f.won.id, null))
            }
            f.pending.onStart = { f.receipts(f.won.id) }
            f.start(this)
            runCurrent()
            assertEquals(listOf(f.session), f.pending.started)
            assertEquals(listOf(f.session), f.pending.active)
            assertTrue(f.failures.any { it is IOException })
            assertEquals(0, f.notifications.activeInboxObservers)
            assertTrue(f.posts.isEmpty())
            offline = false
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(1, f.notifications.activeInboxObservers)
            assertTrue(f.posts.isEmpty())
            f.receipts(f.won.id, f.lost.id)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.posts.map { it.eventId })
            assertEquals(1, f.pending.started.size)
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Bounded baseline retries do not replace or stop the concurrently running durable owner. */
    @Test fun repeatedInitializationFailureHasBackoffWithoutStoppingDueWork() =
        scenario { f ->
            f.notifications.getInboxHandler = { throw IOException("offline") }
            f.start(this)
            runCurrent()
            assertEquals(2, f.notifications.inboxReadRequests.size)
            var attempts = 2
            for (delay in listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)) {
                advanceTimeBy(delay - 1)
                runCurrent()
                assertEquals(attempts, f.notifications.inboxReadRequests.size)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(++attempts, f.notifications.inboxReadRequests.size)
                assertEquals(listOf(f.session), f.pending.active)
                assertEquals(1, f.pending.started.size)
            }
            assertTrue(f.notifications.inboxRequests.isEmpty())
            assertTrue(f.posts.isEmpty())
        }

    /** The actual lifecycle runner retains equal sessions and retires both lanes on replacement. */
    @Test fun lifecycleAccountAndCapabilityChangesKeepExactlyOneOwner() =
        scenario { f ->
            val session = MutableStateFlow<WorkSession?>(f.session)
            val foreground = MutableStateFlow(true)
            f.running =
                backgroundScope.launch {
                    ForegroundWorkRunner(
                        f.wrapper,
                        f.failures::add,
                    ).run(session, foreground)
                }
            runCurrent()
            assertEquals(listOf(f.session), f.pending.active)
            session.value = f.session.copy()
            runCurrent()
            assertEquals(1, f.pending.started.size)
            foreground.value = false
            runCurrent()
            assertTrue(f.pending.active.isEmpty())
            assertEquals(0, f.accounts.activeObservers)
            assertEquals(0, f.notifications.activeInboxObservers)
            foreground.value = true
            runCurrent()
            val owner = f.session.copy(roles = setOf(Role.ENTRANT, Role.OWNER))
            session.value = owner
            runCurrent()
            assertEquals(listOf(owner), f.pending.active)
            val other = WorkSession("other", setOf(Role.ENTRANT))
            f.accounts.account.value = CampingFixtures.account(other.accountId)
            session.value = other
            runCurrent()
            assertEquals(listOf(other), f.pending.active)
            assertEquals(1, f.pending.maximumActive)
            assertEquals(1, f.accounts.activeObservers)
            assertEquals(1, f.notifications.activeInboxObservers)
            session.value = null
            runCurrent()
            assertTrue(f.pending.active.isEmpty())
            assertEquals(0, f.accounts.activeObservers)
            assertEquals(0, f.notifications.activeInboxObservers)
            assertTrue(f.failures.none { it is CancellationException })
            assertTrue(f.posts.isEmpty())
        }

    /** Cancelling during a noncooperative initial read prevents all later lane startup. */
    @Test fun cancelledLateInitialBaselineCannotStartEitherLane() =
        scenario { f ->
            val gate = CompletableDeferred<List<InboxReceipt>>()
            f.notifications.getInboxHandler = { withContext(NonCancellable) { gate.await() } }
            val job = f.start(this)
            try {
                runCurrent()
                assertEquals(listOf(f.session.accountId), f.notifications.inboxReadRequests)
                job.cancel()
                gate.complete(emptyList())
                runCurrent()
                job.join()
                assertTrue(f.pending.started.isEmpty())
                assertTrue(f.notifications.inboxRequests.isEmpty())
                assertEquals(0, f.accounts.observations)
                assertTrue(f.posts.isEmpty())
                assertTrue(f.failures.isEmpty())
            } finally {
                gate.complete(emptyList())
            }
        }

    /** Expected per-event post failures cannot kill durable work or an unrelated eligible alert. */
    @Test fun eventFailureKeepsDueWorkAndUnrelatedAlertsAlive() =
        scenario { f ->
            f.failedPost = f.won.id
            f.pending.onStart = { f.receipts(f.won.id, f.lost.id) }
            f.start(this)
            runCurrent()
            assertEquals(listOf(f.lost.id), f.posts.map { it.eventId })
            assertEquals(listOf(f.session), f.pending.active)
            assertTrue(f.failures.any { it is IOException })
            f.failedPost = null
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf(f.lost.id, f.won.id), f.posts.map { it.eventId })
            assertEquals(1, f.pending.started.size)
            assertTrue(f.notifications.readRequests.isEmpty())
            assertTrue(f.accounts.settingsRequests.isEmpty())
        }

    /** Closes session work and proves both source lifetimes end before virtual-time draining.
     * @param block One fixed composition scenario.
     */
    private fun scenario(block: suspend TestScope.(ForegroundFixture) -> Unit) =
        runTest {
            val f = ForegroundFixture()
            try {
                block(f)
            } finally {
                f.running?.cancelAndJoin()
            }
            assertTrue(f.pending.active.isEmpty())
            assertEquals(0, f.notifications.activeInboxObservers)
            assertEquals(0, f.accounts.activeObservers)
        }
}

/** Shared controlled dependencies around the real cutoff/content/coordinator implementations. */
private class ForegroundFixture {
    val session = WorkSession("entrant", setOf(Role.ENTRANT))
    val accounts = FakeAccountRepository(CampingFixtures.account())
    val won =
        Notification(
            "won",
            null,
            NotificationType.LOTTERY_WON,
            NotificationSubject(SubjectType.LOTTERY, "draw"),
            null,
            CampingFixtures.now,
        )
    val lost = won.copy(id = "lost", type = NotificationType.LOTTERY_LOST)
    val notifications =
        FakeNotificationRepository(listOf(won, lost)).apply {
            getInboxHandler =
                { emptyList() }
        }
    val pending = UnderlyingSessionWork()
    val posts = mutableListOf<NotificationAlert>()
    var failedPost: String? = null
    val failures = mutableListOf<Exception>()
    var running: Job? = null
    val markers = mutableMapOf<String, Set<String>>()
    val coordinator =
        NotificationAlertCoordinator(
            notifications,
            accounts,
            NotificationContentLoader(
                notifications,
                FakeLotteryRepository(listOf(CampingFixtures.lottery())),
                FakeBookingRepository(),
            ),
            object : NotificationPermission {
                /** Reports explicitly allowed device facts for this composition fixture. */
                override fun status() =
                    NotificationPermissionState(true, true, true, null)

                /** Rejects any unexpected UI launcher invocation from foreground work. */
                override fun settingsIntent(): Intent =
                    error("No settings launch")
            },
            object : AlertDeliveryStore {
                /** Returns only this fixture account's independently persisted marker IDs.
                 * @param accountId Current session account.
                 */
                override suspend fun handledIds(accountId: String): Set<String> =
                    markers[accountId].orEmpty()

                /** Adds one marker without modelling any OS behavior.
                 * @param accountId Current session account.
                 * @param eventId Successfully posted/suppressed event.
                 */
                override suspend fun markHandled(
                    accountId: String,
                    eventId: String,
                ) {
                    markers[accountId] = markers[accountId].orEmpty() + eventId
                }
            },
            object : NotificationPublisher {
                /** Captures permitted submissions; one selected event can independently fail.
                 * @param alert Resolved immutable event request.
                 */
                override fun post(alert: NotificationAlert) {
                    if (alert.eventId == failedPost) throw IOException("post failed")
                    posts += alert
                }
            },
        ) { NotificationText("Result", it.notification.subject.id) }
    val wrapper = ForegroundSessionWork(pending, coordinator)

    /** Starts one wrapper under the test's cancellable session lifetime.
     * @param scope Coroutine test owner.
     */
    fun start(scope: TestScope): Job =
        scope.backgroundScope
            .launch {
                wrapper.run(session, failures::add)
            }.also { running = it }

    /** Supplies an observed complete receipt set without marking anything read.
     * @param ids Actual fixture event IDs.
     */
    fun receipts(vararg ids: String) {
        notifications.receipts.value =
            mapOf(session.accountId to ids.map { InboxReceipt(it, null) })
    }
}

/** Captures exact underlying ownership and performs an optional due-delivery fixture action. */
private class UnderlyingSessionWork : PendingWork {
    val started = mutableListOf<WorkSession>()
    val active = mutableListOf<WorkSession>()
    var maximumActive = 0
    var onStart: () -> Unit = {}

    /** Remains alive until the real wrapper/foreground runner cancels its session.
     * @param session Exact ready account and all current capabilities.
     * @param onFailure Unused because this supplied due-work action succeeds.
     */
    override suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    ) {
        started += session
        active += session
        maximumActive = maxOf(maximumActive, active.size)
        try {
            onStart()
            awaitCancellation()
        } finally {
            active -= session
        }
    }
}

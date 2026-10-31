package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Eighteen fixed root-tap contracts with real shared content loading and primitive restoration. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationEntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Normal root/session changes without a tap do not perform notification/context IO. */
    @Test fun noEntryMeansNoContextReads() =
        scenario { f ->
            f.ready()
            f.model.updateSession(SessionState.Initializing)
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.None, f.model.state.value)
            assertTrue(f.notifications.getRequests.isEmpty())
            assertTrue(f.lotteries.events.isEmpty())
            assertTrue(f.bookings.getRequests.isEmpty())
            assertTrue(f.saved.keys().isEmpty())
        }

    /** Initialization/setup/failure retain one envelope until the real matching Ready arrives. */
    @Test fun queuedTapWaitsThroughBootstrapAndSetup() =
        scenario { f ->
            f.model.accept(f.entry)
            for (session in listOf(
                SessionState.Initializing,
                SessionState.SetupRequired("entrant"),
                SessionState.Failure,
            )) {
                f.model.updateSession(session)
                runCurrent()
                assertEquals(NotificationEntryUiState.Waiting(f.entry), f.model.state.value)
                assertTrue(f.notifications.getRequests.isEmpty())
            }
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
            assertEquals(listOf(f.won.id), f.notifications.getRequests)
            assertTrue(f.notifications.readRequests.isEmpty())
        }

    /** Repeated tap and workspace-only root updates keep one held event read. */
    @Test fun duplicatePendingTapAndWorkspaceChangesDoNotRestartRead() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { gate.await() }
            f.ready(roles = setOf(Role.ENTRANT, Role.OWNER))
            f.model.accept(f.entry)
            try {
                runCurrent()
                assertEquals(NotificationEntryUiState.Loading(f.entry), f.model.state.value)
                f.model.accept(f.entry.copy())
                f.ready(Role.OWNER, setOf(Role.ENTRANT, Role.OWNER))
                runCurrent()
                assertEquals(listOf(f.won.id), f.notifications.getRequests)
                gate.complete(f.won)
                runCurrent()
                assertEquals(
                    NotificationEntryUiState.Ready(f.entry, Role.ENTRANT),
                    f.model.state.value,
                )
            } finally {
                gate.complete(f.won)
            }
        }

    /** Actual lottery ownership and available roles choose owner versus entrant presentation. */
    @Test fun lotteryPresentationUsesSavedOwnershipAndCapabilities() =
        runTest {
            for ((owner, roles, expected) in listOf(
                Triple("entrant", setOf(Role.ENTRANT, Role.OWNER), Role.OWNER),
                Triple("owner", setOf(Role.ENTRANT, Role.OWNER), Role.ENTRANT),
                Triple("owner", setOf(Role.ADMIN), null),
            )) {
                val f = EntryFixture()
                try {
                    f.lotteries.records.value = listOf(f.lottery.copy(ownerId = owner))
                    f.ready(if (Role.ENTRANT in roles) Role.ENTRANT else Role.ADMIN, roles)
                    f.model.accept(f.entry)
                    runCurrent()
                    assertEquals(
                        if (expected ==
                            null
                        ) {
                            NotificationEntryUiState.Unavailable(f.entry)
                        } else {
                            NotificationEntryUiState.Ready(f.entry, expected)
                        },
                        f.model.state.value,
                    )
                } finally {
                    f.store.clear()
                }
            }
        }

    /** Saved self stays use entrant context; other readable stays use owner context. */
    @Test fun reservationPresentationUsesSavedEntrantRelationship() =
        runTest {
            for ((entrant, workspace, expected) in listOf(
                Triple("entrant", Role.OWNER, Role.ENTRANT),
                Triple("other", Role.ENTRANT, Role.OWNER),
            )) {
                val f = EntryFixture()
                try {
                    val reservation = CampingFixtures.reservation().copy(entrantId = entrant)
                    val event =
                        f.won.copy(
                            type = NotificationType.RESERVATION_CONFIRMED,
                            subject = NotificationSubject(SubjectType.RESERVATION, reservation.id),
                        )
                    val entry = f.entry.copy(subject = event.subject)
                    f.notifications.records.value = listOf(event)
                    f.bookings.records.value = listOf(reservation)
                    f.ready(workspace, setOf(Role.ENTRANT, Role.OWNER))
                    f.model.accept(entry)
                    runCurrent()
                    assertEquals(
                        NotificationEntryUiState.Ready(entry, expected),
                        f.model.state.value,
                    )
                    assertTrue(f.bookings.reserveRequests.isEmpty())
                } finally {
                    f.store.clear()
                }
            }
        }

    /** Missing, transport-failed and mismatched saved facts retain their honest recovery state. */
    @Test fun readOutcomesRemainExplicitAndVerifyEnvelopeSubject() =
        runTest {
            for (kind in 0..4) {
                val f = EntryFixture()
                try {
                    when (kind) {
                        0 -> {
                            f.notifications.records.value = emptyList()
                        }

                        1 -> {
                            f.notifications.getHandler = { throw IOException("event offline") }
                        }

                        2 -> {
                            f.lotteries.records.value = emptyList()
                        }

                        3 -> {
                            f.lotteries.getHandler = { throw IOException("context offline") }
                        }

                        4 -> {
                            f.notifications.records.value =
                                listOf(f.won.copy(subject = f.lost.subject))
                        }
                    }
                    f.ready()
                    f.model.accept(f.entry)
                    runCurrent()
                    val expected =
                        if (kind == 1 ||
                            kind == 3
                        ) {
                            NotificationEntryUiState.Failure(f.entry)
                        } else {
                            NotificationEntryUiState.Unavailable(f.entry)
                        }
                    assertEquals(expected, f.model.state.value)
                    assertTrue(f.notifications.readRequests.isEmpty())
                } finally {
                    f.store.clear()
                }
            }
        }

    /** Retry is explicit, held calls coalesce, and success resolves that exact same entry. */
    @Test fun retryIsExplicitAndGuardedForSameEntry() =
        scenario { f ->
            f.notifications.getHandler = { throw IOException("offline") }
            f.ready()
            f.model.accept(f.entry)
            runCurrent()
            assertEquals(NotificationEntryUiState.Failure(f.entry), f.model.state.value)
            f.ready()
            f.model.accept(f.entry)
            runCurrent()
            assertEquals(1, f.notifications.getRequests.size)
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { gate.await() }
            try {
                f.model.retry()
                f.model.retry()
                runCurrent()
                assertEquals(2, f.notifications.getRequests.size)
                assertEquals(NotificationEntryUiState.Loading(f.entry), f.model.state.value)
                gate.complete(f.won)
                runCurrent()
                assertEquals(
                    NotificationEntryUiState.Ready(f.entry, Role.ENTRANT),
                    f.model.state.value,
                )
            } finally {
                gate.complete(f.won)
            }
        }

    /** Another ready account cannot cause reads, identity switching or loss of the queued tap. */
    @Test fun wrongAccountWaitsWithoutReadingThenMatchingSessionResolves() =
        scenario { f ->
            f.ready(id = "other")
            f.model.accept(f.entry)
            runCurrent()
            assertEquals(NotificationEntryUiState.WrongAccount(f.entry), f.model.state.value)
            f.model.retry()
            runCurrent()
            assertTrue(f.notifications.getRequests.isEmpty())
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
        }

    /** Saved pending state contains primitive identity only and reloads facts after recreation. */
    @Test fun primitivePendingEntryRestoresBeforeFreshSessionAndRead() =
        scenario { f ->
            f.model.accept(f.entry)
            val snapshot = f.snapshot()
            assertEquals(
                setOf(
                    f.entry.accountId,
                    f.entry.eventId,
                    f.entry.subject.type.name,
                    f.entry.subject.id,
                ),
                snapshot.values.toSet(),
            )
            assertTrue(snapshot.values.all { it is String })
            f.recreate(snapshot)
            assertEquals(NotificationEntryUiState.Waiting(f.entry), f.model.state.value)
            assertTrue(f.notifications.getRequests.isEmpty())
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
        }

    /** Only the exact ready entry is consumed; acknowledged state cannot restore a route. */
    @Test fun exactReadyConsumptionClearsPendingAndSurvivesRestoration() =
        scenario { f ->
            f.model.accept(f.entry)
            f.model.consume(f.entry)
            assertEquals(NotificationEntryUiState.Waiting(f.entry), f.model.state.value)
            f.ready()
            runCurrent()
            f.model.consume(f.entry.copy(eventId = "other"))
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
            f.model.consume(f.entry)
            assertEquals(NotificationEntryUiState.None, f.model.state.value)
            val snapshot = f.snapshot()
            assertEquals(setOf(f.entry.accountId, f.entry.eventId), snapshot.values.toSet())
            f.recreate(snapshot)
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.None, f.model.state.value)
            assertEquals(1, f.notifications.getRequests.size)
        }

    /** Consecutive duplicate taps do not reopen; a different event remains eligible. */
    @Test fun consumedDuplicateIsIgnoredWhileDifferentTapCanOpen() =
        scenario { f ->
            f.ready()
            f.model.accept(f.entry)
            runCurrent()
            f.model.consume(f.entry)
            f.model.accept(f.entry.copy())
            runCurrent()
            assertEquals(NotificationEntryUiState.None, f.model.state.value)
            assertEquals(1, f.notifications.getRequests.size)
            f.model.accept(f.otherEntry)
            runCurrent()
            assertEquals(
                NotificationEntryUiState.Ready(f.otherEntry, Role.ENTRANT),
                f.model.state.value,
            )
        }

    /** Newer input retires old reads so late data cannot replace the current target. */
    @Test fun newestTapRejectsTheOldNoncooperativeResult() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler =
                { id ->
                    if (id ==
                        f.won.id
                    ) {
                        withContext(NonCancellable) { gate.await() }
                    } else {
                        f.lost
                    }
                }
            f.ready()
            f.model.accept(f.entry)
            try {
                runCurrent()
                f.model.accept(f.otherEntry)
                runCurrent()
                assertEquals(
                    NotificationEntryUiState.Ready(f.otherEntry, Role.ENTRANT),
                    f.model.state.value,
                )
                gate.complete(f.won)
                runCurrent()
                assertEquals(
                    NotificationEntryUiState.Ready(f.otherEntry, Role.ENTRANT),
                    f.model.state.value,
                )
                assertEquals(listOf("get:${f.otherLottery.id}"), f.lotteries.events)
            } finally {
                gate.complete(f.won)
            }
        }

    /** Losing Ready cancels resolution; a matching restored session re-reads once. */
    @Test fun leavingReadyRetiresReadAndMatchingReturnCanRestart() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            f.ready()
            f.model.accept(f.entry)
            try {
                runCurrent()
                f.model.updateSession(SessionState.Failure)
                gate.complete(f.won)
                runCurrent()
                assertEquals(NotificationEntryUiState.Waiting(f.entry), f.model.state.value)
                f.notifications.getHandler = null
                f.ready()
                f.ready()
                runCurrent()
                assertEquals(2, f.notifications.getRequests.size)
                assertEquals(
                    NotificationEntryUiState.Ready(f.entry, Role.ENTRANT),
                    f.model.state.value,
                )
            } finally {
                gate.complete(f.won)
            }
        }

    /** Capability changes cannot leave a previously allowed navigation target actionable. */
    @Test fun capabilityChangeRevalidatesThePendingTarget() =
        scenario { f ->
            f.ready()
            f.model.accept(f.entry)
            runCurrent()
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
            f.ready(Role.ADMIN, setOf(Role.ADMIN))
            runCurrent()
            assertEquals(NotificationEntryUiState.Unavailable(f.entry), f.model.state.value)
            f.model.consume(f.entry)
            assertEquals(NotificationEntryUiState.Unavailable(f.entry), f.model.state.value)
        }

    /** Dismissal clears pending primitives and rejects late results across restoration. */
    @Test fun dismissedReadCannotReappearLateOrAfterRestore() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            f.ready()
            f.model.accept(f.entry)
            try {
                runCurrent()
                f.model.dismiss()
                gate.complete(f.won)
                runCurrent()
                assertEquals(NotificationEntryUiState.None, f.model.state.value)
                f.recreate(f.snapshot())
                f.ready()
                f.model.accept(f.entry)
                runCurrent()
                assertEquals(NotificationEntryUiState.None, f.model.state.value)
                assertEquals(1, f.notifications.getRequests.size)
            } finally {
                gate.complete(f.won)
            }
        }

    /** Clearing the root store invalidates both the held completion and stale public callbacks. */
    @Test fun clearedRootRejectsLateCompletionAndCallbacks() =
        scenario { f ->
            val gate = CompletableDeferred<Notification>()
            f.notifications.getHandler = { withContext(NonCancellable) { gate.await() } }
            f.ready()
            f.model.accept(f.entry)
            try {
                runCurrent()
                val before = f.model.state.value
                f.store.clear()
                gate.complete(f.won)
                runCurrent()
                f.model.accept(f.otherEntry)
                f.ready()
                f.model.retry()
                assertEquals(before, f.model.state.value)
                assertFalse(f.model.state.value is NotificationEntryUiState.Failure)
                assertTrue(f.lotteries.events.isEmpty())
            } finally {
                gate.complete(f.won)
            }
        }

    /** Corrupt primitive restoration never guesses an identity, target type or successful read. */
    @Test fun malformedSavedPendingStateIsDiscardedWithoutReading() =
        runTest {
            val valid =
                mapOf<String, Any?>(
                    "notificationEntryAccount" to "entrant",
                    "notificationEntryEvent" to "won",
                    "notificationEntrySubjectType" to "LOTTERY",
                    "notificationEntrySubjectId" to "draw",
                )
            for (bad in listOf(
                valid - "notificationEntryEvent",
                valid + ("notificationEntryAccount" to 42),
                valid + ("notificationEntrySubjectType" to "MESSAGE"),
                valid + ("notificationEntrySubjectType" to "UNKNOWN"),
                valid + ("notificationEntrySubjectId" to " "),
            )) {
                val f = EntryFixture(SavedStateHandle(bad))
                try {
                    f.ready()
                    runCurrent()
                    assertEquals(NotificationEntryUiState.None, f.model.state.value)
                    assertTrue(f.notifications.getRequests.isEmpty())
                    assertTrue(f.saved.keys().isEmpty())
                } finally {
                    f.store.clear()
                }
            }
        }

    /** Source cancellation is not failure feedback; a later session transition can retry. */
    @Test fun sourceCancellationNeverBecomesFailureAndLaterReadyCanRetry() =
        scenario { f ->
            f.notifications.getHandler = { throw CancellationException("source retired") }
            f.ready()
            f.model.accept(f.entry)
            runCurrent()
            assertEquals(NotificationEntryUiState.Waiting(f.entry), f.model.state.value)
            f.notifications.getHandler = null
            f.model.updateSession(SessionState.Initializing)
            f.ready()
            runCurrent()
            assertEquals(NotificationEntryUiState.Ready(f.entry, Role.ENTRANT), f.model.state.value)
            assertEquals(2, f.notifications.getRequests.size)
        }

    /** Releases each root owner before coroutine test draining.
     * @param block One fixed root-tap scenario with controlled saved source outcomes.
     */
    private fun scenario(block: suspend TestScope.(EntryFixture) -> Unit) =
        runTest {
            val fixture = EntryFixture()
            try {
                block(fixture)
            } finally {
                fixture.store.clear()
            }
        }
}

/** Matching ready identity, actual immutable sources and independently reconstructed saved state.
 * @param saved Initial primitive values, including deliberately malformed restoration fixtures.
 */
private class EntryFixture(
    var saved: SavedStateHandle = SavedStateHandle(),
) {
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
    val entry = NotificationEntry("entrant", won.id, won.subject)
    val otherEntry = entry.copy(eventId = lost.id, subject = lost.subject)
    val notifications = FakeNotificationRepository(listOf(won, lost))
    val lotteries = FakeLotteryRepository(listOf(lottery, otherLottery))
    val bookings = FakeBookingRepository(listOf(CampingFixtures.reservation()))
    val store = ViewModelStore()
    var model = createModel()

    /** Supplies real root facts without another profile observer or workspace persistence write.
     * @param workspace Currently selected workspace, which does not determine target context.
     * @param roles Actual granted capabilities for this fixture's current account.
     * @param id Ready identity; foreign values must prevent event reads.
     */
    fun ready(
        workspace: Role = Role.ENTRANT,
        roles: Set<Role> = setOf(Role.ENTRANT),
        id: String = "entrant",
    ) {
        model.updateSession(
            SessionState.Ready(CampingFixtures.account(id).copy(roles = roles), workspace),
        )
    }

    /** Reconstructs only primitive state after releasing the old root owner.
     * @param values Snapshot restored by the Android saved-state owner.
     */
    fun recreate(values: Map<String, Any?>) {
        store.clear()
        saved = SavedStateHandle(values)
        model = createModel()
    }

    /** Exposes only what would be serialized by the real root saved-state owner. */
    fun snapshot(): Map<String, Any?> =
        saved.keys().associateWith { saved.get<Any?>(it) }

    /** Supplies real loader behavior and places the root model in its owned lifetime. */
    private fun createModel(): NotificationEntryViewModel =
        NotificationEntryViewModel(
            NotificationContentLoader(notifications, lotteries, bookings),
            saved,
        ).also {
            store.put("entry", it)
        }
}

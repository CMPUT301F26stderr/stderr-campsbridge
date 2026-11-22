package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** AN-01 through AN-03 establish local composition, complete attempts and explicit recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementComposeTest {
    @get:Rule val main = MainDispatcherRule()
    private val savedLottery =
        CampingFixtures.lottery().copy(
            publishedAt = CampingFixtures.now,
            participantOwnerIds = setOf("owner", "co-owner"),
        )
    private val lotteries =
        FakeLotteryRepository(listOf(savedLottery)).apply {
            entries.value =
                listOf(
                    LotteryEntry(
                        savedLottery.id,
                        "entrant",
                        EntryState.WAITING,
                        CampingFixtures.now,
                        CampingFixtures.now,
                    ),
                )
        }
    private val accounts = FakeAccountRepository(CampingFixtures.account("owner").copy(roles = setOf(Role.OWNER)))
    private val notifications = FakeNotificationRepository()
    private val store = ViewModelStore()
    private val requests = mutableListOf<AnnouncementAttempt>()
    private var nextId = 0

    /** AN-01: blank fields do not write; multiline Send freezes one exact guarded request. */
    @Test fun explicitSendValidatesThenFreezesOnlyOneCompleteRequest() =
        test {
            val gate = CompletableDeferred<String>()
            notifications.announceHandler = { id, lottery, actor, value ->
                requests += AnnouncementAttempt(id, lottery, actor, value.title, value.body)
                gate.await()
            }
            val model = model()
            runCurrent()
            assertTrue(requests.isEmpty())
            model.editTitle(" ")
            model.editBody("A body")
            model.send()
            assertTrue(model.state.value.titleInvalid)
            model.editTitle("Arrival details")
            model.editBody("\n ")
            model.send()
            assertTrue(model.state.value.bodyInvalid)
            assertTrue(requests.isEmpty())
            model.editBody("Please use the east gate.\nWe will meet you there.")
            model.send()
            model.send()
            runCurrent()
            val attempt = requests.single()
            assertEquals(
                AnnouncementAttempt(
                    "event-1",
                    savedLottery.id,
                    "owner",
                    "Arrival details",
                    "Please use the east gate.\nWe will meet you there.",
                ),
                attempt,
            )
            assertEquals(AnnouncementPhase.SENDING, model.state.value.phase)
            assertFalse(model.state.value.sent)
            model.editTitle("Do not replace")
            model.editBody("Do not replace")
            assertEquals(attempt.title, model.state.value.title)
            assertEquals(attempt.body, model.state.value.body)
            assertFalse(model.requestBack())
            gate.complete(attempt.id)
            runCurrent()
            assertTrue(model.state.value.sent)
            assertEquals("", model.state.value.body)
            assertTrue(model.consumeSent())
            assertFalse(model.consumeSent())
            assertTrue(notifications.readRequests.isEmpty())
        }

    /** AN-02: identical, absent, failed-read and conflicting saved events retain honest recovery. */
    @Test fun uncertainResultsCompareTheCompleteFrozenTupleAndRetryOnlyTheOriginalAttempt() =
        test {
            for (kind in 0..3) {
                requests.clear()
                notifications.getHandler = null
                notifications.records.value = emptyList()
                notifications.announceHandler = { id, lottery, actor, value ->
                    requests += AnnouncementAttempt(id, lottery, actor, value.title, value.body)
                    throw IOException("unknown outcome")
                }
                val model = model(key = "case-$kind")
                runCurrent()
                model.editTitle("Trail access")
                model.editBody("Use the east gate.\nThanks!")
                val readGate = CompletableDeferred<Unit>()
                notifications.getHandler = { id ->
                    if (kind == 2) readGate.await()
                    when (kind) {
                        0 -> {
                            event(
                                AnnouncementAttempt(
                                    id,
                                    savedLottery.id,
                                    "owner",
                                    model.state.value.title,
                                    model.state.value.body,
                                ),
                            )
                        }

                        1 -> {
                            null
                        }

                        2 -> {
                            throw IOException("cannot read")
                        }

                        else -> {
                            event(
                                AnnouncementAttempt(
                                    id,
                                    "other-lottery",
                                    "co-owner",
                                    "Different title",
                                    "Different saved body",
                                ),
                            )
                        }
                    }
                }
                model.send()
                runCurrent()
                val original = requests.single()
                if (kind == 2) {
                    assertEquals(AnnouncementPhase.CHECKING, model.state.value.phase)
                    model.editTitle("Blocked title")
                    model.editBody("Blocked body")
                    assertEquals(original.title, model.state.value.title)
                    assertEquals(original.body, model.state.value.body)
                    readGate.complete(Unit)
                    runCurrent()
                }
                assertEquals(
                    if (kind == 0) {
                        AnnouncementPhase.IDLE
                    } else {
                        when (kind) {
                            1 -> AnnouncementPhase.ABSENT
                            2 -> AnnouncementPhase.RETRY
                            else -> AnnouncementPhase.CONFLICT
                        }
                    },
                    model.state.value.phase,
                )
                assertEquals(kind == 0, model.state.value.sent)
                if (kind != 0) {
                    assertEquals(original, model.state.value.attempt)
                    model.editBody("Blocked change")
                    assertEquals(original.body, model.state.value.body)
                }
                if (kind == 2) {
                    val retryRead = CompletableDeferred<Notification?>()
                    notifications.getHandler = { retryRead.await() }
                    model.retrySend()
                    runCurrent()
                    assertEquals(AnnouncementPhase.CHECKING, model.state.value.phase)
                    assertEquals(listOf(original), requests)
                    retryRead.complete(event(original))
                    runCurrent()
                    assertTrue(model.state.value.sent)
                    assertEquals(listOf(original), requests)
                }
                if (kind == 1) {
                    notifications.announceHandler = { id, lottery, actor, value ->
                        requests += AnnouncementAttempt(id, lottery, actor, value.title, value.body)
                        id
                    }
                    model.retrySend()
                    model.retrySend()
                    runCurrent()
                    assertEquals(listOf(original, original), requests)
                    assertTrue(model.state.value.sent)
                }
            }
        }

    /** AN-03: restoration retains its actor/payload without replay; absent Edit starts a new ID. */
    @Test fun restoredAttemptRequiresExplicitRecoveryAndDoesNotRebindItsSender() =
        test {
            val saved = SavedStateHandle()
            notifications.announceHandler = { id, lottery, actor, value ->
                requests += AnnouncementAttempt(id, lottery, actor, value.title, value.body)
                throw IOException("unknown result")
            }
            val first = model(saved = saved)
            runCurrent()
            first.editTitle("Original title")
            first.editBody("Original body")
            first.send()
            runCurrent()
            val original = requests.single()
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            requests.clear()
            val restored = model(saved = SavedStateHandle(values))
            runCurrent()
            assertEquals(original, restored.state.value.attempt)
            assertTrue(requests.isEmpty())
            restored.retrySend()
            runCurrent()
            assertEquals(listOf(original), requests)
            assertEquals(AnnouncementPhase.ABSENT, restored.state.value.phase)
            restored.editAttempt()
            restored.editBody("Changed body")
            restored.send()
            runCurrent()
            assertEquals("event-2", requests.last().id)
            assertEquals("Changed body", requests.last().body)
            accounts.account.value = CampingFixtures.account("co-owner").copy(roles = setOf(Role.OWNER))
            val foreign = model(actor = "co-owner", key = "foreign", saved = SavedStateHandle(values))
            runCurrent()
            foreign.retrySend()
            runCurrent()
            assertEquals(2, requests.size)
            assertEquals(original, foreign.state.value.attempt)
            assertEquals(AnnouncementPhase.CONFLICT, foreign.state.value.phase)
        }

    /** AN-03: published historical states permit actual active participants; unavailable actors cannot send. */
    @Test fun historicalPublishedLotteriesKeepTheExistingParticipantSendContract() =
        test {
            val closed =
                savedLottery.copy(
                    drawnAt = CampingFixtures.now.minusSeconds(2000),
                    configuration =
                        savedLottery.configuration.copy(
                            registrationOpensAt = CampingFixtures.now.minusSeconds(5000),
                            registrationClosesAt = CampingFixtures.now.minusSeconds(4000),
                            bookingOpensAt = CampingFixtures.now.minusSeconds(3000),
                            bookingClosesAt = CampingFixtures.now.minusSeconds(1000),
                        ),
                )
            for (record in listOf(
                savedLottery,
                savedLottery.copy(drawnAt = CampingFixtures.now),
                closed,
                savedLottery.copy(cancellation = Cancellation(CampingFixtures.now, "owner")),
            )) {
                lotteries.records.value = listOf(record)
                val model = model(key = "history-${record.drawnAt}-${record.cancellation}")
                runCurrent()
                assertTrue(model.state.value.canSend)
            }
            accounts.account.value = null
            runCurrent()
            val unavailable = model(key = "unavailable")
            runCurrent()
            assertFalse(unavailable.state.value.canSend)
            unavailable.send()
            assertTrue(requests.isEmpty())
        }

    /** Creates a real controller with retained primitives and deterministic explicit identities.
     * @param actor Actual original owner.
     * @param key Distinct owned controller.
     * @param saved Restored form/request primitives.
     */
    private fun model(
        actor: String = "owner",
        key: String = "compose",
        saved: SavedStateHandle = SavedStateHandle(),
    ) =
        AnnouncementComposeViewModel(
            actor,
            savedLottery.id,
            lotteries,
            accounts,
            notifications,
            saved,
            { "event-${++nextId}" },
        ).also { store.put(key, it) }

    /** Supplies a saved event with all immutable request fields for reconciliation.
     * @param attempt Original or deliberately conflicting request tuple.
     */
    private fun event(attempt: AnnouncementAttempt) =
        Notification(
            attempt.id,
            attempt.senderId,
            NotificationType.OWNER_ANNOUNCEMENT,
            NotificationSubject(SubjectType.LOTTERY, attempt.lotteryId),
            Announcement(attempt.title, attempt.body),
            CampingFixtures.now,
        )

    /** Keeps the fixed cases and held readers inside the owned model lifetime.
     * @param block Declared contract outcomes.
     */
    private fun test(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

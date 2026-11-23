package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Fixed MS-01 through MS-04 contracts for participant reads and retained send attempts. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val reservation = CampingFixtures.reservation()
    private val site = CampingFixtures.campsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val bookings = FakeBookingRepository(listOf(reservation))
    private val messages = FakeMessageRepository()
    private val profiles =
        mapOf(
            "entrant" to MutableStateFlow<Account?>(CampingFixtures.account()),
            "owner" to MutableStateFlow<Account?>(CampingFixtures.account("owner")),
        )
    private val accounts =
        object : AccountRepository by FakeAccountRepository() {
            /** Supplies independent actual profile facts for the two saved participants.
             * @param id Saved participant identity.
             */
            override fun observe(id: String): Flow<Account?> =
                profiles.getValue(id)
        }
    private val sends = mutableListOf<MessageAttempt>()
    private var ids = 0

    /** A failed refresh keeps confirmed history and the draft until an explicit retry succeeds. */
    @Test fun messageReadFailureKeepsHistoryAndDraftDuringRetry() =
        test {
            val offline = MutableStateFlow(false)
            val saved = message("saved", "owner", 1)
            val source =
                object : MessageRepository by messages {
                    /** Emits the saved history, then a controlled read failure.
                     * @param reservationId Saved conversation identity.
                     */
                    override fun observeConversation(reservationId: String): Flow<List<Message>> =
                        flow {
                            offline.collect { failed ->
                                if (failed) throw IOException("offline")
                                emit(listOf(saved))
                            }
                        }
                }
            val model =
                ConversationViewModel(
                    "entrant",
                    reservation.id,
                    bookings,
                    ConversationContextResolver(sites),
                    source,
                    accounts,
                    SavedStateHandle(),
                    { "new-message" },
                ).also { store.put("refresh", it) }
            runCurrent()
            model.edit("My retained draft")
            offline.value = true
            runCurrent()
            assertTrue(model.state.value.messagesFailed)
            assertEquals(listOf(saved), model.state.value.messages)
            assertEquals("My retained draft", model.state.value.input)
            model.retry()
            assertEquals(listOf(saved), model.state.value.messages)
            offline.value = false
            runCurrent()
            assertFalse(model.state.value.messagesFailed)
            assertEquals(listOf(saved), model.state.value.messages)
            assertEquals("My retained draft", model.state.value.input)
        }

    /** MS-01: actual participants read ordered history, including cancelled/past context. */
    @Test fun actualParticipantsKeepHistoryAndMissingLabelsWithoutAnActiveStayRestriction() =
        test {
            messages.records.value = listOf(message("later", "owner", 2), message("first", "entrant", 1))
            profiles.getValue("owner").value = null
            val model = model()
            runCurrent()
            assertEquals(ConversationRead.READY, model.state.value.read)
            assertEquals(
                listOf("first", "later"),
                model.state.value.messages
                    .map { it.id },
            )
            assertEquals(null, model.state.value.labels["owner"])
            profiles.getValue("owner").value = CampingFixtures.account("owner")
            runCurrent()
            assertTrue(model.state.value.canSend)
            bookings.records.value =
                listOf(
                    reservation.copy(
                        cancellation =
                            io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation(
                                CampingFixtures.now,
                                "entrant",
                                io.github.CMPUT301F26stderr.campsbridge.domain.account.Role.ENTRANT,
                                null,
                            ),
                    ),
                )
            runCurrent()
            assertTrue(model.state.value.canSend)
            val unrelated = model(actor = "other-owner", key = "unrelated")
            runCurrent()
            assertEquals(ConversationRead.UNAVAILABLE, unrelated.state.value.read)
            assertFalse(unrelated.state.value.canSend)
        }

    /** MS-01: missing/failed stay or campsite sources never become empty editable history. */
    @Test fun missingAndFailedRequiredContextRemainDistinct() =
        test {
            for (vector in listOf("missing-stay", "failed-stay", "missing-site", "failed-site")) {
                bookings.records.value = if (vector == "missing-stay") emptyList() else listOf(reservation)
                bookings.recordFailure.value = if (vector == "failed-stay") IOException("stay offline") else null
                sites.records.value = if (vector == "missing-site") emptyList() else listOf(site)
                sites.readFailure = if (vector == "failed-site") IOException("site offline") else null
                val model = model(key = vector)
                runCurrent()
                assertEquals(
                    vector,
                    if (vector.startsWith("missing")) ConversationRead.UNAVAILABLE else ConversationRead.FAILURE,
                    model.state.value.read,
                )
                assertFalse(model.state.value.canSend)
            }
        }

    /** MS-02: blank/overlapping sends do nothing and one request freezes the exact tuple/body. */
    @Test fun sendFreezesOneExactRequestAndOnlyObservedRowsBecomeHistory() =
        test {
            val gate = CompletableDeferred<String>()
            messages.sendHandler =
                { id, stay, sender, body ->
                    sends.add(MessageAttempt(id, stay, sender, body))
                    gate.await()
                }
            try {
                val model = model()
                runCurrent()
                model.edit("  ")
                model.send()
                runCurrent()
                assertTrue(sends.isEmpty())
                model.edit("  Exact body\nSecond line.  ")
                model.send()
                model.send()
                model.edit("Should not replace the frozen body")
                runCurrent()
                assertEquals(
                    listOf(MessageAttempt("message-1", reservation.id, "entrant", "  Exact body\nSecond line.  ")),
                    sends,
                )
                assertEquals(MessageSendPhase.SENDING, model.state.value.phase)
                assertEquals(sends.single().body, model.state.value.input)
                assertTrue(
                    model.state.value.messages
                        .isEmpty(),
                )
                gate.complete(sends.single().id)
                runCurrent()
                assertEquals("", model.state.value.input)
                assertTrue(
                    model.state.value.messages
                        .isEmpty(),
                )
                messages.records.value =
                    listOf(Message("message-1", reservation.id, "entrant", sends.single().body, CampingFixtures.now))
                runCurrent()
                assertEquals(
                    listOf("message-1"),
                    model.state.value.messages
                        .map { it.id },
                )
            } finally {
                gate.complete("message-1")
            }
        }

    /** MS-03: fixed reconciliation outcomes preserve IDs/body and never overwrite conflicting content. */
    @Test fun uncertainSendReconcilesIdenticalAbsentFailedAndConflictingRecords() =
        test {
            for (vector in listOf("identical", "absent", "read-failed", "conflict")) {
                store.clear()
                sends.clear()
                val model = model()
                runCurrent()
                messages.sendHandler =
                    {
                        id,
                        stay,
                        sender,
                        body,
                        ->
                        sends.add(MessageAttempt(id, stay, sender, body))
                        throw IOException("ack lost")
                    }
                messages.getHandler = { id ->
                    when (vector) {
                        "identical" -> Message(id, reservation.id, "entrant", "Message body", CampingFixtures.now)
                        "absent" -> null
                        "read-failed" -> throw IOException("read offline")
                        else -> Message(id, reservation.id, "entrant", "Different saved body", CampingFixtures.now)
                    }
                }
                model.edit("Message body")
                model.send()
                runCurrent()
                assertEquals(1, sends.size)
                if (vector == "identical") {
                    assertEquals(MessageSendPhase.IDLE, model.state.value.phase)
                    assertEquals("", model.state.value.input)
                } else {
                    assertEquals("Message body", model.state.value.input)
                    assertEquals(sends.single(), model.state.value.attempt)
                    assertEquals(
                        when (vector) {
                            "absent" -> MessageSendPhase.ABSENT
                            "read-failed" -> MessageSendPhase.RETRY
                            else -> MessageSendPhase.CONFLICT
                        },
                        model.state.value.phase,
                    )
                    model.edit("Blocked edit")
                    assertEquals("Message body", model.state.value.input)
                    if (vector == "absent") {
                        messages.sendHandler =
                            { id, stay, sender, body ->
                                sends.add(MessageAttempt(id, stay, sender, body))
                                id
                            }
                        model.retrySend()
                        model.retrySend()
                        runCurrent()
                        assertEquals(sends.first(), sends.last())
                        assertEquals(2, sends.size)
                    }
                }
            }
        }

    /** MS-03: confirmed absence permits explicit Edit/new ID and restoration never auto-sends. */
    @Test fun absentAttemptCanBeExplicitlyEditedAndRestoredAttemptWaitsForRetry() =
        test {
            val saved = SavedStateHandle()
            messages.sendHandler =
                {
                    id,
                    stay,
                    sender,
                    body,
                    ->
                    sends.add(MessageAttempt(id, stay, sender, body))
                    throw IOException("offline")
                }
            messages.getHandler = { null }
            val first = model(saved = saved)
            runCurrent()
            first.edit("Original")
            first.send()
            runCurrent()
            val primitives = saved.keys().associateWith { saved.get<Any?>(it) }
            store.clear()
            val restored = model(saved = SavedStateHandle(primitives))
            runCurrent()
            assertEquals(1, sends.size)
            assertEquals(sends.single(), restored.state.value.attempt)
            restored.retrySend()
            runCurrent()
            assertEquals(sends[0], sends[1])
            restored.editAttempt()
            restored.edit("Edited")
            restored.send()
            runCurrent()
            assertEquals("Edited", sends.last().body)
            assertTrue(sends.last().id != sends.first().id)
        }

    /** MS-04: only visible/resumed incoming unread rows are acknowledged; failures require Retry. */
    @Test fun visibleRecipientAcknowledgementsDoNotReplayConfirmedReads() =
        test {
            val incomingA = message("incoming-a", "owner", 1)
            val incomingB = message("incoming-b", "owner", 2)
            val read = message("already-read", "owner", 3).copy(readAt = CampingFixtures.now)
            val outgoing = message("outgoing", "entrant", 4)
            messages.records.value = listOf(incomingA, incomingB, read, outgoing)
            var failB = true
            messages.markReadHandler = { id, _ -> if (id == incomingB.id && failB) throw IOException("read offline") }
            val model = model()
            runCurrent()
            assertTrue(messages.readRequests.isEmpty())
            model.setVisible(false)
            runCurrent()
            assertTrue(messages.readRequests.isEmpty())
            model.setVisible(true)
            runCurrent()
            assertEquals(setOf(incomingA.id, incomingB.id), messages.readRequests.map { it.first }.toSet())
            assertEquals(setOf(incomingB.id), model.state.value.failedReadIds)
            messages.records.value = listOf(incomingB, incomingA, read, outgoing)
            runCurrent()
            assertEquals(2, messages.readRequests.size)
            failB = false
            model.retryReads()
            runCurrent()
            assertEquals(listOf(incomingA.id, incomingB.id, incomingB.id), messages.readRequests.map { it.first })
            assertTrue(
                model.state.value.failedReadIds
                    .isEmpty(),
            )
        }

    /** Closed entries release readers and reject stale input, retry and visibility callbacks. */
    @Test fun clearedEntryCannotChangeDraftOrRestartOperations() =
        test {
            messages.records.value = listOf(message("incoming", "owner", 1))
            val model = model()
            runCurrent()
            model.edit("Retain this exact draft")
            store.clear()
            runCurrent()
            val before = model.state.value
            val lookups = messages.getRequests.toList()
            model.edit("Late input")
            model.send()
            model.retrySend()
            model.retry()
            model.setVisible(true)
            model.retryReads()
            model.editAttempt()
            model.dismissDiscard()
            assertFalse(model.requestBack())
            assertFalse(model.confirmDiscard())
            runCurrent()
            assertEquals(before, model.state.value)
            assertEquals(lookups, messages.getRequests)
            assertTrue(messages.readRequests.isEmpty())
            assertTrue(sends.isEmpty())
            assertEquals(0, bookings.activeRecordObservers)
        }

    /** Constructs the actual participant owner with controlled IDs and no automatic lifecycle visibility.
     * @param actor Fixed current account.
     * @param key Distinct model owner.
     * @param saved Retained input/request primitives.
     */
    private fun model(
        actor: String = "entrant",
        key: String = "conversation",
        saved: SavedStateHandle = SavedStateHandle(),
    ): ConversationViewModel =
        ConversationViewModel(
            actor,
            reservation.id,
            bookings,
            ConversationContextResolver(
                sites,
            ),
            messages,
            accounts,
            saved,
            {
                "message-${++ids}"
            },
        ).also { store.put(key, it) }

    /** Supplies ordered saved text without inventing recipients or receipt writes.
     * @param id Original message identity.
     * @param sender Actual participant.
     * @param seconds Controlled timestamp offset.
     */
    private fun message(
        id: String,
        sender: String,
        seconds: Long,
    ): Message =
        Message(id, reservation.id, sender, id, CampingFixtures.now.plusSeconds(seconds))

    /** Bounds all readers and held actions to the test's actual ViewModel store.
     * @param block Fixed public contract assertions.
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

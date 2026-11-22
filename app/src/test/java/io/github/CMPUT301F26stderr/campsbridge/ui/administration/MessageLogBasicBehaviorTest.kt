package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Checks message log reads, search, recovery and retained input with fake repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageLogBasicBehaviorTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val models = mutableListOf<MessageLogViewModel>()

    /** Stops every observation started by a test. */
    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    /** Loading and a confirmed empty result remain different states. */
    @Test
    fun loadingBecomesReadyWithNoMessages() =
        runTest {
            val model = createModel(FakeMessageRepository())

            assertEquals(MessageLogRead.LOADING, model.state.value.read)
            runCurrent()

            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertTrue(
                model.state.value.rows
                    .map { it.message }
                    .isEmpty(),
            )
        }

    /** The log displays saved records and follows later changes from the repository. */
    @Test
    fun savedMessagesAndLaterUpdatesAreDisplayed() =
        runTest {
            val older = message("older", "Meet at the gate.")
            val newer = message("newer", "Check in at noon.").copy(createdAt = Instant.EPOCH.plusSeconds(10))
            val repository = FakeMessageRepository(listOf(older, newer))
            val model = createModel(repository)
            runCurrent()

            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertEquals(
                listOf(newer, older),
                model.state.value.rows
                    .map { it.message },
            )

            repository.records.value = listOf(older)
            runCurrent()
            assertEquals(
                listOf(older),
                model.state.value.rows
                    .map { it.message },
            )

            repository.records.value = emptyList()
            runCurrent()
            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertTrue(
                model.state.value.rows
                    .map { it.message }
                    .isEmpty(),
            )
        }

    /** Search ignores case and surrounding spaces, and clearing it restores all records. */
    @Test
    fun searchMatchesTextAndSavedIdsWithoutDiscardingMessages() =
        runTest {
            val gate = message("gate", "Meet at the east gate.")
            val lake =
                message("lake", "Use pitch [A].").copy(
                    senderId = "owner-lake",
                    reservationId = "stay-lake",
                )
            val model = createModel(FakeMessageRepository(listOf(gate, lake)))
            runCurrent()

            model.editQuery("  EAST GATE  ")
            assertEquals("  EAST GATE  ", model.state.value.query)
            assertEquals(
                listOf(gate),
                model.state.value.rows
                    .map { it.message },
            )

            for (query in listOf("OWNER-LAKE", "STAY-LAKE", "[A]")) {
                model.editQuery(query)
                assertEquals(
                    listOf(lake),
                    model.state.value.rows
                        .map { it.message },
                )
            }

            model.editQuery("no matching message")
            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertTrue(
                model.state.value.rows
                    .map { it.message }
                    .isEmpty(),
            )

            model.editQuery("   ")
            assertEquals(
                setOf(gate, lake),
                model.state.value.rows
                    .map { it.message }
                    .toSet(),
            )

            model.editQuery("")
            assertEquals(
                setOf(gate, lake),
                model.state.value.rows
                    .map { it.message }
                    .toSet(),
            )
        }

    /** New repository results are filtered using the query already entered by the user. */
    @Test
    fun activeSearchAppliesToLaterMessages() =
        runTest {
            val repository = FakeMessageRepository()
            val model = createModel(repository)
            model.editQuery("gate")
            runCurrent()

            val matching = message("matching", "Meet at the gate.")
            val other = message("other", "Check in at noon.")
            repository.records.value = listOf(other, matching)
            runCurrent()

            assertEquals(
                listOf(matching),
                model.state.value.rows
                    .map { it.message },
            )
            assertEquals("gate", model.state.value.query)
        }

    /** A failed read can recover without losing the query or confusing failure with empty. */
    @Test
    fun retryRecoversFromFailureAndKeepsSearch() =
        runTest {
            val repository = FakeMessageRepository()
            repository.logFailure.value = IOException("Offline")
            val model = createModel(repository)
            model.editQuery("gate")
            runCurrent()

            assertEquals(MessageLogRead.FAILURE, model.state.value.read)

            val matching = message("matching", "Meet at the gate.")
            repository.records.value = listOf(matching, message("other", "Check in at noon."))
            repository.logFailure.value = null
            model.retry()
            model.retry()

            assertEquals(MessageLogRead.LOADING, model.state.value.read)
            assertEquals("gate", model.state.value.query)
            runCurrent()

            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertEquals(
                listOf(matching),
                model.state.value.rows
                    .map { it.message },
            )
            assertEquals("gate", model.state.value.query)
        }

    /** A new ViewModel restores the raw query while reading fresh repository records. */
    @Test
    fun searchIsSavedAndRestoredForANewViewModel() =
        runTest {
            val savedState = SavedStateHandle()
            val first = createModel(FakeMessageRepository(), savedState)
            first.editQuery("  GATE  ")
            val savedValues = savedState.keys().associateWith { savedState.get<Any?>(it) }
            first.viewModelScope.cancel()

            val matching = message("matching", "Meet at the gate.")
            val repository = FakeMessageRepository(listOf(matching, message("other", "Check in at noon.")))
            val restored = createModel(repository, SavedStateHandle(savedValues))

            assertEquals("  GATE  ", restored.state.value.query)
            assertEquals(MessageLogRead.LOADING, restored.state.value.read)
            runCurrent()

            assertEquals(MessageLogRead.READY, restored.state.value.read)
            assertEquals(
                listOf(matching),
                restored.state.value.rows
                    .map { it.message },
            )
            assertTrue(savedValues.values.all { it is String })
        }

    /** Browsing, searching and retrying neither sends nor acknowledges saved messages. */
    @Test
    fun logOperationsRemainReadOnly() =
        runTest {
            val original = message("saved", "Meet at the gate.")
            val repository = FakeMessageRepository(listOf(original))
            var sends = 0
            repository.sendHandler = { _, _, _, _ ->
                sends++
                "unexpected-send"
            }
            val model = createModel(repository)
            runCurrent()
            model.editQuery("gate")
            model.editQuery("")
            model.retry()
            runCurrent()

            repository.logFailure.value = IOException("Offline")
            runCurrent()
            assertEquals(MessageLogRead.FAILURE, model.state.value.read)
            repository.logFailure.value = null
            model.retry()
            runCurrent()

            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertEquals(0, sends)
            assertTrue(repository.readRequests.isEmpty())
            assertEquals(listOf(original), repository.records.value)
            assertEquals(
                listOf(original),
                model.state.value.rows
                    .map { it.message },
            )
        }

    /** Coroutine cancellation must not be reported as a failed repository read. */
    @Test
    fun cancellationDoesNotBecomeFailure() =
        runTest {
            val repository =
                object : MessageRepository by FakeMessageRepository() {
                    /** Simulates cancellation while observing the saved log. */
                    override fun observeAll(): Flow<List<Message>> =
                        flow {
                            throw CancellationException("Observation cancelled")
                        }
                }
            val model = createModel(repository)
            runCurrent()

            assertEquals(MessageLogRead.LOADING, model.state.value.read)
        }

    /** Conversation history uses exact reservation identity and remains complete while searching. */
    @Test
    fun conversationIsOrderedAndIndependentOfSearchAndMissingContext() =
        runTest {
            val older = message("older", "Meet at the gate.")
            val newer = message("newer", "We arrive at noon.").copy(createdAt = Instant.EPOCH.plusSeconds(10))
            val unrelated = message("unrelated", "Private other stay.").copy(reservationId = "other-stay")
            val repository = FakeMessageRepository(listOf(newer, unrelated, older))
            val model = createModel(repository)
            runCurrent()

            model.editQuery("gate")
            assertEquals(
                listOf(older),
                model.state.value.rows
                    .map { it.message },
            )
            assertEquals(listOf(older, newer), model.conversation(older.id).map { it.message })
            assertTrue(model.conversation("missing-message").isEmpty())

            val reply = message("reply", "Thanks for the directions.").copy(createdAt = Instant.EPOCH.plusSeconds(20))
            repository.records.value = listOf(reply, unrelated, newer, older)
            runCurrent()
            assertEquals(listOf(older, newer, reply), model.conversation(older.id).map { it.message })
            assertEquals(
                listOf(older, newer, reply, unrelated).toSet(),
                model.savedRows.value
                    .map { it.message }
                    .toSet(),
            )

            repository.records.value = listOf(newer, reply, unrelated)
            runCurrent()
            assertTrue(model.conversation(older.id).isEmpty())
            assertTrue(repository.readRequests.isEmpty())
        }

    /** A failed history source retains known content and retries against the same message identity. */
    @Test
    fun conversationSurvivesFailureAndRecoversWithoutChangingTarget() =
        runTest {
            val original = message("selected", "Meet at the gate.")
            val repository = FakeMessageRepository(listOf(original))
            val model = createModel(repository)
            runCurrent()
            model.editQuery("no matches")

            repository.logFailure.value = IOException("History offline")
            runCurrent()
            assertEquals(MessageLogRead.FAILURE, model.state.value.read)
            assertEquals(listOf(original), model.conversation(original.id).map { it.message })

            val reply = message("reply", "See you soon.").copy(createdAt = Instant.EPOCH.plusSeconds(1))
            repository.records.value = listOf(reply, original)
            repository.logFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(MessageLogRead.READY, model.state.value.read)
            assertEquals("no matches", model.state.value.query)
            assertEquals(listOf(original, reply), model.conversation(original.id).map { it.message })
            assertTrue(repository.readRequests.isEmpty())
        }

    /** Creates an owned ViewModel whose observation is stopped after each test.
     * @param repository Controlled message source.
     * @param savedState Small retained UI values.
     */
    private fun createModel(
        repository: MessageRepository,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): MessageLogViewModel =
        MessageLogViewModel(
            repository,
            FakeBookingRepository(),
            FakeCampsiteRepository(),
            FakeAccountRepository(),
            savedState,
        ).also(models::add)

    /** Creates a saved message with stable participant, reservation and time values.
     * @param id Saved message identity.
     * @param body Message text searched by the log.
     */
    private fun message(
        id: String,
        body: String,
    ): Message =
        Message(
            id = id,
            reservationId = "stay-1",
            senderId = "owner-1",
            body = body,
            createdAt = Instant.EPOCH,
        )
}

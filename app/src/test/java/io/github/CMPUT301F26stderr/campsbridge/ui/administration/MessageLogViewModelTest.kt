package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
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
class MessageLogViewModelTest {
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

            assertEquals(MessageLogReadState.LOADING, model.state.value.readState)
            runCurrent()

            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertTrue(
                model.state.value.messages
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

            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertEquals(listOf(newer, older), model.state.value.messages)

            repository.records.value = listOf(older)
            runCurrent()
            assertEquals(listOf(older), model.state.value.messages)

            repository.records.value = emptyList()
            runCurrent()
            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertTrue(
                model.state.value.messages
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
            assertEquals(listOf(gate), model.state.value.messages)

            for (query in listOf("OWNER-LAKE", "STAY-LAKE", "[A]")) {
                model.editQuery(query)
                assertEquals(listOf(lake), model.state.value.messages)
            }

            model.editQuery("no matching message")
            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )

            model.editQuery("   ")
            assertEquals(
                setOf(gate, lake),
                model.state.value.messages
                    .toSet(),
            )

            model.editQuery("")
            assertEquals(
                setOf(gate, lake),
                model.state.value.messages
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

            assertEquals(listOf(matching), model.state.value.messages)
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

            assertEquals(MessageLogReadState.FAILURE, model.state.value.readState)

            val matching = message("matching", "Meet at the gate.")
            repository.records.value = listOf(matching, message("other", "Check in at noon."))
            repository.logFailure.value = null
            model.retry()
            model.retry()

            assertEquals(MessageLogReadState.LOADING, model.state.value.readState)
            assertEquals("gate", model.state.value.query)
            runCurrent()

            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertEquals(listOf(matching), model.state.value.messages)
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
            assertEquals(MessageLogReadState.LOADING, restored.state.value.readState)
            runCurrent()

            assertEquals(MessageLogReadState.READY, restored.state.value.readState)
            assertEquals(listOf(matching), restored.state.value.messages)
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
            assertEquals(MessageLogReadState.FAILURE, model.state.value.readState)
            repository.logFailure.value = null
            model.retry()
            runCurrent()

            assertEquals(MessageLogReadState.READY, model.state.value.readState)
            assertEquals(0, sends)
            assertTrue(repository.readRequests.isEmpty())
            assertEquals(listOf(original), repository.records.value)
            assertEquals(listOf(original), model.state.value.messages)
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

            assertEquals(MessageLogReadState.LOADING, model.state.value.readState)
        }

    /** Creates an owned ViewModel whose observation is stopped after each test.
     * @param repository Controlled message source.
     * @param savedState Small retained UI values.
     */
    private fun createModel(
        repository: MessageRepository,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): MessageLogViewModel =
        MessageLogViewModel(repository, savedState).also(models::add)

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

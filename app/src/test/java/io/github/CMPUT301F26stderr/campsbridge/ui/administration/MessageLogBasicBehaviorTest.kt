/*
 * Checks message log reads, search, recovery and retained input with fake repositories.
 */

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
    // MainDispatcherRule 让 viewModelScope 在测试调度器上运行，用 runCurrent() 控制协程什么时候执行。
    @get:Rule
    val main = MainDispatcherRule()

    // 记录每个测试创建的 ViewModel，测试结束后统一停止观察。
    private val models = mutableListOf<MessageLogViewModel>()

    /** Stops every observation started by a test. */
    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    // 证明：刚创建时是 LOADING；读取完成后，没有消息是 READY 加空列表，不是失败。

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

    // 证明：已保存的消息按时间从新到旧显示；repository 删除消息后列表跟着更新，
    // 全部删除后仍然是 READY 加空列表。

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

    // 证明：搜索不区分大小写、忽略首尾空格，可以按正文、发送者 id、预订 id 搜索，输入框保留原文；
    // 没有结果时仍是 READY；搜索词为空格或空字符串时，全部消息都回来。

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

    // 证明：先输入搜索词，之后才到达的新消息也会按这个搜索词过滤。

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

    // 证明：读取失败显示 FAILURE；Retry 后先变 LOADING 再变 READY，搜索词一直保留并用于过滤。
    // 连按两次 Retry 也没问题，因为第二次时状态已经不是 FAILURE。

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

    // 证明：搜索词保存在 SavedStateHandle；新的 ViewModel 恢复原始搜索词，重新读取消息并过滤。
    // 最后检查保存的值都是字符串，也就是只保存搜索词，不保存消息。

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

    // 证明：浏览、搜索、读取失败和 Retry 都是只读的：没有发送（sends 为 0），
    // 没有标记已读（readRequests 为空），repository 里的消息也没有变化。

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

    // 证明：观察时协程被取消，不会被当成读取失败，状态仍是 LOADING 而不是 FAILURE。

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

    // 证明：对话只包含同一个 reservationId 的消息，按时间从早到晚排序，搜索时也完整显示；
    // 新回复会加入对话；选中的消息不存在或被删除时返回空列表；整个过程没有写操作。

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

    // 证明：读取失败时，已经读到的对话仍然可以看；Retry 后用同一个消息 id
    // 拿到包含新回复的对话，搜索词也保留，并且没有写操作。

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

    // 只替换消息 repository；预订、营地和账号用空的 fake，所以上下文读不到，但消息照样显示。

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

    // 默认所有消息属于同一个预订 stay-1，发送者是 owner-1，时间是 EPOCH；测试里用 copy 改需要的值。

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

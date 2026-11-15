/*
 * Controls message reads and records explicit acknowledgements for controller tests.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Controlled message source; writes must be configured by their owning test.
 * @param initial Saved messages available to lookups and observations.
 */
class FakeMessageRepository(
    initial: List<Message> = emptyList(),
) : MessageRepository {
    val records = MutableStateFlow(initial)
    val logFailure = MutableStateFlow<Exception?>(null)
    val getRequests = mutableListOf<String>()
    val readRequests = mutableListOf<Pair<String, String>>()
    var getHandler: (suspend (String) -> Message?)? = null
    var sendHandler: (suspend (String, String, String, String) -> String)? = null
    var markReadHandler: (suspend (String, String) -> Unit)? = null

    /** Records a lookup and uses its controlled result or saved records.
     * @param id Message identity.
     */
    override suspend fun get(id: String): Message? {
        getRequests += id
        getHandler?.let { return it(id) }
        return records.value.firstOrNull { it.id == id }
    }

    /** Emits messages for the requested conversation in creation order.
     * @param reservationId Saved conversation identity.
     */
    override fun observeConversation(reservationId: String): Flow<List<Message>> =
        records.map { rows -> rows.filter { it.reservationId == reservationId }.sortedBy { it.createdAt } }

    /** Emits the controlled message log in reverse creation order. */
    override fun observeAll(): Flow<List<Message>> =
        combine(records, logFailure) { rows, failure ->
            failure?.let { throw it }
            rows.sortedByDescending { it.createdAt }
        }

    /** Rejects a write that the test has not configured.
     * @param messageId Caller-retained submission identity.
     * @param reservationId Conversation identity.
     * @param senderId Sending account.
     * @param body Requested text.
     */
    override suspend fun send(
        messageId: String,
        reservationId: String,
        senderId: String,
        body: String,
    ): String =
        checkNotNull(sendHandler) { "Message writes are not configured" }(messageId, reservationId, senderId, body)

    /** Records an explicit acknowledgement without changing loaded message content.
     * @param messageId Received message.
     * @param readerId Acknowledging recipient.
     */
    override suspend fun markRead(
        messageId: String,
        readerId: String,
    ) {
        readRequests += messageId to readerId
        markReadHandler?.invoke(messageId, readerId)
    }
}

/*
 * Checks how reservations and their saved messages become conversation-list rows.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Conversation summaries: newest first, message-less stays omitted, unread counts only the other person. */
class ConversationSummariesTest {
    private val base = CampingFixtures.reservation()
    private val at = Instant.parse("2027-05-10T12:00:00Z")

    /** Builds one saved message at a fixed offset from the shared anchor time.
     * @param id Message identity.
     * @param reservationId Conversation identity.
     * @param sender Sending account.
     * @param minutes Minutes after the anchor.
     * @param read Whether the recipient has read it.
     */
    private fun message(
        id: String,
        reservationId: String,
        sender: String,
        minutes: Long,
        read: Boolean = false,
    ) =
        Message(id, reservationId, sender, "Message $id", at.plusSeconds(minutes * 60), if (read) at else null)

    /** The most recent conversation comes first and shows its newest message; quiet stays are left out. */
    @Test fun newestConversationFirstWithItsLatestMessage() {
        val older = base.copy(id = "older")
        val newer = base.copy(id = "newer")
        val quiet = base.copy(id = "quiet")
        val rows =
            conversationSummaries(
                listOf(older, quiet, newer),
                mapOf(
                    "older" to listOf(message("o1", "older", "owner", 1), message("o2", "older", "entrant", 5)),
                    "newer" to listOf(message("n1", "newer", "owner", 9)),
                    "quiet" to emptyList(),
                ),
                "entrant",
            )
        assertEquals(listOf("newer", "older"), rows.map { it.reservation.id })
        assertEquals("o2", rows[1].latest.id)
    }

    /** Unread counts the other participant's unread messages, never the reader's own or read ones. */
    @Test fun unreadCountsOnlyTheOtherPersonsUnreadMessages() {
        val stay = base.copy(id = "stay")
        val rows =
            conversationSummaries(
                listOf(stay),
                mapOf(
                    "stay" to
                        listOf(
                            message("read", "stay", "owner", 1, read = true),
                            message("mine", "stay", "entrant", 2),
                            message("new-1", "stay", "owner", 3),
                            message("new-2", "stay", "owner", 4),
                        ),
                ),
                "entrant",
            )
        assertEquals(2, rows.single().unread)
    }
}

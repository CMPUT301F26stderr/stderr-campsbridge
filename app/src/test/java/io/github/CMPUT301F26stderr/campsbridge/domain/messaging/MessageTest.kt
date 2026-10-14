package io.github.CMPUT301F26stderr.campsbridge.domain.messaging

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertThrows
import org.junit.Test

/** Owns message-body validity before a conversation write. */
class MessageTest {
    /** Empty and whitespace-only messages cannot enter a conversation. */
    @Test fun messagesRequireVisibleContent() {
        for (body in listOf("", " \n\t")) {
            assertThrows(IllegalArgumentException::class.java) {
                Message("message", "reservation", "owner", body, CampingFixtures.now)
            }
        }
    }
}

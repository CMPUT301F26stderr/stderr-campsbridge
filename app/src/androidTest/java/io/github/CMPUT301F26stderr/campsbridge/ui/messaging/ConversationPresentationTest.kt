/*
 * Checks conversation positioning and composer access through the real route and controller.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Checks conversation positioning and composer access through the real route and controller. */
class ConversationPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val entrant = sampleAccount()
    private val owner = entrant.copy(id = "owner", name = "Blair")
    private val site = sampleCampsite("conversation-site", owner.id)
    private val stay = previewReservation().copy(id = "conversation-stay", campsiteId = site.id, entrantId = entrant.id)
    private val messages = FakeMessageRepository()
    private val root =
        TestAppHost(
            campsites = FakeCampsiteRepository(listOf(site)),
            bookings = FakeBookingRepository(listOf(stay)),
            messages = messages,
        )

    /** Releases the route's observations after each independent conversation. */
    @After fun close() {
        compose.runOnIdle { root.close() }
    }

    /** Initial history opens at its end and follows incoming messages while already there. */
    @Test fun opensLatestAndFollowsIncomingAtBottom() {
        openHistory()
        compose.onNodeWithText("History message 30").assertIsDisplayed()
        compose.runOnIdle { appendMessage("incoming", owner.id, "The gate is open.") }
        compose.onNodeWithText("The gate is open.").assertIsDisplayed()
        compose.onNodeWithText("New messages").assertDoesNotExist()
    }

    /** Incoming text does not pull the reader away from earlier history. */
    @Test fun incomingPreservesReadingPositionUntilNewMessagesIsPressed() {
        openHistory()
        compose.onNodeWithTag("conversation-history").performScrollToNode(hasText("History message 1"))
        compose.onNodeWithText("History message 1").assertIsDisplayed()
        compose.runOnIdle { appendMessage("incoming", owner.id, "The gate is open.") }
        compose.onNodeWithText("History message 1").assertIsDisplayed()
        compose.onNodeWithText("New messages").assertIsDisplayed().performClick()
        compose.onNodeWithText("The gate is open.").assertIsDisplayed()
        compose.onNodeWithText("New messages").assertDoesNotExist()
    }

    /** An explicitly sent, saved message returns the reader to the latest history. */
    @Test fun ownSavedMessageReturnsToLatest() {
        openHistory()
        compose.runOnIdle {
            messages.sendHandler = { id, _, sender, body ->
                appendMessage(id, sender, body)
                id
            }
        }
        compose.onNodeWithTag("conversation-history").performScrollToNode(hasText("History message 1"))
        compose.onNodeWithTag("message-input").performTextInput("Thanks for the directions.")
        compose.onNodeWithText("Send message").performClick()
        compose.onNodeWithText("Thanks for the directions.").assertIsDisplayed()
    }

    /** Enlarged text and the keyboard keep the retained draft and Send action reachable. */
    @Test fun enlargedComposerKeepsSendReachableWithKeyboard() {
        openHistory(fontScale = 2f)
        compose.onNodeWithTag("message-input").performClick().performTextInput("We will arrive after lunch.")
        compose.onNodeWithTag("message-input").assertTextContains("We will arrive after lunch.")
        compose.onNodeWithText("Send message").assertIsDisplayed()
    }

    /** Supplies saved history and enters Messages through the actual reservation route.
     * @param fontScale System text scale applied to the host.
     */
    private fun openHistory(fontScale: Float = 1f) {
        messages.records.value =
            (1..30).map { index ->
                Message("history-$index", stay.id, owner.id, "History message $index", now.plusSeconds(index.toLong()))
            }
        root.classifyReservation = { _, _ -> ReservationCategory.UPCOMING }
        root.orderReservations = { it }
        root.conversationAccounts =
            object : AccountRepository by FakeAccountRepository() {
                /** Resolves each saved participant independently of the selected account.
                 * @param id Participant identity.
                 */
                override fun observe(id: String): Flow<Account?> =
                    flowOf(if (id == owner.id) owner else entrant)
            }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                root.Content()
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithText("Reservations").fetchSemanticsNode() }.isSuccess
        }
        compose.onNodeWithText("Reservations").performClick()
        compose.onNodeWithTag("reservation:${stay.id}").performClick()
        compose.onNodeWithText("Messages").performScrollTo().performClick()
    }

    /** Appends one confirmed record to the controlled repository stream.
     * @param id Saved message identity.
     * @param sender Sender participant.
     * @param body Saved text.
     */
    private fun appendMessage(
        id: String,
        sender: String,
        body: String,
    ) {
        messages.records.value = messages.records.value +
            Message(id, stay.id, sender, body, now.plusSeconds(100L + messages.records.value.size))
    }
}

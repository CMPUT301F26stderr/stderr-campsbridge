package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** MS-05 uses two actual account roots over shared saved reservation/message repositories. */
class ConversationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val entrant = sampleAccount()
    private val owner = sampleAccount(setOf(Role.ENTRANT, Role.OWNER)).copy(id = "owner-B", name = "Blair")
    private val site = sampleCampsite("site/B ?1", owner.id)
    private val saved = previewReservation().copy(id = "stay/B ?1", campsiteId = site.id, entrantId = entrant.id)
    private val campsites = FakeCampsiteRepository(listOf(site))
    private val bookings = FakeBookingRepository(listOf(saved))
    private val messages = FakeMessageRepository()
    private val entrantRoot = TestAppHost(campsites = campsites, bookings = bookings, messages = messages)
    private val ownerRoot = TestAppHost(campsites = campsites, bookings = bookings, messages = messages)
    private val showingOwner = mutableStateOf(false)

    /** Releases both roots and their actual route/controller observations. */
    @After fun close() {
        compose.runOnIdle {
            entrantRoot.close()
            ownerRoot.close()
        }
    }

    /** MS-05: both participants exchange observed text, then unavailable Send preserves history. */
    @Test fun reservationParticipantsExchangeSavedMessagesAndKeepHistoryAfterSendingBecomesUnavailable() {
        val profiles =
            mapOf(
                entrant.id to MutableStateFlow<Account?>(entrant),
                owner.id to MutableStateFlow<Account?>(owner),
            )
        val accounts =
            object : AccountRepository by FakeAccountRepository() {
                /** Supplies actual participant availability independently of either root's selected actor.
                 * @param id Participant identity.
                 */
                override fun observe(id: String): Flow<Account?> =
                    profiles.getValue(id)
            }
        for (root in listOf(entrantRoot, ownerRoot)) {
            root.classifyReservation = { _, _ -> ReservationCategory.UPCOMING }
            root.orderReservations = { it }
            root.conversationAccounts = accounts
        }
        ownerRoot.accounts.account.value = owner
        ownerRoot.identity.pending = CompletableDeferred(owner.id)
        runBlocking { ownerRoot.preferences.setWorkspace(owner.id, Role.OWNER) }
        bookings.ownerHandler = { bookings.records.map { it } }
        messages.sendHandler = { id, stay, sender, body ->
            messages.records.value =
                messages.records.value +
                Message(
                    id,
                    stay,
                    sender,
                    body,
                    now.plusSeconds(
                        messages.records.value.size
                            .toLong(),
                    ),
                )
            id
        }
        compose.setContent {
            val states = rememberSaveableStateHolder()
            states.SaveableStateProvider(showingOwner.value) {
                if (showingOwner.value) ownerRoot.Content() else entrantRoot.Content()
            }
        }
        openConversation()
        compose.onNodeWithTag("message-input").performScrollTo().performTextReplacement("Is the riverside path open?")
        compose.onNodeWithTag("conversation-reservation").performClick()
        compose.onNodeWithText("Reservation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("message-input").assertTextContains("Is the riverside path open?")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        compose.onNodeWithText("Is the riverside path open?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sent").assertIsDisplayed()
        compose.runOnIdle { showingOwner.value = true }
        openConversation()
        compose.onNodeWithText("Is the riverside path open?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Received").assertIsDisplayed()
        compose.onNodeWithTag("message-input").performScrollTo().performTextReplacement("Yes, the path is open.")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        compose.onNodeWithText("Yes, the path is open.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { showingOwner.value = false }
        compose.onNodeWithText("Yes, the path is open.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            messages.sendHandler =
                { _, _, _, _ -> throw PersistenceFailure(PersistenceFailure.Reason.INACTIVE, "Recipient removed") }
        }
        compose.onNodeWithTag("message-input").performScrollTo().performTextReplacement("Thanks for confirming.")
        compose.onNodeWithText("Send message").performScrollTo().performClick()
        compose.onNodeWithText("Sending is unavailable for this conversation.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("message-input").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Is the riverside path open?").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                setOf(entrant.id, owner.id),
                messages.records.value
                    .map { it.senderId }
                    .toSet(),
            )
            assertTrue(messages.records.value.all { it.reservationId == saved.id })
            assertTrue(entrantRoot.notifications.readRequests.isEmpty())
            assertTrue(ownerRoot.notifications.readRequests.isEmpty())
        }
    }

    /** Opens Messages from the actual saved reservation list/detail, never a simulated host. */
    private fun openConversation() {
        compose.onNodeWithText("Reservations").performClick()
        compose.onNodeWithTag("reservation:${saved.id}").performClick()
        compose.onNodeWithText("Messages").performScrollTo().performClick()
    }
}

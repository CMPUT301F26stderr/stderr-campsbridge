package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Real owner child navigation retains one saved parent and exposes only completed outcomes. */
class ParticipantListTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val parent =
        Lottery(
            "draw/A",
            "account-1",
            "Riverside May lottery",
            "",
            LotteryConfiguration(
                now.minusSeconds(120),
                now.minusSeconds(60),
                now,
                now.plusSeconds(3600),
                1,
                null,
            ),
            1,
            now.minusSeconds(300),
            publishedAt = now.minusSeconds(240),
        )
    private val first = entry("entrant-A", EntryState.OFFERED)
    private val second = entry("entrant-B", EntryState.LOST)

    /** Releases the actual navigation store and its readers after every interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Missing names retain canonical IDs; Back and restoration keep the existing parent. */
    @Test fun missingProfileAndChildBackRetainTheSavedParent() {
        val restoration = StateRestorationTester(compose)
        start(restoration)
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose.onNodeWithText("Profile unavailable").assertIsDisplayed()
        compose.onNodeWithText("entrant-B").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("entrant-B").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertEquals(1, host.lotteries.activeRecordObservers)
            assertEquals(1, host.lotteries.activeEntriesObservers)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.onNodeWithText("Participants").performClick()
        compose.onNodeWithText("entrant-B").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertTrue(host.lotteries.events.isEmpty())
        }
    }

    /** A pending helper cannot masquerade as an empty result and All remains available. */
    @Test fun pendingFilterSurvivesBackAndRestorationWithoutHidingAllPermanently() {
        val restoration = StateRestorationTester(compose)
        start(restoration)
        choose("Waiting")
        compose
            .onNodeWithText(
                "Participant filtering is pending implementation. Select All participants to see the full list.",
            ).assertIsDisplayed()
        compose.onNodeWithText("No participants for this selection.").assertDoesNotExist()
        compose.onNodeWithText("entrant-A").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Waiting").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Participants").performClick()
        compose.onNodeWithText("Waiting").performClick()
        compose.onNode(hasText("All participants") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.onNodeWithText("entrant-B").assertIsDisplayed()
    }

    /** Partial outcomes are hidden in rows and available filter choices until parent completion. */
    @Test fun completionReprojectsRowsAndEnablesTheSuppliedExactFilter() {
        host.participantFilter = { _, state ->
            when (state) {
                EntryState.OFFERED -> {
                    listOf(first)
                }

                EntryState.WAITING -> {
                    listOf(
                        first.copy(state = EntryState.WAITING),
                        second.copy(state = EntryState.WAITING),
                    )
                }

                else -> {
                    error("Add fixed filter fixture: $state")
                }
            }
        }
        start()
        compose.onNodeWithText("All participants").performClick()
        compose.onNodeWithText("Selected").assertDoesNotExist()
        compose.onNodeWithText("Not selected").assertDoesNotExist()
        compose.onNode(hasText("Waiting") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.onNodeWithText("entrant-B").assertIsDisplayed()
        compose.runOnIdle { host.lotteries.records.value = listOf(parent.copy(drawnAt = now)) }
        compose.onNodeWithText("Waiting").performClick()
        compose.onNode(hasText("Selected") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.onNodeWithText("entrant-B").assertDoesNotExist()
        compose.onAllNodesWithText("Selected").assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(1, host.lotteries.entriesRequests.size)
            assertTrue(host.lotteries.events.isEmpty())
        }
    }

    /** Participant-source failure has its own retry and does not replace the saved overview. */
    @Test fun entryFailureRetriesOnlyTheChildSource() {
        host.lotteries.entriesFailure.value = IOException("entries offline")
        start()
        compose.onNodeWithText("Participants could not be loaded.").assertIsDisplayed()
        compose.onNodeWithText(parent.title).assertIsDisplayed()
        compose.runOnIdle { host.lotteries.entriesFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, host.lotteries.entriesRequests.size)
            assertEquals(1, host.lotteries.observedRecordIds.size)
        }
    }

    /** A profile failure retains its row, and retry does not restart successful entry reading. */
    @Test fun nameRetryKeepsEntryIdentityAndSource() {
        host.lotteryAccounts.getHandler = { throw IOException("profiles offline") }
        start()
        compose.onNodeWithText("Some profile names could not be loaded.").assertIsDisplayed()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.runOnIdle { host.lotteryAccounts.getHandler = { sampleAccount().copy(id = it) } }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Some profile names could not be loaded.").assertDoesNotExist()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.lotteries.entriesRequests.size) }
    }

    /** Entrant detail cannot expose the owner child even for an account that owns this record. */
    @Test fun entrantDetailHasNoParticipantActionOrOwnerEntryRead() {
        host.lotteries.records.value = listOf(parent)
        host.lotteries.entries.value = listOf(entry("account-1", EntryState.WAITING))
        compose.setContent { host.Content() }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("Participants").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.lotteries.entriesRequests.isEmpty()) }
    }

    /** Parent failure retires child reads and explicit recovery returns to the same child. */
    @Test fun failedParentRecoveryKeepsTheActualChildDestination() {
        start()
        compose.runOnIdle { host.lotteries.recordFailure.value = IOException("parent offline") }
        compose.onNodeWithText("Participants").assertIsDisplayed()
        compose.onNodeWithText("entrant-A").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, host.lotteries.activeEntriesObservers)
            host.lotteries.recordFailure.value = null
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("entrant-A").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.lotteryDetailCreations) }
    }

    /** Selects a dropdown option using its native popup semantics.
     * @param label Localized option text.
     */
    private fun choose(label: String) {
        compose.onNodeWithText("All participants").performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }

    /** Opens the real primary list, saved overview and registered participant child.
     * @param restoration Optional actual composition restoration host.
     */
    private fun start(restoration: StateRestorationTester? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER, Role.ENTRANT))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.lotteries.records.value = listOf(parent)
        host.lotteries.entries.value = listOf(first, second)
        if (host.lotteryAccounts.getHandler == null) {
            host.lotteryAccounts.getHandler = { id ->
                if (id == first.accountId) sampleAccount().copy(id = id) else null
            }
        }
        if (restoration ==
            null
        ) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(parent.title).performClick()
        compose.onNodeWithText("Participants").performClick()
    }

    /** Constructs controlled saved entry facts without simulating a draw.
     * @param id Canonical account identity.
     * @param state Saved entry outcome, possibly provisional before parent completion.
     */
    private fun entry(
        id: String,
        state: EntryState,
    ) =
        LotteryEntry(parent.id, id, state, now.minusSeconds(180), now.minusSeconds(60))
}

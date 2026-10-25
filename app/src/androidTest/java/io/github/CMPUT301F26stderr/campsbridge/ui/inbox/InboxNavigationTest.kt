package io.github.CMPUT301F26stderr.campsbridge.ui.inbox

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.previewReservation
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Actual account-root Inbox and saved targets preserve explicit action and source lifetimes. */
class InboxNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val lottery =
        Lottery(
            "draw/A ?1",
            "owner",
            "May riverside draw",
            "Two-night stays",
            LotteryConfiguration(
                now.minusSeconds(7200),
                now.minusSeconds(3600),
                now.minusSeconds(1800),
                now.plusSeconds(7200),
                1,
                null,
            ),
            2,
            now.minusSeconds(10800),
            publishedAt = now.minusSeconds(9000),
            drawnAt = now.minusSeconds(1800),
        )
    private val winner =
        Notification(
            "winner",
            null,
            NotificationType.LOTTERY_WON,
            NotificationSubject(SubjectType.LOTTERY, lottery.id),
            null,
            now.minusSeconds(1800),
        )

    /** Retires the real account store and every acquired receipt/subject reader. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Default limited history is genuinely empty and does not invent an unread total. */
    @Test fun emptyInboxKeepsItsPrimaryFrameAndExplicitPendingHelpers() {
        start()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("No notifications yet.").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Notification ordering and unread count are pending in this build.",
            ).assertIsDisplayed()
        compose.onNodeWithText("0 unread").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.inboxCreations)
            assertEquals(listOf("account-1"), host.notifications.inboxRequests)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }

    /**
     * Actual winner/loser history is passive and does not claim a winning opportunity is
     * booked.
     */
    @Test fun resultArrivalDoesNotNavigateOrAcknowledge() {
        val other = lottery.copy(id = "other-draw", title = "Summer camping")
        host.lotteries.records.value = listOf(lottery, other)
        val loser =
            winner.copy(
                id = "loser",
                type = NotificationType.LOTTERY_LOST,
                subject = NotificationSubject(SubjectType.LOTTERY, other.id),
            )
        history(winner, loser)
        start()
        compose.onNodeWithText("You were selected in the lottery.").assertIsDisplayed()
        compose.onNodeWithText("You were not selected in the lottery.").assertIsDisplayed()
        compose.onNodeWithText(lottery.title).assertIsDisplayed()
        compose.onNodeWithText("Reservation confirmed").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, host.lotteryDetailCreations)
            assertTrue(host.notifications.readRequests.isEmpty())
            assertEquals(1, host.notifications.activeInboxObservers)
        }
    }

    /** An actual held/failed acknowledgement cannot delay navigation or replay it after Back. */
    @Test fun readAcknowledgementAndRetryAreIndependentFromNavigation() {
        host.lotteries.records.value = listOf(lottery)
        history(winner)
        val gate = CompletableDeferred<Unit>()
        host.notifications.markReadHandler = { _, _ ->
            gate.await()
            throw IOException("ack lost")
        }
        try {
            start()
            compose.onNodeWithTag("inbox-event-winner").performClick()
            compose.onNodeWithText("Lottery").assertIsDisplayed()
            compose.onNodeWithText(lottery.title).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("account-1" to winner.id), host.notifications.readRequests)
                gate.complete(Unit)
            }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Could not mark this notification as read.").assertIsDisplayed()
            compose.onNodeWithText("Unread").assertIsDisplayed()
            compose.runOnIdle { host.notifications.markReadHandler = { _, _ -> Unit } }
            compose.onNodeWithText("Retry read acknowledgement").performClick()
            compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("Unread").assertIsDisplayed()
            compose.runOnIdle {
                host.notifications.receipts.value =
                    mapOf("account-1" to listOf(InboxReceipt(winner.id, now)))
            }
            compose.onNodeWithText("Read").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, host.lotteryDetailCreations)
                assertEquals(List(2) { "account-1" to winner.id }, host.notifications.readRequests)
                assertEquals(1, host.inboxCreations)
            }
        } finally {
            gate.complete(Unit)
        }
    }

    /**
     * Saved-target restoration and return consume one explicit request, without replaying
     * writes.
     */
    @Test fun restorationDoesNotReopenOrRepeatAnAlreadyReadEvent() {
        host.lotteries.records.value = listOf(lottery)
        host.notifications.records.value = listOf(winner)
        host.notifications.receipts.value =
            mapOf("account-1" to listOf(InboxReceipt(winner.id, now)))
        val restoration = StateRestorationTester(compose)
        start(restoration)
        compose.onNodeWithTag("inbox-event-winner").performClick()
        compose.onNodeWithText("Lottery").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Lottery").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, host.lotteryDetailCreations)
            assertEquals(1, host.inboxCreations)
            assertEquals(listOf("account-1"), host.notifications.inboxRequests)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }

    /** Actor presentation follows the saved aggregate even when the current workspace is owner. */
    @Test fun reservationTargetsUseTheirActualEntrantRatherThanSelectedWorkspace() {
        ownerWorkspace()
        val own = previewReservation().copy(id = "own/A", entrantId = "account-1")
        val hosted = own.copy(id = "hosted/B", entrantId = "other-camper")
        host.bookings.records.value = listOf(own, hosted)
        val ownEvent =
            winner.copy(
                id = "own-booking",
                type = NotificationType.RESERVATION_CONFIRMED,
                subject = NotificationSubject(SubjectType.RESERVATION, own.id),
            )
        val hostedEvent =
            ownEvent.copy(
                id = "hosted-booking",
                subject = NotificationSubject(SubjectType.RESERVATION, hosted.id),
            )
        history(ownEvent, hostedEvent)
        host.notifications.markReadHandler = { _, _ -> Unit }
        start()
        compose.onNodeWithTag("inbox-event-${ownEvent.id}").performClick()
        compose.onNodeWithText(own.id).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("inbox-event-${hostedEvent.id}").performClick()
        compose.onNodeWithText(hosted.id).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    own.id to ReservationContext.ENTRANT,
                    hosted.id to ReservationContext.OWNER,
                ),
                host.reservationDetailRequests,
            )
            assertEquals(1, host.notifications.activeInboxObservers)
            assertEquals(1, host.inboxCreations)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Inbox") and hasClickAction()).assertIsSelected()
    }

    /** Both workspace entries share account receipt ownership rather than duplicate observers. */
    @Test fun switchingWorkspacesReusesTheSameAccountInboxModel() {
        ownerWorkspace()
        start()
        compose.onNodeWithContentDescription("Switch workspace").performClick()
        compose.onNodeWithText("Camper").performClick()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("No notifications yet.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.inboxCreations)
            assertEquals(1, host.notifications.activeInboxObservers)
            assertEquals(listOf("account-1"), host.notifications.inboxRequests)
        }
    }

    /**
     * Unsupported subjects remain readable without a dead link or implicit read
     * acknowledgement.
     */
    @Test fun unsupportedMessageHistoryHasNoClickableTarget() {
        val event =
            winner.copy(
                id = "message",
                type = NotificationType.NEW_MESSAGE,
                subject = NotificationSubject(SubjectType.MESSAGE, "thread"),
            )
        history(event)
        start()
        compose.onNodeWithTag("inbox-event-message").assertHasNoClickAction()
        compose
            .onNodeWithText(
                "This notification has no supported destination in this build.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Messages").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.notifications.readRequests.isEmpty()) }
    }

    /** An unknown-time receipt is a readable non-target and retries only its missing event. */
    @Test fun unavailableEventRetriesWithoutInventingADestinationOrAcknowledgement() {
        host.notifications.receipts.value =
            mapOf("account-1" to listOf(InboxReceipt("missing-event", null)))
        start()
        compose.onNodeWithText("Unavailable notifications").assertIsDisplayed()
        compose.onNodeWithText("Notification unavailable").assertIsDisplayed()
        compose.onNodeWithTag("inbox-receipt-missing-event").assertHasNoClickAction()
        compose.runOnIdle {
            host.lotteries.records.value = listOf(lottery)
            host.notifications.records.value = listOf(winner.copy(id = "missing-event"))
        }
        compose.onNodeWithText("Retry notification").performClick()
        compose.onNodeWithTag("inbox-event-missing-event").assertIsDisplayed()
        compose.onNodeWithTag("inbox-receipt-missing-event").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("missing-event", "missing-event"), host.notifications.getRequests)
            assertEquals(listOf("account-1"), host.notifications.inboxRequests)
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }

    /** Source failure cannot become empty history, and explicit retry replaces only the source. */
    @Test fun failedReceiptSourceCanRetryWithoutRebuildingItsOwner() {
        host.notifications.inboxFailure.value = IOException("offline")
        start()
        compose.onNodeWithText("Could not refresh notifications.").assertIsDisplayed()
        compose.onNodeWithText("No notifications yet.").assertDoesNotExist()
        compose.runOnIdle { host.notifications.inboxFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No notifications yet.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.inboxCreations)
            assertEquals(listOf("account-1", "account-1"), host.notifications.inboxRequests)
            assertEquals(1, host.notifications.activeInboxObservers)
        }
    }

    /** Installs original immutable events and only this account's actual receipts.
     * @param events Existing event facts; arrival alone must be passive.
     */
    private fun history(vararg events: Notification) {
        host.notifications.records.value = events.toList()
        host.notifications.receipts.value =
            mapOf("account-1" to events.map { InboxReceipt(it.id, null) })
    }

    /** Selects an already granted owner capability without onboarding or new identity. */
    private fun ownerWorkspace() {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
    }

    /** Starts production navigation and selects its registered Inbox primary.
     * @param restoration Optional actual composition restoration host.
     */
    private fun start(restoration: StateRestorationTester? = null) {
        host.lotteries.entries.value =
            host.notifications.records.value
                .filter {
                    it.type == NotificationType.LOTTERY_WON ||
                        it.type == NotificationType.LOTTERY_LOST
                }.map { event ->
                    LotteryEntry(
                        event.subject.id,
                        "account-1",
                        if (event.type ==
                            NotificationType.LOTTERY_WON
                        ) {
                            EntryState.OFFERED
                        } else {
                            EntryState.LOST
                        },
                        now.minusSeconds(5400),
                        now.minusSeconds(1800),
                    )
                }
        if (restoration ==
            null
        ) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
        compose.onNodeWithText("Inbox").performClick()
    }
}

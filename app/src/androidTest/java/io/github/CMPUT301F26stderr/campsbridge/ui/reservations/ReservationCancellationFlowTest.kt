/*
 * Tests entrant cancellation, owner rejection and saved history through the production root and routes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Nine fixed actual-root interactions for shared cancellation/rejection and saved history. */
class ReservationCancellationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private var record = previewReservation().copy(id = "stay/A ?1", entrantId = "account-1")
    private var category = ReservationCategory.UPCOMING
    private val at = Instant.parse("2027-05-01T12:00:00Z")

    /** Retires each actual root/feature model after its fixed interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Both explicit Keep and real dialog Back dismiss without a write or recovery read. */
    @Test fun entrantPromptCanBeInspectedAndDismissedWithoutWriting() {
        start()
        request()
        compose.onNodeWithText("Cancel reservation?").assertIsDisplayed()
        compose.onNodeWithText("This win remains used.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reason (optional)").performTextReplacement("Changed plans")
        closeSoftKeyboard()
        dialogAction("Keep reservation").performClick()
        request()
        compose.onNodeWithText("Cancel reservation?").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("Cancel reservation?").assertDoesNotExist()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(host.bookings.cancellationRequests.isEmpty())
            assertTrue(host.bookings.getRequests.isEmpty())
        }
    }

    /** Entrant success uses saved refund facts and returns to the updated actual Active list. */
    @Test fun entrantCancellationUpdatesSavedRefundAndActiveList() {
        succeed()
        start()
        request()
        dialogAction("Cancel reservation").performClick()
        compose.onNodeWithText("Cancelled by camper").assertIsDisplayed()
        open("Payment & refund")
        compose.onNodeWithText("Full simulated refund").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amount refunded").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No active reservations.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(CancellationRequest(record.id, "account-1", Role.ENTRANT)),
                host.bookings.cancellationRequests,
            )
            assertEquals(1, host.reservationDetailCreations)
            assertEquals(listOf(record.id), host.bookings.getRequests)
        }
    }

    /** Owner confirmation identifies the entrant and saves the actual role with trimmed input. */
    @Test fun ownerRejectionRetainsActualAttributionAndReason() {
        succeed(Role.OWNER, "Closure")
        start(owner = true)
        request(owner = true)
        compose.onNodeWithText("Reject reservation?").assertIsDisplayed()
        compose.onNodeWithText(record.entrantId, substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reason (optional)").performTextReplacement("  Closure  ")
        closeSoftKeyboard()
        dialogAction("Reject reservation").performClick()
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        compose.onNodeWithText("Closure").performScrollTo().assertIsDisplayed()
        open("Payment & refund")
        compose.onNodeWithText("Full simulated refund").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(CancellationRequest(record.id, "account-1", Role.OWNER, "Closure")),
                host.bookings.cancellationRequests,
            )
        }
    }

    /** Pending acknowledgement blocks edits/dismissal/Back and never replays on restoration. */
    @Test fun heldConfirmationStaysGuardedThroughRestoration() {
        val held = CompletableDeferred<Unit>()
        host.bookings.cancelHandler = {
            held.await()
            saveCancelled()
        }
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        request()
        compose.onNodeWithText("Reason (optional)").performTextReplacement("Original")
        closeSoftKeyboard()
        dialogAction("Cancel reservation").performClick()
        dialogAction("Cancel reservation").assertIsNotEnabled()
        dialogAction("Keep reservation").assertIsNotEnabled()
        compose.onNodeWithText("Reason (optional)").assertIsNotEnabled()
        compose.onNodeWithText("Cancelling reservation…").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("Cancel reservation?").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Cancelling reservation…").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.bookings.cancellationRequests.size) }
        compose.runOnIdle { held.complete(Unit) }
        compose.onNodeWithText("Cancelled by camper").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.bookings.cancellationRequests.size) }
    }

    /** Known rejection retains editable input; only another explicit confirmation retries. */
    @Test fun typedFailurePreservesReasonForExplicitRetry() {
        host.bookings.cancelHandler =
            { throw PersistenceFailure(PersistenceFailure.Reason.NOT_ALLOWED, "Denied") }
        start()
        request()
        compose.onNodeWithText("Reason (optional)").performTextReplacement("Plans changed")
        closeSoftKeyboard()
        dialogAction("Cancel reservation").performClick()
        compose.onNodeWithText("You cannot change this reservation.").assertIsDisplayed()
        compose.onNodeWithText("Reason (optional)").assertTextContains("Plans changed")
        dialogAction("Cancel reservation").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(1, host.bookings.cancellationRequests.size)
            succeed(reason = "Plans changed")
        }
        dialogAction("Cancel reservation").performClick()
        compose.onNodeWithText("Cancelled by camper").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, host.bookings.cancellationRequests.size) }
    }

    /** Unknown feedback stays reachable despite source failure; only Check performs recovery. */
    @Test fun uncertainReadHasIndependentFeedbackAndReadOnlyRecovery() {
        host.bookings.cancelHandler = {
            host.bookings.recordFailure.value = IOException("observe offline")
            throw IOException("ack lost")
        }
        host.bookings.getHandler = { throw IOException("server offline") }
        start()
        request()
        dialogAction("Cancel reservation").performClick()
        compose.onNodeWithText("Check reservation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        pressSystemBack()
        compose.onNodeWithText("Check reservation").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.bookings.cancellationRequests.size)
            host.bookings.getHandler = { cancelled(Role.OWNER, "Actual closure") }
        }
        compose.onNodeWithText("Check reservation").performClick()
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        compose.onNodeWithText("Actual closure").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.bookings.cancellationRequests.size)
            assertEquals(2, host.bookings.getRequests.size)
        }
    }

    /** Confirmed noncommit retains input and never automatically retries the mutation. */
    @Test fun authoritativeUncancelledReadRequiresAnotherExplicitAttempt() {
        host.bookings.cancelHandler = { throw IOException("ack unknown") }
        start()
        request()
        compose.onNodeWithText("Reason (optional)").performTextReplacement("Reason")
        closeSoftKeyboard()
        dialogAction("Cancel reservation").performClick()
        compose
            .onNodeWithText(
                "The reservation is still active. You can try again.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Reason (optional)").assertTextContains("Reason")
        compose.runOnIdle {
            assertEquals(1, host.bookings.cancellationRequests.size)
            succeed(reason = "Reason")
        }
        dialogAction("Cancel reservation").performClick()
        compose.onNodeWithText("Cancelled by camper").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, host.bookings.cancellationRequests.size) }
    }

    /** Exact booked-local checkout and saved cancellation omit new cancellation/rejection actions. */
    @Test fun checkoutAndCancelledHistoryHaveNoNewActions() {
        record =
            record.copy(
                checkInDate = LocalDate.of(2027, 4, 30),
                checkOutDate = LocalDate.of(2027, 5, 1),
            )
        start()
        compose.onNodeWithText("Cancel reservation").assertDoesNotExist()
        compose.onNodeWithText("Reject reservation").assertDoesNotExist()
        compose.runOnIdle { host.bookings.records.value = listOf(cancelled(Role.OWNER)) }
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        compose.onNodeWithText("Cancel reservation").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.bookings.cancellationRequests.isEmpty()) }
    }

    /** Enlarged owner content retains reachable reason, consequence and nonmutating Keep. */
    @Test fun enlargedOwnerDialogKeepsItsInputAndActionsReachable() {
        start(owner = true, fontScale = 2f)
        request(owner = true)
        compose
            .onNodeWithText(
                "Reason (optional)",
            ).performScrollTo()
            .performTextReplacement("Long closure explanation")
        closeSoftKeyboard()
        compose
            .onNodeWithText(
                "Full simulated refund",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        dialogAction("Keep reservation").assertIsDisplayed().performClick()
        compose.onNodeWithText(record.id).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.bookings.cancellationRequests.isEmpty()) }
    }

    /** Hosts the actual account/list/saved graph with fixed helper results and saved fixtures.
     * @param owner Whether the actual workspace is owner.
     * @param restoration Optional composition/navigation restoration driver.
     * @param fontScale System text scale to exercise.
     */
    private fun start(
        owner: Boolean = false,
        restoration: StateRestorationTester? = null,
        fontScale: Float = 1f,
    ) {
        if (owner) {
            record = record.copy(entrantId = "camper-a")
            host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
            host.preferences.progress.value =
                host.preferences.progress.value
                    .copy(workspace = Role.OWNER)
            host.bookings.ownerHandler = { host.bookings.records.map { it } }
        }
        host.classifyReservation = { _, _ -> category }
        host.orderReservations = { it }
        host.bookings.records.value = listOf(record)
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                host.Content()
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithText("Reservations").performClick()
        compose.onNodeWithTag("reservation:${record.id}").performClick()
        compose.onNodeWithText(record.id).assertIsDisplayed()
    }

    /** Opens the actual eligible action on the saved overview.
     * @param owner Whether rejection wording is expected.
     */
    private fun request(owner: Boolean = false) {
        open(if (owner) "Reject reservation" else "Cancel reservation")
    }

    /** Scrolls and invokes an actual overview action.
     * @param label Exact visible action text.
     */
    private fun open(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    /** Finds only the modal action, excluding the underlying overview action.
     * @param label Exact shared dialog action text.
     */
    private fun dialogAction(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    /** Builds the actual-shaped saved cancellation with its nested simulated refund.
     * @param role Saved actor presentation.
     * @param reason Saved explanation.
     */
    private fun cancelled(
        role: Role = Role.ENTRANT,
        reason: String? = null,
    ): Reservation =
        record.copy(
            cancellation =
                ReservationCancellation(
                    at,
                    if (role ==
                        Role.OWNER
                    ) {
                        "account-1"
                    } else {
                        record.entrantId
                    },
                    role,
                    reason,
                ),
            payment = record.payment.copy(refundedAt = at),
        )

    /** Supplies a fixed classification outcome and the complete saved repository result.
     * @param role Saved cancellation actor.
     * @param reason Saved explanation.
     */
    private fun saveCancelled(
        role: Role = Role.ENTRANT,
        reason: String? = null,
    ) {
        category = ReservationCategory.CANCELLED
        host.bookings.records.value = listOf(cancelled(role, reason))
    }

    /** Configures an explicit successful repository result; unconfigured writes remain unsupported.
     * @param role Saved attribution.
     * @param reason Saved explanation.
     */
    private fun succeed(
        role: Role = Role.ENTRANT,
        reason: String? = null,
    ) {
        host.bookings.cancelHandler = { saveCancelled(role, reason) }
    }

    /** Sends real Back to the focused window, including a native confirmation dialog. */
    private fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
}

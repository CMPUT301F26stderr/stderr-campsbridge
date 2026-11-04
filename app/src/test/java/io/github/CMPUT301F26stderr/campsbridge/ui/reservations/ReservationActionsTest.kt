package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.ZoneId

/** Fixed cancellation coordination contracts; saved facts and atomic effects remain repository-owned. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationActionsTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val record = CampingFixtures.reservation()
    private val clock = MutableClock(CampingFixtures.now)
    private val bookings = FakeBookingRepository(listOf(record))

    /** An entrant can inspect/edit/dismiss a prompt without any write or server recovery read. */
    @Test fun entrantPromptAndDismissNeverWrite() =
        actionTest {
            val model = model()
            runCurrent()
            assertTrue(model.cancellation.value.canRequest)
            model.requestCancel()
            assertEquals(CancellationPrompt(""), model.cancellation.value.prompt)
            model.editReason("Plans changed")
            assertEquals(CancellationPrompt("Plans changed"), model.cancellation.value.prompt)
            model.dismissCancel()
            assertNull(model.cancellation.value.prompt)
            assertTrue(model.canLeave())
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertTrue(bookings.getRequests.isEmpty())
        }

    /** Owner rejection uses the same operation with its actual actor/role and optional reason. */
    @Test fun ownerConfirmFreezesTheActualActorRoleAndTrimmedReason() =
        actionTest {
            succeed(Role.OWNER, "Closure")
            val model = model(ReservationContext.OWNER)
            runCurrent()
            model.requestCancel()
            model.editReason("  Closure  ")
            model.confirmCancel()
            runCurrent()
            assertEquals(
                listOf(CancellationRequest(record.id, "owner", Role.OWNER, "Closure")),
                bookings.cancellationRequests,
            )
            assertEquals(Role.OWNER, content(model).cancellation?.initiatedAs)
        }

    /** Whitespace-only optional input becomes absent rather than an invented explanation. */
    @Test fun blankReasonIsNullInTheRequest() =
        actionTest {
            succeed()
            val model = model()
            runCurrent()
            model.requestCancel()
            model.editReason(" \n ")
            model.confirmCancel()
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
            assertNull(bookings.cancellationRequests.single().reason)
        }

    /** Cancelled history and exact booked-local checkout cannot open another action prompt. */
    @Test fun cancelledAndCheckoutStaysAreNotActionable() =
        actionTest {
            bookings.records.value = listOf(cancelled())
            val saved = model()
            runCurrent()
            saved.requestCancel()
            assertFalse(saved.cancellation.value.canRequest)
            assertNull(saved.cancellation.value.prompt)
            bookings.records.value = listOf(record)
            val cutoff =
                record.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(record.bookedCampsite.timeZone),
                    ).toInstant()
            clock.advance(Duration.between(clock.instant(), cutoff))
            val late = model(key = "late")
            runCurrent()
            late.requestCancel()
            assertFalse(late.cancellation.value.canRequest)
            assertNull(late.cancellation.value.prompt)
            assertTrue(bookings.cancellationRequests.isEmpty())
        }

    /** The synchronous guard blocks duplicate writes, dismissal, edits and navigation while held. */
    @Test fun heldCommandBlocksConflictingActionsAndOnlyWritesOnce() =
        actionTest {
            val held = CompletableDeferred<Unit>()
            bookings.cancelHandler = { held.await() }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.editReason("Original")
            model.confirmCancel()
            assertEquals(CancellationPhase.CANCELLING, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            runCurrent()
            model.confirmCancel()
            model.dismissCancel()
            model.editReason("Changed")
            model.requestCancel()
            model.retryCancellationRead()
            assertEquals(CancellationPrompt("Original"), model.cancellation.value.prompt)
            assertEquals(1, bookings.cancellationRequests.size)
            held.complete(Unit)
            runCurrent()
        }

    /** Primitive uncertainty and submitted reason are retained before the repository suspends. */
    @Test fun recoveryMarkerPrecedesIo() =
        actionTest {
            val saved = SavedStateHandle()
            bookings.cancelHandler = {
                assertEquals(true, saved.get<Boolean>("reservationCancellationRecovery"))
                assertEquals("Reason", saved.get<String>("reservationCancellationReason"))
            }
            val model = model(saved = saved)
            runCurrent()
            model.requestCancel()
            model.editReason("Reason")
            model.confirmCancel()
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
        }

    /** Acknowledgement is reconciled through the same complete saved ID, including actual refund. */
    @Test fun acknowledgedCancellationDisplaysTheSavedAggregate() =
        actionTest {
            succeed()
            val saved = SavedStateHandle()
            val model = model(saved = saved)
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(cancelled(), content(model))
            assertEquals(listOf(record.id), bookings.getRequests)
            assertEquals(CancellationPhase.IDLE, model.cancellation.value.phase)
            assertNull(model.cancellation.value.prompt)
            assertFalse(model.cancellation.value.canRequest)
            assertFalse(saved.get<Boolean>("reservationCancellationRecovery") == true)
            assertTrue(model.canLeave())
        }

    /** An observed cancellation cannot unlock a command still awaiting its acknowledgement. */
    @Test fun observedCancellationDoesNotReleaseAHeldAcknowledgement() =
        actionTest {
            val held = CompletableDeferred<Unit>()
            bookings.cancelHandler = {
                bookings.records.value = listOf(cancelled())
                held.await()
            }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(CancellationPhase.CANCELLING, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            model.confirmCancel()
            assertEquals(1, bookings.cancellationRequests.size)
            held.complete(Unit)
            runCurrent()
            assertEquals(CancellationPhase.IDLE, model.cancellation.value.phase)
            assertEquals(cancelled(), content(model))
        }

    /** A known rejection retains correctable input; only explicit Confirm retries the write. */
    @Test fun knownRejectionPreservesReasonAndRequiresAnExplicitRetry() =
        actionTest {
            bookings.cancelHandler =
                { throw PersistenceFailure(PersistenceFailure.Reason.NOT_ALLOWED, "Denied") }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.editReason("Plans changed")
            model.confirmCancel()
            runCurrent()
            assertEquals(PersistenceFailure.Reason.NOT_ALLOWED, model.cancellation.value.failure)
            assertEquals(CancellationPrompt("Plans changed"), model.cancellation.value.prompt)
            assertTrue(model.canLeave())
            assertEquals(1, bookings.cancellationRequests.size)
            assertTrue(bookings.getRequests.isEmpty())
            succeed(reason = "Plans changed")
            model.confirmCancel()
            runCurrent()
            assertEquals(2, bookings.cancellationRequests.size)
            assertCancellationComplete(model)
        }

    /** A lost response resolves actual saved attribution/refund without issuing another cancel. */
    @Test fun lostAcknowledgementFindsTheSavedCancellation() =
        actionTest {
            bookings.cancelHandler = {
                bookings.records.value = listOf(cancelled(Role.OWNER, "Actual owner closure"))
                throw IOException("ack lost")
            }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(cancelled(Role.OWNER, "Actual owner closure"), content(model))
            assertEquals(1, bookings.cancellationRequests.size)
            assertCancellationComplete(model)
        }

    /** An authoritative uncancelled read permits another explicit attempt, never an automatic one. */
    @Test fun confirmedUncancelledRecoveryRetainsReasonWithoutReplaying() =
        actionTest {
            bookings.cancelHandler = { throw IOException("ack unknown") }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.editReason("Reason")
            model.confirmCancel()
            runCurrent()
            assertEquals(CancellationPhase.IDLE, model.cancellation.value.phase)
            assertTrue(model.cancellation.value.notCancelled)
            assertEquals(CancellationPrompt("Reason"), model.cancellation.value.prompt)
            assertEquals(record, content(model))
            assertEquals(1, bookings.cancellationRequests.size)
            model.refreshTime()
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
        }

    /** Failed server reads keep uncertainty blocked until an explicit read-only retry succeeds. */
    @Test fun unknownRecoveryBlocksExitsAndRetriesOnlyTheRead() =
        actionTest {
            bookings.cancelHandler = { throw IOException("ack unknown") }
            bookings.getHandler = { throw IOException("read offline") }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            model.dismissCancel()
            model.confirmCancel()
            assertEquals(1, bookings.cancellationRequests.size)
            bookings.getHandler = { cancelled() }
            model.retryCancellationRead()
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
            assertEquals(2, bookings.getRequests.size)
            assertEquals(cancelled(), content(model))
            assertCancellationComplete(model)
        }

    /** A held recovery has one read guard and never re-enters the cancellation operation. */
    @Test fun heldRecoveryRejectsDuplicateReadAndWriteActions() =
        actionTest {
            val held = CompletableDeferred<Reservation?>()
            bookings.cancelHandler = { throw IOException("ack unknown") }
            bookings.getHandler = { held.await() }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(CancellationPhase.RECOVERING, model.cancellation.value.phase)
            model.retryCancellationRead()
            model.confirmCancel()
            model.dismissCancel()
            assertFalse(model.canLeave())
            assertEquals(1, bookings.getRequests.size)
            held.complete(cancelled())
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
        }

    /** Recreated uncertainty resolves by fixed identity even when ordinary observation fails. */
    @Test fun recreationRecoversByIdentityWithoutReplayingTheCommand() =
        actionTest {
            bookings.recordFailure.value = IOException("observe offline")
            bookings.getHandler = { cancelled() }
            val saved =
                SavedStateHandle(
                    mapOf(
                        "reservationCancellationRecovery" to true,
                        "reservationCancellationReason" to "Original",
                    ),
                )
            val model = model(saved = saved)
            runCurrent()
            assertEquals(listOf(record.id), bookings.getRequests)
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertEquals(cancelled(), content(model))
            assertCancellationComplete(model)
            assertFalse(saved.get<Boolean>("reservationCancellationRecovery") == true)
        }

    /** Wrong identity or unrelated entrant data cannot confirm a saved result for this caller. */
    @Test fun mismatchedRecoveryRecordRemainsUnknown() =
        actionTest {
            bookings.getHandler = { cancelled().copy(id = "different") }
            val model =
                model(saved = SavedStateHandle(mapOf("reservationCancellationRecovery" to true)))
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            bookings.getHandler = { cancelled().copy(entrantId = "different") }
            model.retryCancellationRead()
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertTrue(bookings.cancellationRequests.isEmpty())
        }

    /** Repository cancellation propagates and leaves a recoverable marker rather than busy progress. */
    @Test fun interruptedActiveCommandBecomesReadRecoverable() =
        actionTest {
            bookings.cancelHandler = { throw CancellationException("interrupted") }
            val saved = SavedStateHandle()
            val model = model(saved = saved)
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertEquals(true, saved.get<Boolean>("reservationCancellationRecovery"))
            bookings.getHandler = { cancelled() }
            model.retryCancellationRead()
            runCurrent()
            assertEquals(cancelled(), content(model))
            assertEquals(1, bookings.cancellationRequests.size)
        }

    /** Graph retirement rejects a noncooperative late command and starts no recovery afterward. */
    @Test fun clearingTheParentRetiresLateCommandResults() =
        actionTest {
            val held = CompletableDeferred<Unit>()
            bookings.cancelHandler = { withContext(NonCancellable) { held.await() } }
            val model = model()
            runCurrent()
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(1, bookings.cancellationRequests.size)
            val before = model.state.value
            val action = model.cancellation.value
            store.clear()
            held.complete(Unit)
            runCurrent()
            assertEquals(before, model.state.value)
            assertEquals(action, model.cancellation.value)
            assertTrue(bookings.getRequests.isEmpty())
        }

    /** A prompt cannot submit after the booked-local cutoff passes while it is open. */
    @Test fun confirmRechecksTheCurrentCheckoutBoundary() =
        actionTest {
            val model = model()
            runCurrent()
            model.requestCancel()
            val cutoff =
                record.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(record.bookedCampsite.timeZone),
                    ).toInstant()
            clock.advance(Duration.between(clock.instant(), cutoff))
            model.confirmCancel()
            runCurrent()
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertFalse(model.cancellation.value.canRequest)
            assertNull(model.cancellation.value.prompt)
        }

    /** An externally confirmed cancellation retires an idle prompt without a duplicate write. */
    @Test fun observedCancellationRetiresTheIdlePrompt() =
        actionTest {
            val model = model()
            runCurrent()
            model.requestCancel()
            bookings.records.value = listOf(cancelled(Role.OWNER))
            runCurrent()
            assertNull(model.cancellation.value.prompt)
            assertFalse(model.cancellation.value.canRequest)
            model.confirmCancel()
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertEquals(cancelled(Role.OWNER), content(model))
        }

    /** Unknown required source facts cannot authorize a new write from an old prompt. */
    @Test fun failedSourceCannotConfirmTheRetainedPrompt() =
        actionTest {
            val model = model()
            runCurrent()
            model.requestCancel()
            bookings.recordFailure.value = IOException("observe offline")
            runCurrent()
            model.confirmCancel()
            assertEquals(ReservationDetailUiState.Failure, model.state.value)
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertTrue(model.canLeave())
        }

    /** Missing history is unknown, not proof of an uncancelled target or completed refund. */
    @Test fun absentRecoveryCannotReleaseUncertainty() =
        actionTest {
            bookings.getHandler = { null }
            val model =
                model(saved = SavedStateHandle(mapOf("reservationCancellationRecovery" to true)))
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            assertFalse(model.cancellation.value.notCancelled)
            assertTrue(bookings.cancellationRequests.isEmpty())
        }

    /** One booked-local rollover and foreground refresh update eligibility without writes. */
    @Test fun bookedMidnightAndForegroundRefreshUpdateEligibility() =
        actionTest {
            val cutoff =
                record.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(record.bookedCampsite.timeZone),
                    ).toInstant()
            clock.advance(Duration.between(clock.instant(), cutoff.minusSeconds(1)))
            val model = model()
            runCurrent()
            assertTrue(model.cancellation.value.canRequest)
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
            assertFalse(model.cancellation.value.canRequest)
            model.refreshTime()
            assertFalse(model.cancellation.value.canRequest)
            assertTrue(bookings.cancellationRequests.isEmpty())
        }

    /** A stay remains cancellable during arrival day until its booked-local checkout. */
    @Test fun inProgressStayCanStillBeCancelled() =
        actionTest {
            val arrival = record.checkInDate.atStartOfDay(ZoneId.of(record.bookedCampsite.timeZone)).toInstant()
            clock.advance(Duration.between(clock.instant(), arrival))
            succeed()
            val model = model()
            runCurrent()
            assertTrue(model.cancellation.value.canRequest)
            model.requestCancel()
            model.confirmCancel()
            runCurrent()
            assertEquals(
                listOf(CancellationRequest(record.id, record.entrantId, Role.ENTRANT)),
                bookings.cancellationRequests,
            )
            assertCancellationComplete(model)
        }

    /** Retiring the saved parent also retires a noncooperative server recovery read. */
    @Test fun clearingTheParentRetiresLateRecoveryResults() =
        actionTest {
            val held = CompletableDeferred<Reservation?>()
            bookings.getHandler = { withContext(NonCancellable) { held.await() } }
            val model = model(saved = SavedStateHandle(mapOf("reservationCancellationRecovery" to true)))
            runCurrent()
            assertEquals(CancellationPhase.RECOVERING, model.cancellation.value.phase)
            val before = model.state.value
            val action = model.cancellation.value
            store.clear()
            held.complete(cancelled())
            runCurrent()
            assertEquals(before, model.state.value)
            assertEquals(action, model.cancellation.value)
            assertTrue(bookings.cancellationRequests.isEmpty())
        }

    /** Coroutine interruption keeps recovery available without replaying the mutation. */
    @Test fun interruptedRecoveryCanRetryOnlyTheRead() =
        actionTest {
            bookings.getHandler = { throw CancellationException("read interrupted") }
            val model = model(saved = SavedStateHandle(mapOf("reservationCancellationRecovery" to true)))
            runCurrent()
            assertEquals(CancellationPhase.UNKNOWN, model.cancellation.value.phase)
            assertFalse(model.canLeave())
            bookings.getHandler = { cancelled() }
            model.retryCancellationRead()
            runCurrent()
            assertEquals(2, bookings.getRequests.size)
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertCancellationComplete(model)
        }

    /** Supplies the actual saved cancellation/refund fixture instead of a client-side status flip.
     * @param role Actual attribution retained by the saved aggregate.
     * @param reason Optional saved explanation.
     */
    private fun cancelled(
        role: Role = Role.ENTRANT,
        reason: String? = null,
    ): Reservation =
        record.copy(
            cancellation =
                ReservationCancellation(
                    clock.instant(),
                    if (role ==
                        Role.OWNER
                    ) {
                        "owner"
                    } else {
                        record.entrantId
                    },
                    role,
                    reason,
                ),
            payment = record.payment.copy(refundedAt = clock.instant()),
        )

    /** Configures a deliberate repository outcome, with complete saved facts for the later read.
     * @param role Attribution saved by this fixture.
     * @param reason Saved optional explanation.
     */
    private fun succeed(
        role: Role = Role.ENTRANT,
        reason: String? = null,
    ) {
        bookings.cancelHandler = { bookings.records.value = listOf(cancelled(role, reason)) }
    }

    /** Checks completed saved cancellation can no longer open another action.
     * @param model Actual identity-scoped saved owner.
     */
    private fun assertCancellationComplete(model: ReservationDetailViewModel) {
        assertEquals(CancellationPhase.IDLE, model.cancellation.value.phase)
        assertNull(model.cancellation.value.prompt)
        assertFalse(model.cancellation.value.canRequest)
        assertTrue(model.canLeave())
    }

    /** Reads the real-shaped complete aggregate after verifying a confirmed Content outcome.
     * @param model Actual saved identity's owner.
     */
    private fun content(model: ReservationDetailViewModel): Reservation {
        assertTrue(
            "Expected Content: ${model.state.value}",
            model.state.value is ReservationDetailUiState.Content,
        )
        return (model.state.value as ReservationDetailUiState.Content).reservation
    }

    /** Creates a retained parent with explicit clock, identity/context and primitive restoration.
     * @param context Actual caller workspace.
     * @param saved Small command uncertainty state.
     * @param key Independent retained parent identity for this case.
     */
    private fun model(
        context: ReservationContext = ReservationContext.ENTRANT,
        saved: SavedStateHandle = SavedStateHandle(),
        key: String = "detail",
    ): ReservationDetailViewModel =
        ReservationDetailViewModel(
            if (context == ReservationContext.OWNER) "owner" else record.entrantId,
            record.id,
            context,
            bookings,
            clock,
            saved,
        ).also { store.put(key, it) }

    /** Releases every reader/command/timer even when a fixed contract assertion fails.
     * @param block Exact controlled scenario from the frozen module checklist.
     */
    private fun actionTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
            }
        }
}

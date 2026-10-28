package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Saved history has one cancellable read owner and no current-listing or payment dependency. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val record = CampingFixtures.reservation()
    private val bookings = FakeBookingRepository(listOf(record))

    /** Retires every created parent and flushes cancellation cleanup. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
    }

    /** Initial loading cannot become false empty content or trigger a second read on retry. */
    @Test fun heldFirstObservationHasOneActualSavedSource() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            bookings.observeHandler = {
                flow {
                    ready.await()
                    emit(record)
                }
            }
            val model = model()
            assertEquals(ReservationDetailUiState.Loading, model.state.value)
            runCurrent()
            model.retry()
            runCurrent()
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertEquals(1, bookings.activeRecordObservers)
            assertEquals(ReservationDetailUiState.Loading, model.state.value)
            ready.complete(Unit)
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
        }

    /** Overview/payment/terms derive from the exact same complete historical record. */
    @Test fun savedSnapshotAndNestedFactsAreNotReplacedWithCurrentData() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertTrue(bookings.getRequests.isEmpty())
            assertTrue(bookings.entrantRequests.isEmpty())
            assertTrue(bookings.opportunityRequests.isEmpty())
            model.retry()
            runCurrent()
            assertEquals(listOf(record.id), bookings.observedRecordIds)
        }

    /** Owner presentation reads the booked record without confusing the entrant with the owner. */
    @Test fun ownerContextDoesNotRequireTheOwnerToBeTheEntrant() =
        runTest {
            val model = model("owner", ReservationContext.OWNER)
            runCurrent()
            assertEquals(ReservationContext.OWNER, model.context)
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
        }

    /** Own entrant presentation never substitutes another account's reservation. */
    @Test fun unrelatedEntrantIsUnavailableAndCanRecoverOnActualObservation() =
        runTest {
            val model = model("different-entrant")
            runCurrent()
            assertEquals(ReservationDetailUiState.Unavailable, model.state.value)
            bookings.records.value = listOf(record.copy(entrantId = "different-entrant"))
            runCurrent()
            assertEquals(
                ReservationDetailUiState.Content(bookings.records.value.single()),
                model.state.value,
            )
            assertEquals(1, bookings.activeRecordObservers)
        }

    /** Confirmed absence remains observed, and reappearance uses the original identity. */
    @Test fun missingRecordIsNotAFailureAndReappearanceUsesOneSource() =
        runTest {
            bookings.records.value = emptyList()
            val model = model()
            runCurrent()
            assertEquals(ReservationDetailUiState.Unavailable, model.state.value)
            model.retry()
            runCurrent()
            bookings.records.value = listOf(record)
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
        }

    /** An inconsistent source cannot silently render another saved ID. */
    @Test fun mismatchedRecordIsUnavailable() =
        runTest {
            bookings.observeHandler = { flow { emit(record.copy(id = "wrong-id")) } }
            val model = model()
            runCurrent()
            assertEquals(ReservationDetailUiState.Unavailable, model.state.value)
        }

    /** Unknown policy history stays exact, including saved rules and acceptance time. */
    @Test fun unbundledPolicyVersionIsRetainedWithoutCurrentVersionFallback() =
        runTest {
            val historical =
                record.copy(
                    acceptedTerms =
                        record.acceptedTerms.copy(
                            rules = "Historical rules\nKeep both lines.",
                            cancellationPolicyVersion = "retired-refund-v0",
                        ),
                )
            bookings.records.value = listOf(historical)
            val model = model()
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(historical), model.state.value)
        }

    /** Transport failure stays distinct from absent data; repeated retry coalesces. */
    @Test fun failedSourceCanBeRetriedExactlyOnce() =
        runTest {
            bookings.recordFailure.value = IOException("saved record offline")
            val model = model()
            runCurrent()
            assertEquals(ReservationDetailUiState.Failure, model.state.value)
            assertEquals(0, bookings.activeRecordObservers)
            bookings.recordFailure.value = null
            model.retry()
            model.retry()
            assertEquals(ReservationDetailUiState.Loading, model.state.value)
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
            assertEquals(listOf(record.id, record.id), bookings.observedRecordIds)
            assertEquals(1, bookings.activeRecordObservers)
        }

    /**
     * A live failure removes current-content status, and retry restores the same full
     * aggregate.
     */
    @Test fun failureAfterContentDoesNotPretendTheReadIsStillCurrent() =
        runTest {
            val model = model()
            runCurrent()
            bookings.recordFailure.value = IOException("disconnected")
            runCurrent()
            assertEquals(ReservationDetailUiState.Failure, model.state.value)
            bookings.recordFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(record), model.state.value)
        }

    /** Cancellation and refund arrive atomically as one observed history update. */
    @Test fun cancellationAndRefundUseTheExistingAggregateObserver() =
        runTest {
            val model = model()
            runCurrent()
            val cancelled =
                record.copy(
                    cancellation =
                        ReservationCancellation(
                            CampingFixtures.now.plusSeconds(30),
                            "owner",
                            Role.OWNER,
                            "Closure",
                        ),
                    payment =
                        record.payment.copy(
                            refundedAt = CampingFixtures.now.plusSeconds(30),
                        ),
                )
            bookings.records.value = listOf(cancelled)
            runCurrent()
            assertEquals(ReservationDetailUiState.Content(cancelled), model.state.value)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertTrue(bookings.getRequests.isEmpty())
        }

    /** Cancellation is propagated rather than converted into a transport error. */
    @Test fun cancelledReadDoesNotBecomeFailure() =
        runTest {
            bookings.observeHandler = { flow { throw CancellationException("retired") } }
            val model = model()
            runCurrent()
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertEquals(ReservationDetailUiState.Loading, model.state.value)
            assertEquals(0, bookings.activeRecordObservers)
        }

    /** A retired noncooperative source cannot republish content or restart via a stale callback. */
    @Test fun clearingParentRetiresLateReadAndRetry() =
        runTest {
            val ready = CompletableDeferred<Unit>()
            bookings.observeHandler =
                {
                    flow {
                        withContext(NonCancellable) { ready.await() }
                        emit(record)
                    }
                }
            val model = model()
            runCurrent()
            assertEquals(1, bookings.activeRecordObservers)
            store.clear()
            ready.complete(Unit)
            runCurrent()
            model.retry()
            runCurrent()
            assertEquals(ReservationDetailUiState.Loading, model.state.value)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertEquals(0, bookings.activeRecordObservers)
        }

    /** Creates one parent with explicit identity and presentation.
     * @param accountId Actor whose own entrant record may be shown.
     * @param context Caller-selected presentation, not an authorization substitute.
     */
    private fun model(
        accountId: String = record.entrantId,
        context: ReservationContext = ReservationContext.ENTRANT,
    ): ReservationDetailViewModel =
        ReservationDetailViewModel(accountId, record.id, context, bookings)
            .also { store.put("reader", it) }
}

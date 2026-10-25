package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration

/** Entrant detail keeps membership/consumption sources separate and masks unfinished draws. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDetailEntrantTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = CampingFixtures.now
    private val clock = MutableClock(now.plusSeconds(120))
    private val parent = CampingFixtures.lottery("draw", "owner")
    private val drawn = parent.copy(drawnAt = now.plusSeconds(120))
    private val lotteries = FakeLotteryRepository(listOf(parent))
    private val sites = FakeCampsiteRepository()
    private val bookings = FakeBookingRepository()
    private val reservation = CampingFixtures.reservation().copy(id = "saved-reservation")

    /** An entrant reads a published foreign-owner parent and queries only its own memberships. */
    @Test fun entrantReadsPublishedParentWithoutOwnerControlsOrGlobalMemberships() =
        withModel {
            runCurrent()
            assertEquals(parent, content(it).lottery)
            assertFalse(content(it).canContinue)
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                content(it).membership,
            )
            assertEquals(LotteryOpportunityRead.NotApplicable, content(it).opportunity)
            assertEquals(listOf("entrant"), lotteries.entrantRequests)
            assertTrue(lotteries.ownerRequests.isEmpty())
            assertTrue(bookings.opportunityRequests.isEmpty())
            assertTrue(bookings.entrantRequests.isEmpty())
        }

    /** Unpublished targets are unavailable; publication can reopen the same retained reader. */
    @Test fun unpublishedParentIsUnavailableUntilPublished() =
        withModel {
            lotteries.records.value = listOf(parent.copy(publishedAt = null))
            runCurrent()
            assertEquals(LotteryDetailUiState.Unavailable, it.state.value)
            assertEquals(0, lotteries.activeEntrantObservers)
            lotteries.records.value = listOf(parent)
            runCurrent()
            assertEquals(parent, content(it).lottery)
            assertEquals(1, lotteries.activeEntrantObservers)
        }

    /** Own-entry errors remain sectional, and retry cannot multiply successful parent readers. */
    @Test fun failedMembershipKeepsParentAndOffersAndRetriesOnce() =
        withModel {
            lotteries.entrantFailure.value = IOException("membership offline")
            runCurrent()
            assertEquals(parent, content(it).lottery)
            assertEquals(LotteryMembershipRead.Failure, content(it).membership)
            assertEquals(LotteryOffersState.Content(emptyList()), content(it).offers)
            lotteries.entrantFailure.value = null
            it.retryMembership()
            it.retryMembership()
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                content(it).membership,
            )
            assertEquals(2, lotteries.entrantRequests.size)
            assertEquals(1, lotteries.activeRecordObservers)
        }

    /** Partial winner/loser entries never reach public outcome state or trigger a used-win read. */
    @Test fun unfinishedDrawMasksBothOutcomesUntilParentCompletion() =
        withModel {
            for (state in listOf(EntryState.OFFERED, EntryState.LOST)) {
                lotteries.entries.value = listOf(entry(state))
                runCurrent()
                assertEquals(
                    LotteryMembershipRead.Content(LotteryMembershipStatus.RESULTS_PENDING),
                    content(it).membership,
                )
                assertEquals(LotteryOpportunityRead.NotApplicable, content(it).opportunity)
                assertTrue(bookings.opportunityRequests.isEmpty())
            }
            lotteries.records.value = listOf(drawn)
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.LOST),
                content(it).membership,
            )
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.WON),
                content(it).membership,
            )
            assertEquals(LotteryOpportunityRead.Unused, content(it).opportunity)
            assertEquals(listOf("draw" to "entrant"), bookings.opportunityRequests)
        }

    /** A found consuming reservation uses one live aggregate, including cancellation and refund. */
    @Test fun foundReservationRemainsConsumedAcrossLiveCancellation() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            assertEquals(LotteryOpportunityRead.Reserved(reservation), content(it).opportunity)
            assertEquals(listOf(reservation.id), bookings.observedRecordIds)
            val cancelled =
                reservation.copy(
                    cancellation =
                        ReservationCancellation(
                            clock.instant(),
                            "entrant",
                            Role.ENTRANT,
                            null,
                        ),
                    payment = reservation.payment.copy(refundedAt = clock.instant()),
                )
            bookings.records.value = listOf(cancelled)
            lotteries.records.value = listOf(drawn.copy(title = "Updated display title"))
            runCurrent()
            assertEquals(LotteryOpportunityRead.Reserved(cancelled), content(it).opportunity)
            assertEquals(1, bookings.opportunityRequests.size)
            assertEquals(1, bookings.activeRecordObservers)
            assertTrue(bookings.entrantRequests.isEmpty())
        }

    /** Held retries remain unknown and coalesce; a confirmed empty result alone means unused. */
    @Test fun failedLookupPreservesConfirmedWinAndCoalescesRetry() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.opportunityHandler = { _, _ -> throw IOException("server offline") }
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.WON),
                content(it).membership,
            )
            assertEquals(LotteryOpportunityRead.Failure, content(it).opportunity)
            val gate = CompletableDeferred<Reservation?>()
            try {
                bookings.opportunityHandler = { _, _ -> gate.await() }
                it.retryOpportunity()
                it.retryOpportunity()
                it.refreshTime()
                runCurrent()
                assertEquals(LotteryOpportunityRead.Loading, content(it).opportunity)
                assertEquals(2, bookings.opportunityRequests.size)
                gate.complete(null)
                runCurrent()
                assertEquals(LotteryOpportunityRead.Unused, content(it).opportunity)
            } finally {
                gate.complete(null)
            }
        }

    /** Foreground return can discover a just-saved stay even while the independent offers fail. */
    @Test fun foregroundReconcilesConsumptionWithoutDependingOnOffers() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            runCurrent()
            assertEquals(LotteryOpportunityRead.Unused, content(it).opportunity)
            bookings.records.value = listOf(reservation)
            lotteries.offerFailure.value = IOException("offers offline")
            runCurrent()
            it.refreshTime()
            runCurrent()
            assertEquals(LotteryOffersState.Failure, content(it).offers)
            assertEquals(LotteryOpportunityRead.Reserved(reservation), content(it).opportunity)
            assertEquals(2, bookings.opportunityRequests.size)
        }

    /** A missing live saved record stays unavailable history instead of releasing the win. */
    @Test fun missingObservedReservationNeverBecomesUnused() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            assertEquals(LotteryOpportunityRead.Reserved(reservation), content(it).opportunity)
            bookings.records.value = emptyList()
            runCurrent()
            assertEquals(
                LotteryOpportunityRead.Unavailable(reservation.id),
                content(it).opportunity,
            )
            assertEquals(1, bookings.opportunityRequests.size)
            it.refreshTime()
            runCurrent()
            assertEquals(
                LotteryOpportunityRead.Unavailable(reservation.id),
                content(it).opportunity,
            )
            assertEquals(1, bookings.opportunityRequests.size)
        }

    /** Foreground refresh keeps a healthy confirmed saved observer rather than replacing it. */
    @Test fun foregroundKeepsHealthyKnownReservationObservation() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            bookings.opportunityHandler = { _, _ -> throw IOException("a new lookup would fail") }
            it.refreshTime()
            runCurrent()
            assertEquals(LotteryOpportunityRead.Reserved(reservation), content(it).opportunity)
            assertEquals(1, bookings.opportunityRequests.size)
            assertEquals(1, bookings.observedRecordIds.size)
        }

    /** A failed live reader's retry cannot release an already confirmed consuming identity. */
    @Test fun recoveryAbsenceKeepsPreviouslyConfirmedConsumption() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            bookings.recordFailure.value = IOException("live read failed")
            runCurrent()
            assertEquals(LotteryOpportunityRead.Failure, content(it).opportunity)
            bookings.records.value = emptyList()
            bookings.recordFailure.value = null
            it.retryOpportunity()
            runCurrent()
            assertEquals(
                LotteryOpportunityRead.Unavailable(reservation.id),
                content(it).opportunity,
            )
            assertEquals(2, bookings.opportunityRequests.size)
        }

    /** A failed saved aggregate is unknown; explicit recovery replaces its reader once. */
    @Test fun failedObservedReservationCanReconcileWithoutLosingParent() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            bookings.recordFailure.value = IOException("live stay offline")
            runCurrent()
            assertEquals(LotteryOpportunityRead.Failure, content(it).opportunity)
            assertEquals(drawn, content(it).lottery)
            bookings.recordFailure.value = null
            it.retryOpportunity()
            it.retryOpportunity()
            runCurrent()
            assertEquals(LotteryOpportunityRead.Reserved(reservation), content(it).opportunity)
            assertEquals(1, bookings.activeRecordObservers)
            assertEquals(2, bookings.opportunityRequests.size)
        }

    /** A late noncooperative lookup cannot resurrect children after the parent is unavailable. */
    @Test fun removedParentRejectsLateLookupAndReleasesMemberships() =
        withModel {
            val gate = CompletableDeferred<Reservation?>()
            try {
                lotteries.records.value = listOf(drawn)
                lotteries.entries.value = listOf(entry(EntryState.OFFERED))
                bookings.opportunityHandler =
                    { _, _ -> withContext(NonCancellable) { gate.await() } }
                runCurrent()
                assertEquals(LotteryOpportunityRead.Loading, content(it).opportunity)
                lotteries.records.value = emptyList()
                runCurrent()
                gate.complete(reservation)
                runCurrent()
                assertEquals(LotteryDetailUiState.Unavailable, it.state.value)
                assertEquals(0, lotteries.activeEntrantObservers)
                assertEquals(0, bookings.activeRecordObservers)
            } finally {
                gate.complete(null)
            }
        }

    /** Own membership removal retires only the linked opportunity, preserving the saved parent. */
    @Test fun removedMembershipRetiresTheConsumingRecordReader() =
        withModel {
            lotteries.records.value = listOf(drawn)
            lotteries.entries.value = listOf(entry(EntryState.OFFERED))
            bookings.records.value = listOf(reservation)
            runCurrent()
            assertEquals(1, bookings.activeRecordObservers)
            lotteries.entries.value = emptyList()
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                content(it).membership,
            )
            assertEquals(LotteryOpportunityRead.NotApplicable, content(it).opportunity)
            assertEquals(0, bookings.activeRecordObservers)
            assertEquals(1, lotteries.activeRecordObservers)
            assertEquals(1, lotteries.activeEntrantObservers)
        }

    /** Waiting becomes pending at exact close, without fabricating a winner or reading bookings. */
    @Test fun registrationDeadlineRefreshesMembershipWithoutDrawing() =
        withModel {
            clock.advance(Duration.ofSeconds(-60))
            lotteries.entries.value = listOf(entry(EntryState.WAITING))
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.WAITING),
                content(it).membership,
            )
            clock.advance(Duration.ofSeconds(60))
            advanceTimeBy(60000)
            runCurrent()
            assertEquals(
                LotteryMembershipRead.Content(LotteryMembershipStatus.RESULTS_PENDING),
                content(it).membership,
            )
            assertTrue(bookings.opportunityRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Runs one real reader and closes it before virtual-time draining.
     * @param block Public state and controlled-source assertions.
     */
    private fun withModel(block: suspend TestScope.(LotteryDetailViewModel) -> Unit) =
        runTest {
            val store = ViewModelStore()
            try {
                val model =
                    LotteryDetailViewModel(
                        "entrant",
                        "draw",
                        lotteries,
                        sites,
                        clock,
                        bookings,
                        LotteryContext.ENTRANT,
                    )
                store.put("detail", model)
                block(model)
            } finally {
                store.clear()
                runCurrent()
                assertEquals(0, lotteries.activeRecordObservers)
                assertEquals(0, lotteries.activeOfferObservers)
                assertEquals(0, lotteries.activeEntrantObservers)
                assertEquals(0, bookings.activeRecordObservers)
            }
        }

    /** Requires a confirmed readable parent before asserting independent sections.
     * @param model Actual entrant reader.
     */
    private fun content(model: LotteryDetailViewModel): LotteryDetailUiState.Content {
        assertTrue(
            "Expected parent content, got ${model.state.value}",
            model.state.value is LotteryDetailUiState.Content,
        )
        return model.state.value as LotteryDetailUiState.Content
    }

    /** Supplies a current own membership without deriving a displayed draw outcome.
     * @param state Stored membership state.
     */
    private fun entry(state: EntryState): LotteryEntry =
        LotteryEntry("draw", "entrant", state, now.plusSeconds(60), clock.instant())
}

package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** One frozen attempt has explicit acknowledgement, rejection and read-only recovery outcomes. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingWriterTest {
    private val record = CampingFixtures.reservation()
    private val request =
        BookingRequest(
            record.lotteryId,
            record.entrantId,
            record.campsiteId,
            record.checkInDate,
            record.checkOutDate,
            3,
            13000,
            record.acceptedTerms.rules,
            BookingPolicy.VERSION,
        )

    /** Acknowledgement supplies the identity without another history read. */
    @Test fun forwardsExactRequestOnceAndReturnsAcknowledgedId() =
        runTest {
            val bookings = FakeBookingRepository().apply { reserveHandler = { "saved/A ?1" } }
            var recovering = 0
            val result = BookingWriter(bookings).submit(request) { recovering++ }
            assertEquals(BookingWriteResult.Saved("saved/A ?1"), result)
            assertEquals(listOf(request), bookings.reserveRequests)
            assertEquals(emptyList<Pair<String, String>>(), bookings.opportunityRequests)
            assertEquals(0, recovering)
        }

    /** Expected rejection preserves its reason without another mutation. */
    @Test fun everyKnownRejectionExceptConsumedWinReturnsItsActualReason() =
        runTest {
            for (reason in PersistenceFailure.Reason.entries.filterNot {
                it == PersistenceFailure.Reason.ALREADY_USED
            }) {
                val bookings =
                    FakeBookingRepository().apply {
                        reserveHandler = { throw PersistenceFailure(reason, "Rejected") }
                    }
                var recovering = 0
                assertEquals(
                    BookingWriteResult.Rejected(reason),
                    BookingWriter(bookings).submit(request) { recovering++ },
                )
                assertEquals(listOf(request), bookings.reserveRequests)
                assertTrue(bookings.opportunityRequests.isEmpty())
                assertEquals(0, recovering)
            }
        }

    /** An already-consumed response opens actual retained history without replaying reserve. */
    @Test fun alreadyUsedRecoversTheExistingIdentity() =
        runTest {
            val bookings =
                FakeBookingRepository(listOf(record)).apply {
                    reserveHandler = {
                        throw PersistenceFailure(PersistenceFailure.Reason.ALREADY_USED, "Used")
                    }
                }
            var recovering = 0
            assertEquals(
                BookingWriteResult.Saved(record.id),
                BookingWriter(bookings).submit(request) { recovering++ },
            )
            assertEquals(1, recovering)
            assertEquals(listOf(request), bookings.reserveRequests)
            assertEquals(
                listOf(request.lotteryId to request.entrantId),
                bookings.opportunityRequests,
            )
        }

    /** Lost acknowledgement resolves a saved result through one read after the phase callback. */
    @Test fun transportFailureRecoversSavedHistoryAfterReportingRecovery() =
        runTest {
            for (failure in listOf(
                IOException("Lost acknowledgement"),
                IllegalArgumentException("Decode"),
            )) {
                val phases = mutableListOf<String>()
                val bookings =
                    FakeBookingRepository().apply {
                        reserveHandler = {
                            phases.add("reserve")
                            throw failure
                        }
                        opportunityHandler = { _, _ ->
                            phases.add("lookup")
                            record
                        }
                    }
                assertEquals(
                    BookingWriteResult.Saved(record.id),
                    BookingWriter(bookings).submit(request) { phases.add("recovering") },
                )
                assertEquals(listOf("reserve", "recovering", "lookup"), phases)
                assertEquals(1, bookings.reserveRequests.size)
            }
        }

    /** A confirmed absent result permits only a caller-controlled, revalidated explicit retry. */
    @Test fun transportFailureWithConfirmedAbsenceDoesNotReplayTheRequest() =
        runTest {
            val bookings =
                FakeBookingRepository().apply {
                    reserveHandler = { throw IOException("No acknowledgement") }
                }
            assertEquals(BookingWriteResult.Absent, BookingWriter(bookings).submit(request))
            assertEquals(listOf(request), bookings.reserveRequests)
            assertEquals(
                listOf(request.lotteryId to request.entrantId),
                bookings.opportunityRequests,
            )
        }

    /** A failed recovery is still uncertain, even when the local fake contains no record. */
    @Test fun failedRecoveryNeverClaimsAbsenceOrRetriesReserve() =
        runTest {
            val bookings =
                FakeBookingRepository().apply {
                    reserveHandler = { throw IOException("No acknowledgement") }
                    opportunityHandler = { _, _ -> throw IOException("No server confirmation") }
                }
            assertEquals(BookingWriteResult.Unknown, BookingWriter(bookings).submit(request))
            assertEquals(listOf(request), bookings.reserveRequests)
            assertEquals(
                listOf(request.lotteryId to request.entrantId),
                bookings.opportunityRequests,
            )
        }

    /** An invalid acknowledged ID requires facts instead of a made-up saved destination. */
    @Test fun blankAcknowledgementUsesReadOnlyRecovery() =
        runTest {
            for (id in listOf("", "  ")) {
                val bookings =
                    FakeBookingRepository(
                        listOf(record),
                    ).apply { reserveHandler = { id } }
                assertEquals(
                    BookingWriteResult.Saved(record.id),
                    BookingWriter(bookings).submit(request),
                )
                assertEquals(1, bookings.reserveRequests.size)
                assertEquals(1, bookings.opportunityRequests.size)
            }
        }

    /** Restoring an uncertain marker needs only identities and may never reserve automatically. */
    @Test fun standaloneRecoveryReadsActualFoundAndAbsentWithoutAnyWrite() =
        runTest {
            val bookings = FakeBookingRepository(listOf(record))
            val writer = BookingWriter(bookings)
            assertEquals(
                BookingWriteResult.Saved(record.id),
                writer.recover(request.lotteryId, request.entrantId),
            )
            bookings.records.value = emptyList()
            assertEquals(
                BookingWriteResult.Absent,
                writer.recover(request.lotteryId, request.entrantId),
            )
            assertTrue(bookings.reserveRequests.isEmpty())
            assertEquals(
                List(2) { request.lotteryId to request.entrantId },
                bookings.opportunityRequests,
            )
        }

    /** Cancellation/refund changes history presentation but does not free an already-used win. */
    @Test fun cancelledHistoryStillReturnsTheActualSavedIdentity() =
        runTest {
            val cancelled =
                record.copy(
                    cancellation =
                        ReservationCancellation(
                            CampingFixtures.now,
                            "owner",
                            Role.OWNER,
                            null,
                        ),
                    payment = record.payment.copy(refundedAt = CampingFixtures.now),
                )
            val bookings = FakeBookingRepository(listOf(cancelled))
            assertEquals(
                BookingWriteResult.Saved(record.id),
                BookingWriter(bookings).recover(request.lotteryId, request.entrantId),
            )
            assertTrue(bookings.reserveRequests.isEmpty())
        }

    /** The consuming tuple is sufficient even when its saved campsite differs. */
    @Test fun foundConsumptionNeedNotMatchThisSelectedCampsite() =
        runTest {
            val bookings = FakeBookingRepository(listOf(record.copy(campsiteId = "another-site")))
            assertEquals(
                BookingWriteResult.Saved(record.id),
                BookingWriter(bookings).recover(request.lotteryId, request.entrantId),
            )
        }

    /** A mismatched or malformed server response remains unknown rather than inventing absence. */
    @Test fun mismatchedLotteryEntrantOrBlankIdentityCannotBecomeSaved() =
        runTest {
            for (invalid in listOf(
                record.copy(lotteryId = "another-lottery"),
                record.copy(entrantId = "another-entrant"),
                record.copy(id = ""),
                record.copy(id = " "),
            )) {
                val bookings =
                    FakeBookingRepository().apply {
                        opportunityHandler = {
                            _,
                            _,
                            ->
                            invalid
                        }
                    }
                assertEquals(
                    BookingWriteResult.Unknown,
                    BookingWriter(bookings).recover(request.lotteryId, request.entrantId),
                )
                assertTrue(bookings.reserveRequests.isEmpty())
                assertEquals(
                    listOf(request.lotteryId to request.entrantId),
                    bookings.opportunityRequests,
                )
            }
        }

    /** Expected and unexpected read errors both mean the server has not confirmed consumption. */
    @Test fun standaloneRecoveryFailureRemainsUnknown() =
        runTest {
            for (failure in listOf(
                IOException("Offline"),
                PersistenceFailure(PersistenceFailure.Reason.NOT_FOUND, "Read failed"),
                IllegalArgumentException("Malformed record"),
            )) {
                val bookings =
                    FakeBookingRepository().apply {
                        opportunityHandler = { _, _ -> throw failure }
                    }
                assertEquals(
                    BookingWriteResult.Unknown,
                    BookingWriter(bookings).recover(request.lotteryId, request.entrantId),
                )
                assertTrue(bookings.reserveRequests.isEmpty())
                assertEquals(
                    listOf(request.lotteryId to request.entrantId),
                    bookings.opportunityRequests,
                )
            }
        }

    /** ALREADY_USED still requires confirmed history; it never fabricates a destination. */
    @Test fun alreadyUsedCanRemainAbsentOrUnknownWithoutBlindRetry() =
        runTest {
            val bookings =
                FakeBookingRepository().apply {
                    reserveHandler = {
                        throw PersistenceFailure(PersistenceFailure.Reason.ALREADY_USED, "Used")
                    }
                }
            val writer = BookingWriter(bookings)
            assertEquals(BookingWriteResult.Absent, writer.submit(request))
            bookings.opportunityHandler = { _, _ -> throw IOException("Unknown") }
            assertEquals(BookingWriteResult.Unknown, writer.submit(request))
            assertEquals(List(2) { request }, bookings.reserveRequests)
            assertEquals(
                List(2) { request.lotteryId to request.entrantId },
                bookings.opportunityRequests,
            )
        }

    /** Cancellation from reserve propagates unchanged and cannot begin a recovery operation. */
    @Test fun reserveCancellationPropagatesWithoutLookup() =
        runTest {
            val cancelled = CancellationException("Retired graph")
            val bookings = FakeBookingRepository().apply { reserveHandler = { throw cancelled } }
            try {
                BookingWriter(bookings).submit(request)
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
            assertTrue(bookings.opportunityRequests.isEmpty())
        }

    /** Cancellation from either recovery entry propagates without another mutation. */
    @Test fun recoveryCancellationPropagatesFromSubmitAndStandaloneRead() =
        runTest {
            val cancelled = CancellationException("Retired read")
            val bookings =
                FakeBookingRepository().apply {
                    reserveHandler = { throw IOException("Unknown acknowledgement") }
                    opportunityHandler = { _, _ -> throw cancelled }
                }
            val writer = BookingWriter(bookings)
            try {
                writer.submit(request)
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
            try {
                writer.recover(request.lotteryId, request.entrantId)
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
            assertEquals(listOf(request), bookings.reserveRequests)
        }

    /** A noncooperative success cannot publish after its caller is cancelled. */
    @Test fun cancelledCallerCannotPublishAcknowledgementOrRecovery() =
        runTest {
            val bookings =
                FakeBookingRepository().apply {
                    reserveHandler = {
                        currentCoroutineContext().cancel()
                        record.id
                    }
                }
            var published: BookingWriteResult? = null
            val job = launch { published = BookingWriter(bookings).submit(request) }
            runCurrent()
            assertTrue(job.isCancelled)
            assertNull(published)
            assertTrue(bookings.opportunityRequests.isEmpty())
            bookings.opportunityHandler = { _, _ ->
                currentCoroutineContext().cancel()
                record
            }
            val recovery =
                launch {
                    published =
                        BookingWriter(bookings).recover(request.lotteryId, request.entrantId)
                }
            runCurrent()
            assertTrue(recovery.isCancelled)
            assertNull(published)
            bookings.opportunityHandler = { _, _ ->
                currentCoroutineContext().cancel()
                throw IOException("Interrupted recovery")
            }
            val failedRecovery =
                launch {
                    published =
                        BookingWriter(bookings).recover(request.lotteryId, request.entrantId)
                }
            runCurrent()
            assertTrue(failedRecovery.isCancelled)
            assertNull(published)
        }

    /** An expected failure after cancellation cannot publish an editable outcome. */
    @Test fun cancelledCallerCannotPublishARejection() =
        runTest {
            for (failure in listOf(
                PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Too late"),
                IOException("Interrupted write"),
            )) {
                val bookings =
                    FakeBookingRepository().apply {
                        reserveHandler = {
                            currentCoroutineContext().cancel()
                            throw failure
                        }
                    }
                var published: BookingWriteResult? = null
                var recoveryPhases = 0
                val job =
                    launch {
                        published = BookingWriter(bookings).submit(request) { recoveryPhases++ }
                    }
                runCurrent()
                assertTrue(job.isCancelled)
                assertNull(published)
                assertTrue(bookings.opportunityRequests.isEmpty())
                assertEquals(0, recoveryPhases)
            }
        }

    /** Retired callers cannot start either a new mutation or its recovery read. */
    @Test fun alreadyCancelledCallerStartsNoRepositoryOperation() =
        runTest {
            val bookings = FakeBookingRepository().apply { reserveHandler = { record.id } }
            val writer = BookingWriter(bookings)
            val job =
                launch {
                    currentCoroutineContext().cancel()
                    writer.submit(request)
                }
            runCurrent()
            assertTrue(job.isCancelled)
            assertTrue(bookings.reserveRequests.isEmpty())
            val read =
                launch {
                    currentCoroutineContext().cancel()
                    writer.recover(request.lotteryId, request.entrantId)
                }
            runCurrent()
            assertTrue(read.isCancelled)
            assertTrue(bookings.opportunityRequests.isEmpty())
        }
}

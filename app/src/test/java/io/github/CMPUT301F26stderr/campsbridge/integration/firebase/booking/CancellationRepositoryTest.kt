package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns refunds, inventory release, consumed opportunities and cancellation history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CancellationRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Entrant cancellation refunds once, releases every night and permanently consumes the win. */
    @Test(timeout = 60000)
    fun cancellationIsCompleteAndRepeatable() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val originalPayment = bookings.getPayment(id)!!
            bookings.cancel(
                CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "Plans changed"),
            )
            val cancelled = bookings.get(id)!!
            val refunded = bookings.getPayment(id)!!
            assertEquals(fixture.entrant.id, cancelled.cancellation!!.actorId)
            assertEquals(Role.ENTRANT, cancelled.cancellation.initiatedAs)
            assertEquals("Plans changed", cancelled.cancellation.reason)
            assertNotNull(refunded.refundedAt)
            assertEquals(originalPayment.amountCents, refunded.amountCents)
            assertEquals(originalPayment.paidAt, refunded.paidAt)
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertEquals(2, nights.size)
            assertTrue(nights.all { it.isFree })
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            bookings.cancel(
                CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "A second reason"),
            )
            assertEquals(cancelled, bookings.get(id))
            assertEquals(refunded, bookings.getPayment(id))
            assertPersistenceFailure(Reason.ALREADY_USED) { bookings.reserve(fixture.request) }
            assertPersistenceFailure(Reason.ALREADY_USED) {
                fixture.entrant.container.lotteries
                    .decline("draw", fixture.entrant.id)
            }
        }

    /** Owner rejection retains owner attribution while using the same refund operation. */
    @Test(timeout = 60000)
    fun ownerRejectionRecordsItsActor() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val id =
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            fixture.owner.container.bookings.cancel(
                CancellationRequest(id, fixture.owner.id, Role.OWNER, "Unavailable"),
            )
            val cancellation =
                fixture.owner.container.bookings
                    .get(id)!!
                    .cancellation!!
            assertEquals(fixture.owner.id, cancellation.actorId)
            assertEquals(Role.OWNER, cancellation.initiatedAs)
            assertNotNull(
                fixture.owner.container.bookings
                    .getPayment(id)!!
                    .refundedAt,
            )
        }

    /** An administrator records administrative attribution rather than impersonating a participant. */
    @Test(timeout = 60000)
    fun administratorCancellationRecordsItsActor() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val admin = firebase.registeredActor(Role.ADMIN)
            val id =
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            admin.container.bookings.cancel(
                CancellationRequest(id, admin.id, Role.ADMIN, "Removed listing"),
            )
            val cancellation =
                admin.container.bookings
                    .get(id)!!
                    .cancellation!!
            assertEquals(admin.id, cancellation.actorId)
            assertEquals(Role.ADMIN, cancellation.initiatedAs)
        }

    /** A mismatched night prevents every cancellation write rather than partially releasing a stay. */
    @Test(timeout = 60000)
    fun brokenCalendarClaimPreventsPartialCancellation() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val before = bookings.get(id)
            val payment = bookings.getPayment(id)
            firebase.seed(
                "campsites/site/calendar/${fixture.request.checkInDate.plusDays(1)}",
                mapOf("isOffered" to true, "reservationId" to "different-reservation"),
            )
            assertPersistenceFailure(Reason.INVALID_STATE) {
                bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT))
            }
            assertEquals(before, bookings.get(id))
            assertEquals(payment, bookings.getPayment(id))
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertEquals(id, nights.first().reservationId)
            assertEquals("different-reservation", nights.last().reservationId)
            assertNull(bookings.getPayment(id)!!.refundedAt)
        }

    /** Missing payment data prevents cancellation and leaves the calendar claims intact. */
    @Test(timeout = 60000)
    fun missingPaymentPreventsPartialCancellation() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            firebase.erase("payments/$id")
            assertPersistenceFailure(Reason.NOT_FOUND) {
                bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT))
            }
            assertNull(bookings.get(id)!!.cancellation)
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertTrue(nights.all { it.reservationId == id })
        }
}

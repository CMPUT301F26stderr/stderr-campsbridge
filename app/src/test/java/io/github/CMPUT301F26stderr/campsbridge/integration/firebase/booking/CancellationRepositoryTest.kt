package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.ZoneId

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
            val originalPayment = bookings.get(id)!!.payment
            bookings.cancel(
                CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "Plans changed"),
            )
            val cancelled = bookings.get(id)!!
            val refunded = bookings.get(id)!!.payment
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
            assertEquals(refunded, bookings.get(id)?.payment)
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
                    .get(id)!!
                    .payment
                    .refundedAt,
            )
        }

    /** Administrative cancellation retains the actual administrator attribution. */
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

    /** A mismatched claim prevents cancellation without partially releasing the stay. */
    @Test(timeout = 60000)
    fun brokenCalendarClaimPreventsPartialCancellation() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val before = bookings.get(id)
            val payment = bookings.get(id)?.payment
            firebase.seed(
                "campsites/site/calendar/${fixture.request.checkInDate.plusDays(1)}",
                mapOf("isOffered" to true, "reservationId" to "different-reservation"),
            )
            assertPersistenceFailure(Reason.INVALID_STATE) {
                bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT))
            }
            assertEquals(before, bookings.get(id))
            assertEquals(payment, bookings.get(id)?.payment)
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertEquals(id, nights.first().reservationId)
            assertEquals("different-reservation", nights.last().reservationId)
            assertNull(bookings.get(id)!!.payment.refundedAt)
        }

    /** Missing payment data prevents cancellation and leaves the calendar claims intact. */
    @Test(timeout = 60000)
    fun missingPaymentPreventsPartialCancellation() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val stored = ReservationDocument.encode(bookings.get(id)!!)
            firebase.seed("reservations/$id", stored - "payment")
            assertThrows(DocumentDecodingException::class.java) {
                runBlocking {
                    bookings.cancel(
                        CancellationRequest(id, fixture.entrant.id, Role.ENTRANT),
                    )
                }
            }
            val after =
                fixture.entrant.clients.firestore
                    .document("reservations/$id")
                    .get(Source.SERVER)
                    .await()
            assertNull(after.get("cancellation"))
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertTrue(nights.all { it.reservationId == id })
        }

    /** Exact booked-local checkout rejects entrant cancellation without partial side effects. */
    @Test(timeout = 60000)
    fun entrantCannotCancelAtBookedCheckout() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val before = bookings.get(id)!!
            firebase.clock.current =
                before.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(before.bookedCampsite.timeZone),
                    ).toInstant()
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                bookings.cancel(
                    CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "Too late"),
                )
            }
            assertEquals(before, bookings.get(id))
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        before.checkInDate,
                        before.checkOutDate,
                    ).first()
            assertTrue(nights.all { it.reservationId == id })
            val eventId = DocumentKeys.compound("RESERVATION_CANCELLED", id)
            assertFalse(firebase.documentExists("notifications/$eventId"))
        }

    /** Owner rejection uses the same excluded checkout cutoff as entrant cancellation. */
    @Test(timeout = 60000)
    fun ownerCannotRejectAtBookedCheckout() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val id =
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            val before =
                fixture.owner.container.bookings
                    .get(id)!!
            firebase.clock.current =
                before.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(before.bookedCampsite.timeZone),
                    ).toInstant()
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                fixture.owner.container.bookings.cancel(
                    CancellationRequest(id, fixture.owner.id, Role.OWNER),
                )
            }
            assertEquals(
                before,
                fixture.owner.container.bookings
                    .get(id),
            )
        }

    /** The final instant of the last occupied local day still permits the full refund operation. */
    @Test(timeout = 60000)
    fun entrantCanCancelImmediatelyBeforeBookedCheckout() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val before = bookings.get(id)!!
            firebase.clock.current =
                before.checkOutDate
                    .atStartOfDay(
                        ZoneId.of(before.bookedCampsite.timeZone),
                    ).toInstant()
                    .minusMillis(1)
            bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT))
            val after = bookings.get(id)!!
            assertNotNull(after.cancellation)
            assertEquals(firebase.clock.current, after.payment.refundedAt)
            assertTrue(
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        before.checkInDate,
                        before.checkOutDate,
                    ).first()
                    .all {
                        it.isFree
                    },
            )
        }

    /** Editable listing/device zones cannot replace the historical zone at the action boundary. */
    @Test(timeout = 60000)
    fun cancellationUsesTheBookedZoneAfterTheLiveListingChanges() =
        runBlocking {
            val originalDeviceZone = java.util.TimeZone.getDefault()
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"))
                val fixture = firebase.bookingFixture()
                val id =
                    fixture.entrant.container.bookings
                        .reserve(fixture.request)
                val before =
                    fixture.owner.container.bookings
                        .get(id)!!
                fixture.owner.container.campsites.update(
                    "site",
                    fixture.owner.id,
                    fixture.campsite.details.copy(timeZone = "Pacific/Honolulu"),
                )
                firebase.clock.current =
                    before.checkOutDate
                        .atStartOfDay(
                            ZoneId.of(before.bookedCampsite.timeZone),
                        ).toInstant()
                assertTrue(
                    LocalDate.ofInstant(firebase.clock.current, ZoneId.of("Pacific/Honolulu")) <
                        before.checkOutDate,
                )
                assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                    fixture.owner.container.bookings.cancel(
                        CancellationRequest(id, fixture.owner.id, Role.OWNER),
                    )
                }
                assertEquals(
                    before,
                    fixture.owner.container.bookings
                        .get(id),
                )
            } finally {
                java.util.TimeZone.setDefault(originalDeviceZone)
            }
        }

    /** Authorized repeats after checkout preserve the original actor/refund/event exactly. */
    @Test(timeout = 60000)
    fun authorizedCancelledRetryAfterCheckoutRetainsEveryOriginalFact() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            bookings.cancel(
                CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "Original reason"),
            )
            val before = bookings.get(id)!!
            val eventId = DocumentKeys.compound("RESERVATION_CANCELLED", id)
            val event =
                fixture.entrant.container.notifications
                    .get(eventId)
            firebase.clock.current =
                before.checkOutDate
                    .plusDays(
                        1,
                    ).atStartOfDay(ZoneId.of(before.bookedCampsite.timeZone))
                    .toInstant()
            fixture.owner.container.bookings.cancel(
                CancellationRequest(id, fixture.owner.id, Role.OWNER, "Another reason"),
            )
            bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT, "Retry"))
            assertEquals(before, bookings.get(id))
            assertEquals(
                event,
                fixture.entrant.container.notifications
                    .get(eventId),
            )
            assertTrue(
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        before.checkInDate,
                        before.checkOutDate,
                    ).first()
                    .all {
                        it.isFree
                    },
            )
        }

    /** Authorization precedes both the cutoff and the already-cancelled no-op path. */
    @Test(timeout = 60000)
    fun unauthorizedLateCancellationCannotUseTheIdempotentReturn() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            // Readable history isolates the requested ENTRANT role authorization from read rules.
            val stranger = firebase.registeredActor(Role.ADMIN, Role.ENTRANT)
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            bookings.cancel(CancellationRequest(id, fixture.entrant.id, Role.ENTRANT))
            val before = bookings.get(id)!!
            firebase.clock.current =
                before.checkOutDate
                    .plusDays(
                        1,
                    ).atStartOfDay(ZoneId.of(before.bookedCampsite.timeZone))
                    .toInstant()
            assertPersistenceFailure(Reason.NOT_ALLOWED) {
                stranger.container.bookings.cancel(
                    CancellationRequest(id, stranger.id, Role.ENTRANT),
                )
            }
            assertEquals(before, bookings.get(id))
        }
}

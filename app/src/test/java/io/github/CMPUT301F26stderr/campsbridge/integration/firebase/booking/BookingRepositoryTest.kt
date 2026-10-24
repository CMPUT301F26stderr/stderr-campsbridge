package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Owns booking's coordinated writes, quote validation and competing claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class BookingRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Confirmation persists payment, all claimed nights, historical terms and delivery work. */
    @Test(timeout = 60000)
    fun reservationCommitsAllItsFactsTogether() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = bookings.reserve(fixture.request)
            val reservation = bookings.get(id)!!
            assertEquals(DocumentKeys.reservation("draw", fixture.entrant.id), id)
            assertEquals(fixture.campsite.details.name, reservation.bookedCampsite.name)
            assertEquals(fixture.request.acceptedRules, reservation.acceptedTerms.rules)
            assertEquals(
                fixture.request.policyVersion,
                reservation.acceptedTerms.cancellationPolicyVersion,
            )
            assertEquals(13000L, bookings.get(id)!!.payment.amountCents)
            assertNull(bookings.get(id)!!.payment.refundedAt)
            val calendar =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertEquals(2, calendar.size)
            assertTrue(calendar.all { it.reservationId == id })
            val eventId = DocumentKeys.compound("RESERVATION_CONFIRMED", id)
            assertNotNull(
                fixture.entrant.container.notifications
                    .get(eventId),
            )
            val job =
                fixture.entrant.clients.firestore
                    .document(
                        "_notificationDeliveries/$eventId",
                    ).get(Source.SERVER)
                    .await()
            assertEquals(listOf(fixture.entrant.id), job.get("recipientIds"))
        }

    /** Changing editable listing facts never changes previously accepted booking history. */
    @Test(timeout = 60000)
    fun laterListingEditsPreserveBookedHistory() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val id =
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            val original =
                fixture.entrant.container.bookings
                    .get(id)
            fixture.owner.container.campsites.update(
                "site",
                fixture.owner.id,
                fixture.campsite.details.copy(
                    name = "Renamed",
                    rules = "Changed rules",
                    nightlyPriceCents = 9900,
                ),
            )
            assertEquals(
                original,
                fixture.entrant.container.bookings
                    .get(id),
            )
            assertEquals(
                13000L,
                fixture.entrant.container.bookings
                    .get(id)!!
                    .payment
                    .amountCents,
            )
        }

    /** A conflicting night rolls back the complete requested stay, charge and notification. */
    @Test(timeout = 60000)
    fun unavailableNightLeavesNoPartialBooking() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val blocked = fixture.request.checkInDate.plusDays(1)
            firebase.seed(
                "campsites/site/calendar/$blocked",
                mapOf(
                    "isOffered" to true,
                    "reservationId" to "existing",
                ),
            )
            assertPersistenceFailure(Reason.CONFLICT) {
                fixture.entrant.container.bookings
                    .reserve(fixture.request)
            }
            assertNoBooking(fixture)
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        fixture.request.checkOutDate,
                    ).first()
            assertNull(nights.first().reservationId)
            assertEquals("existing", nights.last().reservationId)
        }

    /** Each accepted quote component is revalidated before any booking facts are written. */
    @Test(timeout = 60000)
    fun changedPriceRulesOrPolicyRejectStaleQuotes() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            for (request in listOf(
                fixture.request.copy(expectedTotalCents = 12999),
                fixture.request.copy(acceptedRules = "Old rules"),
                fixture.request.copy(policyVersion = "old-policy"),
            )) {
                assertPersistenceFailure(
                    Reason.STALE_QUOTE,
                ) {
                    fixture.entrant.container.bookings
                        .reserve(request)
                }
                assertNoBooking(fixture)
            }
        }

    /** Winning state and stay eligibility are checked before claiming inventory. */
    @Test(timeout = 60000)
    fun invalidOpportunityOrStayCannotBook() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            assertPersistenceFailure(Reason.CAPACITY_REACHED) {
                bookings.reserve(fixture.request.copy(guestCount = 9))
            }
            assertPersistenceFailure(Reason.NOT_ALLOWED) {
                bookings.reserve(
                    fixture.request.copy(checkOutDate = fixture.request.checkOutDate.plusDays(2)),
                )
            }
            for (state in listOf(
                EntryState.LOST,
                EntryState.DECLINED,
                EntryState.WITHDRAWN,
                EntryState.WAITING,
            )) {
                firebase.seed(
                    "lotteries/draw/entries/${fixture.entrant.id}",
                    LotteryEntryDocument.encode(
                        LotteryEntry(
                            "draw",
                            fixture.entrant.id,
                            state,
                            firebase.clock.instant(),
                            firebase.clock.instant(),
                        ),
                    ),
                )
                assertPersistenceFailure(Reason.NOT_ALLOWED) { bookings.reserve(fixture.request) }
            }
            assertNoBooking(fixture)
        }

    /** A closed or cancelled lottery cannot consume an otherwise valid winning opportunity. */
    @Test(timeout = 60000)
    fun closedAndCancelledLotteriesCannotBook() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            for (lottery in listOf(
                fixture.lottery.copy(
                    configuration =
                        fixture.lottery.configuration.copy(
                            bookingClosesAt = firebase.clock.instant().minusSeconds(1),
                        ),
                ),
                fixture.lottery.copy(
                    cancellation = Cancellation(firebase.clock.instant(), fixture.owner.id),
                ),
            )) {
                firebase.seed(
                    "lotteries/draw",
                    LotteryDocument.encode(lottery) + ("_revision" to 0L),
                )
                assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                    fixture.entrant.container.bookings
                        .reserve(fixture.request)
                }
                assertNoBooking(fixture)
            }
        }

    /** Booking today uses the campsite's local date even when UTC has reached tomorrow. */
    @Test(timeout = 60000)
    fun checkInCutoffUsesCampsiteTimezone() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val wallTime = firebase.clock.instant()
            val utcDate = LocalDate.ofInstant(wallTime, ZoneOffset.UTC)
            firebase.clock.current = utcDate.atTime(0, 30).toInstant(ZoneOffset.UTC)
            val localToday =
                LocalDate.ofInstant(
                    firebase.clock.instant(),
                    ZoneId.of(fixture.campsite.details.timeZone),
                )
            assertEquals(utcDate.minusDays(1), localToday)
            val broadWindow =
                fixture.lottery.copy(
                    configuration =
                        LotteryConfiguration(
                            wallTime.minusSeconds(259200),
                            wallTime.minusSeconds(172800),
                            wallTime.minusSeconds(172800),
                            wallTime.plusSeconds(172800),
                            2,
                            null,
                        ),
                )
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(broadWindow) + ("_revision" to 0L),
            )
            val dates = (-1L..1L).map(localToday::plusDays)
            firebase.seed(
                "lotteries/draw/offers/site",
                LotteryOfferDocument.encode(LotteryOffer("site", dates)),
            )
            for (date in dates) {
                firebase.seed(
                    "campsites/site/calendar/$date",
                    CalendarNightDocument.encode(CalendarNight(date, true)),
                )
            }
            val todayRequest =
                fixture.request.copy(
                    checkInDate = localToday,
                    checkOutDate = localToday.plusDays(2),
                )
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                fixture.entrant.container.bookings.reserve(
                    todayRequest.copy(
                        checkInDate = localToday.minusDays(1),
                        checkOutDate = localToday.plusDays(1),
                    ),
                )
            }
            assertNoBooking(fixture)
            val id =
                fixture.entrant.container.bookings
                    .reserve(todayRequest)
            assertEquals(
                localToday,
                fixture.entrant.container.bookings
                    .get(id)!!
                    .checkInDate,
            )
        }

    /** Overlapping concurrent stays permit one complete booking and no partial losing claims. */
    @Test(timeout = 60000)
    fun concurrentEntrantsCannotDoubleBookOverlappingNights() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val second = firebase.registeredActor(Role.ENTRANT)
            firebase.seed(
                "lotteries/draw/entries/${second.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "draw",
                        second.id,
                        EntryState.OFFERED,
                        firebase.clock.instant(),
                        firebase.clock.instant(),
                    ),
                ),
            )
            val secondRequest =
                fixture.request.copy(
                    entrantId = second.id,
                    checkInDate = fixture.request.checkInDate.plusDays(1),
                    checkOutDate = fixture.request.checkOutDate.plusDays(1),
                )
            val start = CompletableDeferred<Unit>()
            val operations =
                listOf(
                    async {
                        start.await()
                        runCatching {
                            fixture.entrant.container.bookings
                                .reserve(fixture.request)
                        }
                    },
                    async {
                        start.await()
                        runCatching { second.container.bookings.reserve(secondRequest) }
                    },
                )
            start.complete(Unit)
            val results = operations.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.isFailure })
            val winningId = results.single { it.isSuccess }.getOrThrow()
            val reservations =
                fixture.owner.container.bookings
                    .observeForCampsite("site")
                    .first()
            assertEquals(listOf(winningId), reservations.map { it.id })
            val nights =
                fixture.owner.container.campsites
                    .observeCalendar(
                        "site",
                        fixture.request.checkInDate,
                        secondRequest.checkOutDate,
                    ).first()
            assertEquals(2, nights.count { it.reservationId == winningId })
            assertEquals(1, nights.count { it.reservationId == null })
        }

    /** Simultaneous submissions cannot spend a winning opportunity more than once. */
    @Test(timeout = 60000)
    fun concurrentSubmissionsConsumeOneWinOnce() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val start = CompletableDeferred<Unit>()
            val submissions =
                (1..2).map {
                    async {
                        start.await()
                        runCatching {
                            fixture.entrant.container.bookings
                                .reserve(fixture.request)
                        }
                    }
                }
            start.complete(Unit)
            val results = submissions.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.isFailure })
            assertEquals(
                1,
                fixture.entrant.container.bookings
                    .observeForEntrant(fixture.entrant.id)
                    .first()
                    .size,
            )
        }

    /**
     * Verifies failed confirmation left no stay, payment or confirmation event behind.
     *
     * @param fixture Booking-ready starting state.
     */
    private suspend fun assertNoBooking(fixture: BookingFixture) {
        val id = DocumentKeys.reservation("draw", fixture.entrant.id)
        assertNull(
            fixture.entrant.container.bookings
                .get(id),
        )
        assertNull(
            fixture.entrant.container.bookings
                .get(id)
                ?.payment,
        )
        val eventId = DocumentKeys.compound("RESERVATION_CONFIRMED", id)
        assertFalse(firebase.documentExists("notifications/$eventId"))
        assertFalse(firebase.documentExists("_notificationDeliveries/$eventId"))
    }

    /** One live reservation contains confirmed payment and a cross-account cancellation/refund. */
    @Test(timeout = 60000)
    fun observesCompleteReservationAcrossBookingAndCancellation() =
        runBlocking {
            val fixture = firebase.bookingFixture()
            val bookings = fixture.entrant.container.bookings
            val id = DocumentKeys.reservation(fixture.request.lotteryId, fixture.entrant.id)
            val records = Channel<Reservation?>(Channel.UNLIMITED)
            val observation =
                launch {
                    try {
                        bookings.observe(id).collect { records.send(it) }
                    } finally {
                        records.close()
                    }
                }
            try {
                assertNull(withTimeout(5000) { records.receive() })
                assertEquals(id, bookings.reserve(fixture.request))
                val confirmed =
                    withTimeout(5000) {
                        records.receiveAsFlow().filterNotNull().first { it.cancellation == null }
                    }
                val charge = ReservationDocument.encode(confirmed)["payment"] as Map<*, *>
                assertEquals(fixture.request.expectedTotalCents, charge["amountCents"])
                assertNull(charge["refundedAt"])
                fixture.owner.container.bookings.cancel(
                    CancellationRequest(id, fixture.owner.id, Role.OWNER),
                )
                val cancelled =
                    withTimeout(5000) {
                        records.receiveAsFlow().filterNotNull().first { it.cancellation != null }
                    }
                val refund = ReservationDocument.encode(cancelled)["payment"] as Map<*, *>
                assertEquals(charge["amountCents"], refund["amountCents"])
                assertEquals(charge["paidAt"], refund["paidAt"])
                assertEquals(cancelled.cancellation!!.at.stored(), refund["refundedAt"])
                assertEquals(confirmed.acceptedTerms, cancelled.acceptedTerms)
                assertEquals(confirmed.bookedCampsite, cancelled.bookedCampsite)
            } finally {
                observation.cancelAndJoin()
            }
        }
}

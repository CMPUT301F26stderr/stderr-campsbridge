/*
 * Exercises the shared offline write contracts through the same repositories as the screens.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/** Exercises the shared offline write contracts through the same repositories as the screens. */
class ConnectedDemoContractTest {
    @get:Rule val main =
        io.github.CMPUT301F26stderr.campsbridge.testsupport
            .MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Saved listings, inventory and lottery allocations remain linked and enforce ownership. */
    @Test fun hostWritesProduceDiscoverableInventoryAndAnEditableLottery() =
        runTest {
            val session = DemoSession(DemoScenario.OWNER, files.root)
            try {
                val data = session.preview
                val repositories = session.services.repositories
                val sites = repositories.campsites
                val details =
                    data.campsites
                        .first()
                        .details
                        .copy(name = "Shared river pitch")
                val draft = sites.createDraft("journey-site", data.ownerId, details)
                sites.update(draft.id, data.ownerId, details.copy(description = "Saved from the editor"))
                assertEquals(
                    "Saved from the editor",
                    sites.createDraft(draft.id, data.ownerId, details).details.description,
                )
                assertTrue(
                    runCatching {
                        sites.update(
                            draft.id,
                            data.entrantId,
                            details,
                        )
                    }.exceptionOrNull() is PersistenceFailure,
                )
                sites.publish(draft.id, data.ownerId)
                val nights = listOf(LocalDate.of(2027, 5, 20), LocalDate.of(2027, 5, 21))
                sites.setOffered(draft.id, data.ownerId, nights.toSet(), true)
                assertTrue(sites.observePublished().first().any { it.id == draft.id })
                assertEquals(
                    nights,
                    sites.observeCalendar(draft.id, nights.first(), nights.last().plusDays(1)).first().map { it.date },
                )
                val now = session.services.clock.instant()
                val config =
                    LotteryConfiguration(
                        now.plusSeconds(60),
                        now.plusSeconds(120),
                        now.plusSeconds(120),
                        now.plusSeconds(3600),
                        1,
                        2,
                    )
                val lotteries = repositories.lotteries
                lotteries.createDraft("journey-lottery", data.ownerId, "River draw", "", config)
                lotteries.setOffer("journey-lottery", data.ownerId, LotteryOffer(draft.id, nights))
                lotteries.publish("journey-lottery", data.ownerId)
                assertEquals(
                    "journey-lottery",
                    lotteries
                        .observePublishedForCampsite(draft.id)
                        .first()
                        .single()
                        .id,
                )
                assertTrue(
                    runCatching {
                        lotteries.join(
                            "journey-lottery",
                            data.entrantId,
                        )
                    }.exceptionOrNull() is PersistenceFailure,
                )
            } finally {
                session.close()
            }
        }

    /** A real winner consumes exactly one opportunity and all occupied nights until refund. */
    @Test fun bookingCancellationAndOwnerRejectionUpdateCalendarAndInboxTogether() =
        runTest {
            val session = DemoSession(DemoScenario.ENTRANT, files.root)
            try {
                val data = session.preview
                val r = session.services.repositories
                val site = data.campsites.first()
                val request =
                    BookingRequest(
                        "demo-won",
                        data.entrantId,
                        site.id,
                        LocalDate.of(2027, 5, 18),
                        LocalDate.of(2027, 5, 20),
                        2,
                        site.details.nightlyPriceCents * 2,
                        site.details.rules,
                        BookingPolicy.VERSION,
                    )
                assertTrue(
                    runCatching {
                        r.bookings.reserve(request.copy(expectedTotalCents = 1))
                    }.exceptionOrNull() is PersistenceFailure,
                )
                val id = r.bookings.reserve(request)
                assertTrue(
                    r.campsites.observeCalendar(site.id, request.checkInDate, request.checkOutDate).first().all {
                        it.reservationId ==
                            id
                    },
                )
                assertTrue(runCatching { r.bookings.reserve(request) }.exceptionOrNull() is PersistenceFailure)
                assertTrue(
                    runCatching {
                        r.campsites.setOffered(site.id, data.ownerId, setOf(request.checkInDate), false)
                    }.exceptionOrNull() is PersistenceFailure,
                )
                assertTrue(
                    runCatching {
                        r.bookings.cancel(CancellationRequest(id, "demo-other", Role.ENTRANT))
                    }.exceptionOrNull() is PersistenceFailure,
                )
                r.bookings.cancel(CancellationRequest(id, data.entrantId, Role.ENTRANT))
                val cancelled = r.bookings.get(id)!!
                r.bookings.cancel(CancellationRequest(id, data.ownerId, Role.OWNER))
                assertEquals(cancelled, r.bookings.get(id))
                assertNotNull(cancelled.payment.refundedAt)
                assertTrue(
                    r.campsites
                        .observeCalendar(
                            site.id,
                            request.checkInDate,
                            request.checkOutDate,
                        ).first()
                        .all { it.isFree },
                )
                assertTrue(runCatching { r.bookings.reserve(request) }.exceptionOrNull() is PersistenceFailure)
                r.bookings.cancel(
                    CancellationRequest(
                        io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys.reservation(
                            "demo-booked",
                            "demo-account",
                        ),
                        data.ownerId,
                        Role.OWNER,
                        "Site unavailable",
                    ),
                )
                assertEquals(
                    Role.OWNER,
                    r.bookings
                        .get(
                            io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys.reservation(
                                "demo-booked",
                                "demo-account",
                            ),
                        )!!
                        .cancellation!!
                        .initiatedAs,
                )
                val events =
                    r.notifications
                        .observeAll()
                        .first()
                        .filter { it.subject.id == id }
                assertEquals(
                    setOf(
                        NotificationType.RESERVATION_CONFIRMED,
                        NotificationType.RESERVATION_CANCELLED,
                    ),
                    events
                        .map {
                            it.type
                        }.toSet(),
                )
                assertEquals(2, events.size)
                assertTrue(
                    r.notifications
                        .getInbox(data.entrantId)
                        .map { it.notificationId }
                        .containsAll(events.map { it.id }),
                )
            } finally {
                session.close()
            }
        }
}

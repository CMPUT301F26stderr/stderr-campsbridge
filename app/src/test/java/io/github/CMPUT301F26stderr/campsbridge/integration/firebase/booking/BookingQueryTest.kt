package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.booking

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owner-wide reads derive ownership from campsites and preserve complete retained history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class BookingQueryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Only owned sites contribute, including removed sites and cancelled reservations. */
    @Test(timeout = 60000)
    fun ownerSeesOrderedHistoryAcrossRetainedSites() =
        runBlocking<Unit> {
            val owner = firebase.registeredActor(Role.OWNER)
            val other = firebase.registeredActor(Role.OWNER)
            val empty = firebase.registeredActor(Role.OWNER)
            listOf("first", "retained", "foreign").forEach { id ->
                var site = CampingFixtures.campsite(id, if (id == "foreign") other.id else owner.id)
                if (id ==
                    "retained"
                ) {
                    site = site.copy(removal = Removal(firebase.clock.instant(), owner.id))
                }
                firebase.seed("campsites/$id", CampsiteDocument.encode(site))
                var reservation = sampleReservation(id, id)
                if (id ==
                    "first"
                ) {
                    reservation =
                        reservation.copy(createdAt = firebase.clock.instant().minusSeconds(60))
                }
                if (id == "retained") {
                    reservation =
                        reservation.copy(
                            cancellation =
                                ReservationCancellation(
                                    firebase.clock.instant(),
                                    owner.id,
                                    Role.OWNER,
                                    null,
                                ),
                            payment =
                                reservation.payment.copy(
                                    refundedAt = firebase.clock.instant(),
                                ),
                        )
                }
                firebase.seed(
                    "reservations/${reservation.id}",
                    ReservationDocument.encode(reservation),
                )
            }
            assertEquals(
                listOf(
                    DocumentKeys.reservation("retained", "entrant"),
                    DocumentKeys.reservation("first", "entrant"),
                ),
                withTimeout(5000) {
                    owner.container.bookings
                        .observeForOwner(owner.id)
                        .first { it.size == 2 }
                        .map { it.id }
                },
            )
            // The parent query is authoritatively empty; no child query should be needed.
            assertTrue(
                empty.clients.firestore
                    .collection("campsites")
                    .whereEqualTo("ownerId", empty.id)
                    .get(Source.SERVER)
                    .await()
                    .isEmpty,
            )
            assertTrue(
                empty.container.bookings
                    .observeForOwner(empty.id)
                    .first()
                    .isEmpty(),
            )
        }

    /** Newly owned sites and later reservation updates reach the same subscription. */
    @Test(timeout = 60000)
    fun ownerObservationTracksNewSitesAndReservationChanges() =
        runBlocking<Unit> {
            val owner = firebase.registeredActor(Role.OWNER)
            val states = Channel<List<Reservation>>(Channel.UNLIMITED)
            val observation =
                launch {
                    try {
                        owner.container.bookings
                            .observeForOwner(owner.id)
                            .collect { states.send(it) }
                    } finally {
                        states.close()
                    }
                }
            try {
                assertTrue(withTimeout(5000) { states.receive() }.isEmpty())
                owner.container.campsites.createDraft(
                    "new",
                    owner.id,
                    CampingFixtures.campsite().details,
                )
                owner.container.campsites.publish("new", owner.id)
                val reservation = sampleReservation("new", "draw")
                firebase.seed(
                    "reservations/${reservation.id}",
                    ReservationDocument.encode(reservation),
                )
                withTimeout(5000) {
                    states.receiveAsFlow().first {
                        it.map(Reservation::id) ==
                            listOf(reservation.id)
                    }
                }
                val cancelled =
                    reservation.copy(
                        cancellation =
                            ReservationCancellation(
                                firebase.clock.instant(),
                                owner.id,
                                Role.OWNER,
                                null,
                            ),
                        payment =
                            reservation.payment.copy(
                                refundedAt = firebase.clock.instant(),
                            ),
                    )
                firebase.seed(
                    "reservations/${reservation.id}",
                    ReservationDocument.encode(cancelled),
                )
                val observed =
                    withTimeout(5000) {
                        states.receiveAsFlow().first {
                            it.singleOrNull()?.cancellation !=
                                null
                        }
                    }
                assertEquals(cancelled.payment.amountCents, observed.single().payment.amountCents)
                assertEquals(cancelled.payment.refundedAt, observed.single().payment.refundedAt)
            } finally {
                observation.cancelAndJoin()
            }
        }

    /**
     * Builds a complete independently seeded stay for query contracts.
     * @param campsiteId Campsite establishing authoritative ownership.
     * @param lotteryId Identity used to derive the unique reservation key.
     */
    private fun sampleReservation(
        campsiteId: String,
        lotteryId: String,
    ): Reservation =
        CampingFixtures.reservation().copy(
            id = DocumentKeys.reservation(lotteryId, "entrant"),
            lotteryId = lotteryId,
            campsiteId = campsiteId,
            createdAt = firebase.clock.instant(),
        )
}

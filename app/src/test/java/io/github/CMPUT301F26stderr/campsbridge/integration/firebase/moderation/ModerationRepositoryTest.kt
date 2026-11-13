/*
 * Tests removal cascades and durable orchestration; booking and image suites own their operations.
 */

package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.moderation

import io.github.CMPUT301F26stderr.campsbridge.data.account.DeviceRegistrationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.moderation.FirestoreModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.stayNights
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery.SharedLotteryPhase
import io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery.sharedLotteryFixture
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.EmulatorActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Owns removal cascades and durable orchestration; booking and image suites own their operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ModerationRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Self-removal resumes shared-site cleanup after role loss and leaves the other owner's stay intact. */
    @Test(timeout = 60000)
    fun sharedOwnerRemovalRetriesRecordedWorkWithoutChangingOtherStays() =
        runBlocking {
            val f = firebase.sharedLotteryFixture(SharedLotteryPhase.DUE)
            val now = firebase.clock.instant()
            firebase.seed(
                "lotteries/shared",
                LotteryDocument.encode(
                    f.lottery.copy(
                        waitingCount = 0,
                        drawnAt = now.minusSeconds(30),
                        configuration = f.lottery.configuration.copy(winnerCount = 2),
                    ),
                ) + mapOf("participantOwnerIds" to listOf(f.organizer.id, f.member.id), "_revision" to 0L),
            )
            val stays =
                listOf("b1" to f.entrants[0], "a1" to f.entrants[1]).map { (siteId, entrant) ->
                    val id = DocumentKeys.reservation("shared", entrant.id)
                    val stay =
                        CampingFixtures.reservation().copy(
                            id = id,
                            lotteryId = "shared",
                            campsiteId = siteId,
                            entrantId = entrant.id,
                            checkInDate = f.nights[0],
                            checkOutDate = f.nights[0].plusDays(1),
                            createdAt = now,
                            acceptedTerms = CampingFixtures.reservation().acceptedTerms.copy(acceptedAt = now),
                            payment = Payment(6500, now),
                        )
                    firebase.seed("reservations/$id", ReservationDocument.encode(stay))
                    firebase.seed(
                        "campsites/$siteId/calendar/${f.nights[0]}",
                        CalendarNightDocument.encode(CalendarNight(f.nights[0], true, id)),
                    )
                    firebase.seed(
                        "lotteries/shared/entries/${entrant.id}",
                        LotteryEntryDocument.encode(
                            LotteryEntry(
                                "shared",
                                entrant.id,
                                EntryState.OFFERED,
                                f.lottery.configuration.registrationOpensAt
                                    .plusSeconds(30),
                                now.minusSeconds(30),
                            ),
                        ),
                    )
                    stay
                }
            val failure = IOException("Interrupted image cleanup")
            val interrupted =
                FirestoreModerationRepository(
                    f.member.clients.firestore,
                    object : ImageRepository by f.member.container.images {
                        /** Interrupts orchestration after removal facts have committed.
                         * @param target Recorded cleanup target.
                         */
                        override suspend fun delete(target: ImageTarget): ImageUpdateResult =
                            throw failure
                    },
                    FirestoreLotteryRepository(f.member.clients.firestore, firebase.clock, Random(17)),
                    firebase.clock,
                )
            try {
                interrupted.removeAccount(f.member.id, f.member.id, "Leaving")
                error("Expected the injected cleanup failure")
            } catch (actual: IOException) {
                assertSame(failure, actual)
            }
            val removed =
                checkNotNull(
                    f.admin.container.accounts
                        .get(f.member.id),
                )
            assertTrue(removed.roles.isEmpty())
            assertTrue(firebase.documentExists("_removals/account_${f.member.id}"))
            firebase.clock.current = now.plusSeconds(30)
            f.member.container.moderation
                .removeAccount(f.member.id, f.member.id, "Retry")
            val cancelled =
                checkNotNull(
                    f.admin.container.bookings
                        .get(stays[0].id),
                )
            assertEquals(
                f.member.id,
                f.admin.container.lotteries
                    .get("shared")!!
                    .cancellation!!
                    .actorId,
            )
            assertEquals(f.member.id, cancelled.cancellation!!.actorId)
            assertNotNull(cancelled.payment.refundedAt)
            assertEquals(
                stays[1],
                f.admin.container.bookings
                    .get(stays[1].id),
            )
            assertEquals(
                removed.removal,
                f.admin.container.accounts
                    .get(f.member.id)!!
                    .removal,
            )
            assertFalse(firebase.documentExists("_removals/account_${f.member.id}"))
            assertFalse(firebase.documentExists("_removals/campsite_b1"))
            assertFalse(firebase.documentExists("_removals/campsite_b2"))
            val eventId = DocumentKeys.compound("RESERVATION_CANCELLED", stays[0].id)
            val event =
                f.admin.container.notifications
                    .get(eventId)
            firebase.clock.current = now.plusSeconds(60)
            f.member.container.moderation
                .removeAccount(f.member.id, f.member.id, "Another retry")
            assertEquals(
                cancelled,
                f.admin.container.bookings
                    .get(stays[0].id),
            )
            assertEquals(
                event,
                f.admin.container.notifications
                    .get(eventId),
            )
        }

    /** Account removal scrubs editable data, clears devices, withdraws open entries and hides owned sites. */
    @Test(timeout = 60000)
    fun accountRemovalPreservesIdentityAndFrozenParticipation() =
        runBlocking {
            val account = firebase.registeredActor(Role.ENTRANT, Role.OWNER)
            val owner = firebase.registeredActor(Role.OWNER)
            val admin = firebase.registeredActor(Role.ADMIN)
            val createdAt = checkNotNull(admin.container.accounts.get(account.id)).createdAt
            firebase.seed(
                "accounts/${account.id}/devices/install",
                DeviceRegistrationDocument.encode(
                    DeviceRegistration("install", "token", firebase.clock.instant()),
                ),
            )
            firebase.seed(
                "campsites/owned",
                CampsiteDocument.encode(CampingFixtures.campsite("owned", account.id)),
            )
            val now = firebase.clock.instant()
            for ((id, deadline) in listOf(
                "open" to now.plusSeconds(600),
                "frozen" to now.minusSeconds(60),
            )) {
                val lottery =
                    CampingFixtures.lottery(id, owner.id).let {
                        it.copy(
                            configuration =
                                it.configuration.copy(
                                    registrationOpensAt = deadline.minusSeconds(600),
                                    registrationClosesAt = deadline,
                                    bookingOpensAt = deadline,
                                    bookingClosesAt = deadline.plusSeconds(1200),
                                ),
                            waitingCount = 1,
                        )
                    }
                firebase.seed(
                    "lotteries/$id",
                    LotteryDocument.encode(lottery) + ("_revision" to 0L),
                )
                firebase.seed(
                    "lotteries/$id/entries/${account.id}",
                    LotteryEntryDocument.encode(
                        LotteryEntry(
                            id,
                            account.id,
                            EntryState.WAITING,
                            now.minusSeconds(700),
                            now.minusSeconds(700),
                        ),
                    ),
                )
            }
            admin.container.moderation.removeAccount(account.id, admin.id, "Removed")
            val removed = checkNotNull(admin.container.accounts.get(account.id))
            assertEquals(account.id, removed.id)
            assertEquals(createdAt, removed.createdAt)
            assertEquals("", removed.name)
            assertNull(removed.email)
            assertNull(removed.phone)
            assertTrue(removed.roles.isEmpty())
            assertEquals(Preferences(), removed.preferences)
            assertEquals(AccountSettings(notificationsEnabled = false), removed.settings)
            assertEquals(admin.id, removed.removal!!.actorId)
            assertFalse(firebase.documentExists("accounts/${account.id}/devices/install"))
            assertNotNull(
                admin.container.campsites
                    .get("owned")!!
                    .removal,
            )
            assertEquals(
                EntryState.WITHDRAWN,
                admin.container.lotteries
                    .observeEntries("open")
                    .first()
                    .single()
                    .state,
            )
            assertEquals(
                0L,
                admin.container.lotteries
                    .get("open")!!
                    .waitingCount,
            )
            assertEquals(
                EntryState.WAITING,
                admin.container.lotteries
                    .observeEntries("frozen")
                    .first()
                    .single()
                    .state,
            )
            assertEquals(
                1L,
                admin.container.lotteries
                    .get("frozen")!!
                    .waitingCount,
            )
            assertFalse(firebase.documentExists("_removals/account_${account.id}"))
            assertFalse(firebase.documentExists("_removals/campsite_owned"))
            firebase.clock.current = now.plusSeconds(60)
            admin.container.moderation.removeAccount(account.id, admin.id, "Second reason")
            assertEquals(removed, admin.container.accounts.get(account.id))
        }

    /** Owner removal cancels drafts and active lotteries while retaining ended and unrelated ones. */
    @Test(timeout = 60000)
    fun ownerRemovalPreservesEndedLotteryHistory() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val other = firebase.registeredActor(Role.OWNER)
            val admin = firebase.registeredActor(Role.ADMIN)
            val now = firebase.clock.instant()
            val active =
                CampingFixtures.lottery("active", owner.id).copy(
                    configuration =
                        LotteryConfiguration(
                            now.minusSeconds(600),
                            now.plusSeconds(600),
                            now.plusSeconds(600),
                            now.plusSeconds(1800),
                            2,
                            null,
                        ),
                )
            val ended =
                active.copy(
                    id = "ended",
                    drawnAt = now.minusSeconds(4000),
                    configuration =
                        LotteryConfiguration(
                            now.minusSeconds(4800),
                            now.minusSeconds(4500),
                            now.minusSeconds(4500),
                            now.minusSeconds(3600),
                            2,
                            null,
                        ),
                )
            val scenarios =
                listOf(
                    active,
                    ended,
                    ended.copy(id = "draft", publishedAt = null, drawnAt = null),
                    active.copy(id = "unrelated", ownerId = other.id, participantOwnerIds = setOf(other.id)),
                )
            for (lottery in scenarios) {
                firebase.seed(
                    "lotteries/${lottery.id}",
                    LotteryDocument.encode(lottery) + ("_revision" to 0L),
                )
            }
            admin.container.moderation.removeAccount(owner.id, admin.id, "Owner removed")
            for (id in listOf("draft", "active")) {
                val cancellation =
                    admin.container.lotteries
                        .get(id)!!
                        .cancellation!!
                assertEquals(admin.id, cancellation.actorId)
                assertEquals("Owner removed", cancellation.reason)
            }
            assertNull(
                admin.container.lotteries
                    .get("ended")!!
                    .cancellation,
            )
            assertNull(
                admin.container.lotteries
                    .get("unrelated")!!
                    .cancellation,
            )
            assertFalse(firebase.documentExists("_removals/account_${owner.id}"))
        }

    /** Listing removal cancels uncompleted stays using the booked timezone and keeps completed history. */
    @Test(timeout = 60000)
    fun campsiteRemovalDistinguishesCompletedStaysAtTheLocalDateBoundary() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val todayUtc = LocalDate.ofInstant(firebase.clock.instant(), ZoneId.of("UTC"))
            firebase.clock.current = todayUtc.atTime(0, 30).atZone(ZoneId.of("UTC")).toInstant()
            firebase.seed(
                "campsites/site",
                CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)),
            )
            val uncompleted = seedStay("uncompleted", entrant, todayUtc)
            val completed = seedStay("completed", entrant, todayUtc.minusDays(2))
            admin.container.moderation.removeCampsite("site", admin.id, "Unavailable")
            val current = admin.container.bookings.get(uncompleted)!!
            assertEquals(admin.id, current.cancellation!!.actorId)
            assertEquals(Role.ADMIN, current.cancellation.initiatedAs)
            assertEquals("Unavailable", current.cancellation.reason)
            assertNotNull(
                admin.container.bookings
                    .get(uncompleted)!!
                    .payment
                    .refundedAt,
            )
            assertNull(
                admin.container.bookings
                    .get(completed)!!
                    .cancellation,
            )
            assertNull(
                admin.container.bookings
                    .get(completed)!!
                    .payment
                    .refundedAt,
            )
            val calendar =
                admin.clients.firestore
                    .document(
                        "campsites/site/calendar/${todayUtc.minusDays(1)}",
                    ).get()
                    .await()
            assertNull(calendar.getString("reservationId"))
            assertFalse(firebase.documentExists("_removals/campsite_site"))
        }

    /** An interrupted cascade keeps its job and first audit record until fresh production wiring resumes it. */
    @Test(timeout = 60000)
    fun failedCleanupRetainsDurableWorkAndOriginalRemovalMetadata() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val admin = firebase.registeredActor(Role.ADMIN)
            firebase.seed(
                "campsites/site",
                CampsiteDocument.encode(CampingFixtures.campsite(ownerId = owner.id)),
            )
            val failure = IOException("Storage unavailable")
            val failingImages =
                object : ImageRepository by owner.container.images {
                    /**
                     * Injects one orchestration failure; actual Storage behavior belongs to image tests.
                     * @param target Attachment selected by the removal operation.
                     */
                    override suspend fun delete(target: ImageTarget): ImageUpdateResult =
                        throw failure
                }
            val removals =
                FirestoreModerationRepository(
                    owner.clients.firestore,
                    failingImages,
                    FirestoreLotteryRepository(owner.clients.firestore, firebase.clock, Random(17)),
                    firebase.clock,
                )
            try {
                removals.removeCampsite("site", owner.id, "First reason")
                error("Expected cleanup failure")
            } catch (actual: IOException) {
                assertSame(failure, actual)
            }
            val first =
                admin.container.campsites
                    .get("site")!!
                    .removal
            assertNotNull(first)
            assertTrue(firebase.documentExists("_removals/campsite_site"))
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            admin.container.moderation.removeCampsite("site", admin.id, "Retry reason")
            assertEquals(
                first,
                admin.container.campsites
                    .get("site")!!
                    .removal,
            )
            assertFalse(firebase.documentExists("_removals/campsite_site"))
        }

    /**
     * Arranges a paid stay and its inventory claims without exercising booking creation.
     * @param id Reservation identity.
     * @param entrant Reservation participant.
     * @param checkout Excluded final local date.
     */
    private fun seedStay(
        id: String,
        entrant: EmulatorActor,
        checkout: LocalDate,
    ): String {
        val reservationId = DocumentKeys.reservation(id, entrant.id)
        val reservation =
            CampingFixtures.reservation().copy(
                id = reservationId,
                lotteryId = id,
                entrantId = entrant.id,
                checkInDate = checkout.minusDays(1),
                checkOutDate = checkout,
                payment = Payment(6500, CampingFixtures.now),
            )
        firebase.seed("reservations/$reservationId", ReservationDocument.encode(reservation))
        for (night in stayNights(reservation.checkInDate, checkout)) {
            firebase.seed(
                "campsites/site/calendar/$night",
                CalendarNightDocument.encode(CalendarNight(night, true, reservationId)),
            )
        }
        return reservationId
    }
}

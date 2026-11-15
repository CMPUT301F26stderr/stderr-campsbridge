/*
 * Pins collaboration authority, whole-owner allocation writes and participant-scoped queries.
 */

package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Collaboration contracts; existing draw and booking suites retain their detailed rule matrices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SharedLotteryRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** The organizer controls membership, which cannot discard an owner with saved offers. */
    @Test(timeout = 60000)
    fun membershipPreservesOrganizerAndOutstandingAllocations() =
        runBlocking {
            val f = firebase.sharedLotteryFixture()
            val members = setOf(f.organizer.id, f.member.id)
            firebase.erase("lotteries/shared/offers/b1")
            firebase.seed(
                "lotteries/shared",
                LotteryDocument.encode(f.lottery) +
                    mapOf(
                        "participantOwnerIds" to listOf(f.organizer.id),
                        "_revision" to 0L,
                    ),
            )
            f.organizer.container.lotteries
                .updateParticipants("shared", f.organizer.id, members)
            assertEquals(
                members,
                f.organizer.container.lotteries
                    .get("shared")!!
                    .participantOwnerIds,
            )
            firebase.seed("lotteries/shared/offers/b1", LotteryOfferDocument.encode(f.offers.last()))
            assertPersistenceFailure(NOT_ALLOWED) {
                f.member.container.lotteries
                    .updateParticipants("shared", f.member.id, members + f.outsider.id)
            }
            assertPersistenceFailure(INVALID_STATE) {
                f.organizer.container.lotteries
                    .updateParticipants("shared", f.organizer.id, setOf(f.member.id))
            }
            assertPersistenceFailure(INVALID_STATE) {
                f.organizer.container.lotteries
                    .updateParticipants("shared", f.organizer.id, setOf(f.organizer.id))
            }
            assertEquals(
                members,
                f.organizer.container.lotteries
                    .get("shared")!!
                    .participantOwnerIds,
            )
            f.member.container.lotteries
                .replaceOwnerOffers("shared", f.member.id, emptyList())
            f.organizer.container.lotteries
                .updateParticipants("shared", f.organizer.id, setOf(f.organizer.id))
            assertEquals(
                setOf(f.organizer.id),
                f.organizer.container.lotteries
                    .get("shared")!!
                    .participantOwnerIds,
            )
        }

    /** A first collaborator needs readable published allocations, while solo drafts retain private offers. */
    @Test(timeout = 60000)
    fun sharingRejectsPrivateAllocationsWithoutChangingMembership() =
        runBlocking {
            val f = firebase.sharedLotteryFixture()
            firebase.erase("lotteries/shared/offers/b1")
            firebase.seed(
                "lotteries/shared",
                LotteryDocument.encode(f.lottery) +
                    mapOf(
                        "participantOwnerIds" to listOf(f.organizer.id),
                        "_revision" to 0L,
                    ),
            )
            firebase.seed(
                "campsites/a1",
                CampsiteDocument.encode(
                    CampingFixtures.campsite("a1", f.organizer.id).copy(publishedAt = null),
                ),
            )
            assertPersistenceFailure(INACTIVE) {
                f.organizer.container.lotteries.updateParticipants(
                    "shared",
                    f.organizer.id,
                    setOf(f.organizer.id, f.member.id),
                )
            }
            assertEquals(
                setOf(f.organizer.id),
                f.organizer.container.lotteries
                    .get("shared")!!
                    .participantOwnerIds,
            )
        }

    /** Replacement changes only B's offers and rejects invalid sets without partial deletion. */
    @Test(timeout = 60000)
    fun replacingOwnedAllocationsPreservesOtherOwnersAndRejectsInvalidInput() =
        runBlocking {
            val f = firebase.sharedLotteryFixture()
            val replacement = LotteryOffer("b2", f.nights.takeLast(2))
            val repository = f.member.container.lotteries
            repository.replaceOwnerOffers("shared", f.member.id, listOf(replacement))
            val expected = f.offers.filterNot { it.campsiteId == "b1" } + replacement
            assertEquals(expected.toSet(), repository.getOffers("shared").toSet())
            repository.replaceOwnerOffers("shared", f.member.id, listOf(replacement))
            assertEquals(expected.toSet(), repository.getOffers("shared").toSet())
            assertPersistenceFailure(NOT_ALLOWED) {
                repository.replaceOwnerOffers("shared", f.member.id, listOf(f.offers.first()))
            }
            assertEquals(expected.toSet(), repository.getOffers("shared").toSet())
            assertPersistenceFailure(INVALID_STATE) {
                repository.replaceOwnerOffers(
                    "shared",
                    f.member.id,
                    listOf(LotteryOffer("b2", listOf(f.nights.last().plusDays(10)))),
                )
            }
            assertEquals(expected.toSet(), repository.getOffers("shared").toSet())
            assertPersistenceFailure(
                NOT_ALLOWED,
            ) {
                f.organizer.container.lotteries
                    .removeOffer("shared", f.organizer.id, "b2")
            }
            repository.replaceOwnerOffers("shared", f.member.id, emptyList())
            assertEquals(f.offers.filterNot { it.campsiteId == "b1" }.toSet(), repository.getOffers("shared").toSet())
        }

    /** Published registration freezes membership, allocations and terms while descriptions remain editable. */
    @Test(timeout = 60000)
    fun registrationFreezePreservesSharedTerms() =
        runBlocking {
            val f = firebase.sharedLotteryFixture(SharedLotteryPhase.OPEN)
            val repository = f.organizer.container.lotteries
            assertPersistenceFailure(WINDOW_CLOSED) {
                repository.updateParticipants("shared", f.organizer.id, f.lottery.participantOwnerIds + f.outsider.id)
            }
            assertPersistenceFailure(WINDOW_CLOSED) {
                f.member.container.lotteries
                    .replaceOwnerOffers("shared", f.member.id, emptyList())
            }
            assertPersistenceFailure(WINDOW_CLOSED) {
                repository.update(
                    "shared",
                    f.organizer.id,
                    f.lottery.title,
                    f.lottery.description,
                    f.lottery.configuration.copy(winnerCount = 2),
                )
            }
            repository.update(
                "shared",
                f.organizer.id,
                "Bring drinking water",
                "Updated directions",
                f.lottery.configuration,
            )
            val saved = repository.get("shared")!!
            assertEquals(f.lottery.configuration, saved.configuration)
            assertEquals(f.lottery.participantOwnerIds, saved.participantOwnerIds)
            assertEquals(f.offers.toSet(), repository.getOffers("shared").toSet())
            assertEquals("Bring drinking water", saved.title)
        }

    /** Participant queries include shared drafts without changing the organizer query's meaning. */
    @Test(timeout = 60000)
    fun participantQueriesAndCampsiteLookupFollowActualMembership() =
        runBlocking {
            val f = firebase.sharedLotteryFixture()
            val repository = f.member.container.lotteries
            val organized =
                f.lottery.copy(
                    id = "member-organized",
                    ownerId = f.member.id,
                    participantOwnerIds = setOf(f.member.id),
                )
            firebase.seed(
                "lotteries/${organized.id}",
                LotteryDocument.encode(organized) +
                    mapOf(
                        "participantOwnerIds" to listOf(f.member.id),
                        "_revision" to 0L,
                    ),
            )
            val participating =
                withTimeout(8000) {
                    repository.observeParticipating(f.member.id).first { rows ->
                        rows.any { it.id == "shared" } && rows.any { it.id == organized.id }
                    }
                }
            assertEquals(setOf("shared", organized.id), participating.map { it.id }.toSet())
            val owned =
                withTimeout(8000) {
                    repository.observeForOwner(f.member.id).first { rows -> rows.any { it.id == organized.id } }
                }
            assertEquals(listOf(organized.id), owned.map { it.id })
            assertEquals(
                listOf("shared"),
                withTimeout(8000) {
                    f.organizer.container.lotteries.observeForOwner(f.organizer.id).first { rows ->
                        rows.any {
                            it.id ==
                                "shared"
                        }
                    }
                }.map { it.id },
            )
            val denied =
                runCatching {
                    f.outsider.container.lotteries
                        .get("shared")
                }.exceptionOrNull()
            assertTrue(denied is FirebaseFirestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (denied as FirebaseFirestoreException).code)
            firebase.seed(
                "lotteries/shared",
                LotteryDocument.encode(f.lottery.copy(publishedAt = firebase.clock.instant())) +
                    mapOf(
                        "participantOwnerIds" to listOf(f.organizer.id, f.member.id),
                        "_revision" to 0L,
                    ),
            )
            assertEquals(
                listOf(
                    "shared",
                ),
                withTimeout(8000) {
                    f.entrants.first().container.lotteries.observePublishedForCampsite("b1").first { rows ->
                        rows.any {
                            it.id ==
                                "shared"
                        }
                    }
                }.map { it.id },
            )
        }

    /** Shared operations use participant authority without granting an unrelated owner access. */
    @Test(timeout = 60000)
    fun memberCanDrawAnnounceAndCancel() =
        runBlocking {
            val f = firebase.sharedLotteryFixture(SharedLotteryPhase.DUE)
            val denied =
                runCatching {
                    f.outsider.container.lotteries
                        .draw("shared")
                }.exceptionOrNull()
            assertTrue(denied is FirebaseFirestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (denied as FirebaseFirestoreException).code)
            assertEquals(
                DrawResult.COMPLETED,
                f.member.container.lotteries
                    .draw("shared"),
            )
            assertNotNull(
                f.member.container.lotteries
                    .get("shared")!!
                    .drawnAt,
            )
            assertPersistenceFailure(NOT_ALLOWED) {
                f.member.container.lotteries
                    .publish("shared", f.member.id)
            }
            val id =
                f.member.container.notifications.announce(
                    "member-announcement",
                    "shared",
                    f.member.id,
                    Announcement("Access", "Use the east gate"),
                )
            assertEquals(
                listOf(id),
                f.member.container.notifications
                    .observeSent(f.member.id)
                    .first()
                    .map { it.id },
            )
            assertPersistenceFailure(NOT_ALLOWED) {
                f.outsider.container.notifications.announce(
                    "outsider-announcement",
                    "shared",
                    f.outsider.id,
                    Announcement("Invalid", "No membership"),
                )
            }
            f.member.container.lotteries
                .cancel("shared", f.member.id)
            assertEquals(
                f.member.id,
                f.organizer.container.lotteries
                    .get("shared")!!
                    .cancellation!!
                    .actorId,
            )
            assertPersistenceFailure(NOT_ALLOWED) {
                f.outsider.container.lotteries
                    .cancel("shared", f.outsider.id)
            }
        }

    /** Publication and whole-owner replacement never expose a partly replaced allocation set. */
    @Test(timeout = 60000)
    fun allocationReplacementRacingPublicationIsConsistent() =
        runBlocking {
            val f = firebase.sharedLotteryFixture()
            val gate = CompletableDeferred<Unit>()
            val replacement = LotteryOffer("b2", f.nights.takeLast(2))
            val actions =
                listOf(
                    async {
                        gate.await()
                        runCatching {
                            f.member.container.lotteries.replaceOwnerOffers(
                                "shared",
                                f.member.id,
                                listOf(replacement),
                            )
                        }
                    },
                    async {
                        gate.await()
                        runCatching {
                            f.organizer.container.lotteries
                                .publish("shared", f.organizer.id)
                        }
                    },
                )
            gate.complete(Unit)
            for (outcome in actions.awaitAll()) {
                outcome.exceptionOrNull()?.let { failure ->
                    assertTrue(failure is PersistenceFailure)
                    assertEquals(CONFLICT, (failure as PersistenceFailure).reason)
                }
            }
            f.member.container.lotteries
                .replaceOwnerOffers("shared", f.member.id, listOf(replacement))
            assertEquals(
                (
                    f.offers.filterNot {
                        it.campsiteId == "b1"
                    } + replacement
                ).toSet(),
                f.organizer.container.lotteries
                    .getOffers("shared")
                    .toSet(),
            )
        }
}

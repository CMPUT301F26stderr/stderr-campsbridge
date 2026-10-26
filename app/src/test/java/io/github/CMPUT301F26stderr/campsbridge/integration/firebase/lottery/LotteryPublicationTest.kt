package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns publication prerequisites and the owner's offer/configuration editing boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class LotteryPublicationTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Publication requires offered inventory on a published campsite and retains its first time. */
    @Test(timeout = 60000)
    fun publicationValidatesListingAndCalendarPrerequisites() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val campsites = owner.container.campsites
            val lotteries = owner.container.lotteries
            val now = firebase.clock.instant()
            val siteId =
                campsites
                    .createDraft(
                        "site",
                        owner.id,
                        CampingFixtures.campsite().details,
                    ).id
            val config =
                LotteryConfiguration(
                    now.plusSeconds(3600),
                    now.plusSeconds(7200),
                    now.plusSeconds(7200),
                    now.plusSeconds(10800),
                    2,
                    null,
                )
            val id = lotteries.createDraft("draw", owner.id, "May camping", "Two nights", config).id
            assertNull(lotteries.get(id)!!.publishedAt)
            assertPersistenceFailure(Reason.INVALID_STATE) { lotteries.publish(id, owner.id) }
            val nights = listOf(CampingFixtures.arrival, CampingFixtures.arrival.plusDays(1))
            val offer = LotteryOffer(siteId, nights)
            assertPersistenceFailure(
                Reason.INVALID_STATE,
            ) { lotteries.setOffer(id, owner.id, offer) }
            campsites.setOffered(siteId, owner.id, nights.toSet(), true)
            lotteries.setOffer(id, owner.id, offer)
            assertPersistenceFailure(Reason.INACTIVE) { lotteries.publish(id, owner.id) }
            assertNull(lotteries.get(id)!!.publishedAt)
            campsites.publish(siteId, owner.id)
            lotteries.publish(id, owner.id)
            val published = lotteries.get(id)!!
            assertEquals(listOf(offer), lotteries.observeOffers(id).first())
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            lotteries.publish(id, owner.id)
            assertEquals(published, lotteries.get(id))
        }

    /** An owner can remove a draft allocation; an empty lottery then fails publication. */
    @Test(timeout = 60000)
    fun removingLastDraftOfferPreventsPublication() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val now = firebase.clock.instant()
            val siteId =
                owner.container.campsites
                    .createDraft(
                        "site",
                        owner.id,
                        CampingFixtures.campsite().details,
                    ).id
            owner.container.campsites.publish(siteId, owner.id)
            owner.container.campsites.setOffered(
                siteId,
                owner.id,
                setOf(CampingFixtures.arrival),
                true,
            )
            val id =
                owner.container.lotteries
                    .createDraft(
                        "draw",
                        owner.id,
                        "May camping",
                        "One night",
                        LotteryConfiguration(
                            now.plusSeconds(3600),
                            now.plusSeconds(7200),
                            now.plusSeconds(7200),
                            now.plusSeconds(10800),
                            1,
                            null,
                        ),
                    ).id
            owner.container.lotteries.setOffer(
                id,
                owner.id,
                LotteryOffer(siteId, listOf(CampingFixtures.arrival)),
            )
            owner.container.lotteries.removeOffer(id, owner.id, siteId)
            assertTrue(
                owner.container.lotteries
                    .observeOffers(id)
                    .first()
                    .isEmpty(),
            )
            assertPersistenceFailure(
                Reason.INVALID_STATE,
            ) { owner.container.lotteries.publish(id, owner.id) }
            assertNull(
                owner.container.lotteries
                    .get(id)!!
                    .publishedAt,
            )
        }

    /** Registration freezes offers/configuration while descriptions remain editable. */
    @Test(timeout = 60000)
    fun registrationFreezesTermsButAllowsDescriptiveEdits() =
        runBlocking {
            val fixture = firebase.registrationFixture()
            val lotteries = fixture.owner.container.lotteries
            val id = fixture.lottery.id
            val config = fixture.lottery.configuration
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                lotteries.update(
                    id,
                    fixture.owner.id,
                    "Changed",
                    "Changed",
                    config.copy(winnerCount = 3),
                )
            }
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                lotteries.setOffer(
                    id,
                    fixture.owner.id,
                    LotteryOffer("site", listOf(CampingFixtures.arrival)),
                )
            }
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                lotteries.removeOffer(id, fixture.owner.id, "site")
            }
            lotteries.update(id, fixture.owner.id, "Updated title", "Updated description", config)
            assertEquals(
                fixture.lottery.copy(title = "Updated title", description = "Updated description"),
                lotteries.get(id),
            )
        }

    /** Replayed creation returns confirmed edits and concurrent callers share one draft. */
    @Test(timeout = 60000)
    fun stableDraftCreationPreservesConfirmedEdits() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val lotteries = owner.container.lotteries
            val configuration = CampingFixtures.lottery().configuration
            val original =
                lotteries.createDraft(
                    "stable",
                    owner.id,
                    "May camping",
                    "Two nights",
                    configuration,
                )
            lotteries.update(
                original.id,
                owner.id,
                "Confirmed edit",
                "Updated description",
                configuration,
            )
            val edited = lotteries.get(original.id)!!
            firebase.clock.current = firebase.clock.current.plusSeconds(1)
            assertEquals(
                edited,
                lotteries.createDraft(
                    "stable",
                    owner.id,
                    "May camping",
                    "Two nights",
                    configuration,
                ),
            )
            val concurrent =
                (1..2)
                    .map {
                        async {
                            lotteries.createDraft(
                                "concurrent",
                                owner.id,
                                "May camping",
                                "Two nights",
                                configuration,
                            )
                        }
                    }.awaitAll()
            assertEquals(concurrent[0], concurrent[1])
            assertEquals("concurrent", concurrent[0].id)
            assertEquals(original.createdAt, edited.createdAt)
            assertEquals(
                setOf(
                    "stable",
                    "concurrent",
                ),
                lotteries
                    .observeForOwner(owner.id)
                    .first()
                    .map {
                        it.id
                    }.toSet(),
            )
        }

    /** Existing ownership and Firestore-safe identities are checked before draft writes. */
    @Test(timeout = 60000)
    fun draftCreationRejectsOwnershipCollisionAndInvalidIds() =
        runBlocking {
            val owner = firebase.registeredActor(Role.OWNER)
            val other = firebase.registeredActor(Role.OWNER)
            val occupied = CampingFixtures.lottery("taken", other.id)
            firebase.seed("lotteries/taken", LotteryDocument.encode(occupied))
            val stored = other.container.lotteries.get("taken")!!
            assertPersistenceFailure(Reason.CONFLICT) {
                owner.container.lotteries.createDraft(
                    "taken",
                    owner.id,
                    "Attempt",
                    "Attempt",
                    occupied.configuration,
                )
            }
            assertEquals(stored, owner.container.lotteries.get("taken"))
            listOf("", "a/b", "__reserved__").forEach { id ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        owner.container.lotteries.createDraft(
                            id,
                            owner.id,
                            "Attempt",
                            "Attempt",
                            occupied.configuration,
                        )
                    }
                }
            }
            assertTrue(
                owner.container.lotteries
                    .observeForOwner(owner.id)
                    .first()
                    .isEmpty(),
            )
        }
}

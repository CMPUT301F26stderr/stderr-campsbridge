package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
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

/** Entrant queries resolve canonical offers and react to publication/membership changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class LotteryQueryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Reverse lookup supports multiple sites and excludes drafts, cancellation and other sites. */
    @Test(timeout = 60000)
    fun entrantSeesOnlyAssociatedPublishedLotteries() =
        runBlocking<Unit> {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            listOf("site", "second", "empty").forEach { id ->
                firebase.seed(
                    "campsites/$id",
                    CampsiteDocument.encode(CampingFixtures.campsite(id, owner.id)),
                )
            }
            listOf("a", "b", "c", "unrelated", "draft", "cancelled").forEach { id ->
                var lottery = CampingFixtures.lottery(id, owner.id)
                if (id == "b") {
                    lottery =
                        lottery.copy(
                            configuration =
                                lottery.configuration.copy(
                                    registrationOpensAt =
                                        lottery.configuration.registrationOpensAt
                                            .minusSeconds(
                                                60,
                                            ),
                                ),
                        )
                }
                if (id == "draft") lottery = lottery.copy(publishedAt = null)
                if (id ==
                    "cancelled"
                ) {
                    lottery =
                        lottery.copy(
                            cancellation = Cancellation(firebase.clock.instant(), owner.id, null),
                        )
                }
                firebase.seed("lotteries/$id", LotteryDocument.encode(lottery))
                val site = if (id == "unrelated") "second" else "site"
                firebase.seed(
                    "lotteries/$id/offers/$site",
                    LotteryOfferDocument.encode(
                        LotteryOffer(site, listOf(CampingFixtures.arrival)),
                    ),
                )
            }
            firebase.seed(
                "lotteries/b/offers/second",
                LotteryOfferDocument.encode(
                    LotteryOffer("second", listOf(CampingFixtures.arrival)),
                ),
            )
            assertEquals(
                listOf(
                    "b",
                    "a",
                    "c",
                ),
                withTimeout(5000) {
                    entrant.container.lotteries
                        .observePublishedForCampsite("site")
                        .first { it.size == 3 }
                        .map { it.id }
                },
            )
            assertEquals(
                listOf(
                    "b",
                    "unrelated",
                ),
                withTimeout(5000) {
                    entrant.container.lotteries
                        .observePublishedForCampsite("second")
                        .first { it.size == 2 }
                        .map { it.id }
                },
            )
            // Verify the actual empty query at the server before accepting an empty cache value.
            assertTrue(
                entrant.clients.firestore
                    .collectionGroup("offers")
                    .whereEqualTo("campsiteId", "empty")
                    .get(Source.SERVER)
                    .await()
                    .isEmpty,
            )
            assertTrue(
                entrant.container.lotteries
                    .observePublishedForCampsite("empty")
                    .first()
                    .isEmpty(),
            )
        }

    /** One subscription reflects publication, offer removal/re-addition and cancellation. */
    @Test(timeout = 60000)
    fun entrantLookupStaysLiveAcrossPublicationAndOfferChanges() =
        runBlocking<Unit> {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val sites = owner.container.campsites
            sites.createDraft("site", owner.id, CampingFixtures.campsite().details)
            sites.publish("site", owner.id)
            sites.setOffered("site", owner.id, setOf(CampingFixtures.arrival), true)
            val lotteries = owner.container.lotteries
            lotteries.createDraft(
                "live",
                owner.id,
                "May",
                "Weekend",
                CampingFixtures.lottery().configuration,
            )
            lotteries.setOffer(
                "live",
                owner.id,
                LotteryOffer("site", listOf(CampingFixtures.arrival)),
            )
            val states = Channel<List<Lottery>>(Channel.UNLIMITED)
            val observation =
                launch {
                    try {
                        entrant.container.lotteries
                            .observePublishedForCampsite(
                                "site",
                            ).collect { states.send(it) }
                    } finally {
                        states.close()
                    }
                }
            try {
                assertEquals(emptyList<Lottery>(), withTimeout(5000) { states.receive() })
                lotteries.publish("live", owner.id)
                withTimeout(
                    5000,
                ) { states.receiveAsFlow().first { it.map(Lottery::id) == listOf("live") } }
                lotteries.removeOffer("live", owner.id, "site")
                withTimeout(5000) { states.receiveAsFlow().first { it.isEmpty() } }
                lotteries.setOffer(
                    "live",
                    owner.id,
                    LotteryOffer("site", listOf(CampingFixtures.arrival)),
                )
                withTimeout(5000) { states.receiveAsFlow().first { it.size == 1 } }
                lotteries.cancel("live", owner.id)
                withTimeout(5000) { states.receiveAsFlow().first { it.isEmpty() } }
            } finally {
                observation.cancelAndJoin()
            }
        }
}

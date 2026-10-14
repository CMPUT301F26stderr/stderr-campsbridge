package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns membership transitions and the waitlist count/capacity transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class WaitlistRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Repeated joins/withdrawals are harmless and rejoining records the new joining time. */
    @Test(timeout = 60000)
    fun membershipTransitionsKeepCountInSync() =
        runBlocking {
            val fixture = firebase.registrationFixture()
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val lotteries = entrant.container.lotteries
            lotteries.join("draw", entrant.id)
            lotteries.join("draw", entrant.id)
            assertEquals(1L, lotteries.get("draw")!!.waitingCount)
            val entryRef =
                entrant.clients.firestore.document(
                    "lotteries/draw/entries/${entrant.id}",
                )
            val joined = LotteryEntryDocument.decode(entryRef.get(Source.SERVER).await())
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            lotteries.withdraw("draw", entrant.id)
            lotteries.withdraw("draw", entrant.id)
            assertEquals(0L, lotteries.get("draw")!!.waitingCount)
            val withdrawn = LotteryEntryDocument.decode(entryRef.get(Source.SERVER).await())
            assertEquals(EntryState.WITHDRAWN, withdrawn.state)
            assertEquals(joined.joinedAt, withdrawn.joinedAt)
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            lotteries.join("draw", entrant.id)
            val rejoined = LotteryEntryDocument.decode(entryRef.get(Source.SERVER).await())
            assertEquals(EntryState.WAITING, rejoined.state)
            assertEquals(firebase.clock.instant(), rejoined.joinedAt)
            assertEquals(1L, lotteries.get("draw")!!.waitingCount)
            assertEquals(
                listOf(rejoined),
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first(),
            )
            assertEquals(listOf(rejoined), lotteries.observeForEntrant(entrant.id).first())
        }

    /** Concurrent joins for the last available slot create one member and one count increment. */
    @Test(timeout = 60000)
    fun concurrentJoinsCannotExceedCapacity() =
        runBlocking {
            val fixture = firebase.registrationFixture(capacity = 1)
            val entrants =
                listOf(
                    firebase.registeredActor(Role.ENTRANT),
                    firebase.registeredActor(Role.ENTRANT),
                )
            val start = CompletableDeferred<Unit>()
            val joins =
                entrants.map { entrant ->
                    async {
                        start.await()
                        runCatching { entrant.container.lotteries.join("draw", entrant.id) }
                    }
                }
            start.complete(Unit)
            val results = joins.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.isFailure })
            assertEquals(
                1L,
                fixture.owner.container.lotteries
                    .get("draw")!!
                    .waitingCount,
            )
            val entries =
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
            assertEquals(1, entries.size)
            assertEquals(EntryState.WAITING, entries.single().state)
            val rejected = entrants.single { it.id != entries.single().accountId }
            assertFalse(firebase.documentExists("lotteries/draw/entries/${rejected.id}"))
            assertPersistenceFailure(Reason.CAPACITY_REACHED) {
                rejected.container.lotteries.join("draw", rejected.id)
            }
        }

    /** Unlimited registration permits additional entrants rather than treating null as zero. */
    @Test(timeout = 60000)
    fun unlimitedCapacityAcceptsAdditionalEntrants() =
        runBlocking {
            val fixture = firebase.registrationFixture()
            for (entrant in listOf(
                firebase.registeredActor(Role.ENTRANT),
                firebase.registeredActor(Role.ENTRANT),
            )) {
                entrant.container.lotteries.join("draw", entrant.id)
            }
            assertEquals(
                2L,
                fixture.owner.container.lotteries
                    .get("draw")!!
                    .waitingCount,
            )
            assertEquals(
                2,
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
                    .size,
            )
        }

    /** Closing registration rejects further membership changes without changing entry or count. */
    @Test(timeout = 60000)
    fun closedRegistrationLeavesExistingMembershipUnchanged() =
        runBlocking {
            val fixture = firebase.registrationFixture()
            val entrant = firebase.registeredActor(Role.ENTRANT)
            entrant.container.lotteries.join("draw", entrant.id)
            val before =
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
            val closed =
                fixture.lottery.copy(
                    waitingCount = 1,
                    configuration =
                        fixture.lottery.configuration.copy(
                            registrationOpensAt = firebase.clock.instant().minusSeconds(7200),
                            registrationClosesAt = firebase.clock.instant().minusSeconds(3600),
                        ),
                )
            firebase.seed("lotteries/draw", LotteryDocument.encode(closed) + ("_revision" to 0L))
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                entrant.container.lotteries.withdraw("draw", entrant.id)
            }
            assertEquals(
                before,
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first(),
            )
            assertEquals(
                1L,
                entrant.container.lotteries
                    .get("draw")!!
                    .waitingCount,
            )
            assertTrue(firebase.documentExists("lotteries/draw/entries/${entrant.id}"))
        }
}

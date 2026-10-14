package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.random.Random

/** Owns draw outcomes, the completion barrier and deterministic recovery from persisted work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class LotteryDrawTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** A completed draw assigns eligible entrants once, leaves withdrawals and consumes waiting count. */
    @Test(timeout = 60000)
    fun drawCompletesEligibleMembershipOnlyOnce() =
        runBlocking {
            val fixture = firebase.drawFixture()
            val withdrawn = firebase.registeredActor(Role.ENTRANT)
            val withdrawnEntry =
                LotteryEntry(
                    "draw",
                    withdrawn.id,
                    EntryState.WITHDRAWN,
                    firebase.clock.instant().minusSeconds(7200),
                    firebase.clock.instant().minusSeconds(3600),
                )
            firebase.seed(
                "lotteries/draw/entries/${withdrawn.id}",
                LotteryEntryDocument.encode(withdrawnEntry),
            )
            val lotteries = fixture.owner.container.lotteries
            assertEquals(DrawResult.COMPLETED, lotteries.draw("draw"))
            val entries = lotteries.observeEntries("draw").first()
            assertEquals(2, entries.count { it.state == EntryState.OFFERED })
            assertEquals(1, entries.count { it.state == EntryState.LOST })
            assertEquals(withdrawnEntry, entries.single { it.accountId == withdrawn.id })
            val completed = lotteries.get("draw")!!
            assertEquals(0L, completed.waitingCount)
            assertNotNull(completed.drawnAt)
            for (entry in entries.filter { it.state != EntryState.WITHDRAWN }) {
                val type = if (entry.state == EntryState.OFFERED) "LOTTERY_WON" else "LOTTERY_LOST"
                val eventId = DocumentKeys.compound(type, "draw", entry.accountId)
                val job =
                    fixture.owner.clients.firestore
                        .document(
                            "_notificationDeliveries/$eventId",
                        ).get(Source.SERVER)
                        .await()
                assertEquals("draw", job.getString("waitForDraw"))
                assertEquals(listOf(entry.accountId), job.get("recipientIds"))
            }
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            assertEquals(DrawResult.ALREADY_DRAWN, lotteries.draw("draw"))
            assertEquals(completed, lotteries.get("draw"))
            assertEquals(entries.toSet(), lotteries.observeEntries("draw").first().toSet())
        }

    /** Empty lotteries still receive a draw-completion marker. */
    @Test(timeout = 60000)
    fun emptyDrawCompletes() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 0, winners = 5)
            assertEquals(
                DrawResult.COMPLETED,
                fixture.owner.container.lotteries
                    .draw("draw"),
            )
            assertTrue(
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
                    .isEmpty(),
            )
        }

    /** Scarce membership selects fewer winners than the configured maximum. */
    @Test(timeout = 60000)
    fun fewerEntrantsThanWinnerSlotsAllWin() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 1, winners = 5)
            assertEquals(
                DrawResult.COMPLETED,
                fixture.owner.container.lotteries
                    .draw("draw"),
            )
            assertEquals(
                EntryState.OFFERED,
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
                    .single()
                    .state,
            )
        }

    /** Restarting mid-draw keeps the original seed, winners and already committed outcome timestamp. */
    @Test(timeout = 60000)
    fun interruptedDrawReusesSeedAndPreservesCommittedOutcome() =
        runBlocking {
            val fixture = firebase.drawFixture()
            fixture.owner.container.lotteries
                .draw("draw")
            val baseline =
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
            val preserved = baseline.first { it.state == EntryState.OFFERED }
            val seedRef =
                fixture.owner.clients.firestore
                    .document("_draws/draw")
            val seed = seedRef.get(Source.SERVER).await().getString("seed")
            for (entry in baseline.filter { it.accountId != preserved.accountId }) {
                firebase.seed(
                    "lotteries/draw/entries/${entry.accountId}",
                    LotteryEntryDocument.encode(entry.copy(state = EntryState.WAITING)),
                )
                val type = if (entry.state == EntryState.OFFERED) "LOTTERY_WON" else "LOTTERY_LOST"
                val eventId = DocumentKeys.compound(type, "draw", entry.accountId)
                firebase.erase("notifications/$eventId")
                firebase.erase("_notificationDeliveries/$eventId")
            }
            firebase.seed(
                "lotteries/draw",
                LotteryDocument.encode(
                    fixture.lottery.copy(waitingCount = 2),
                ) + ("_revision" to 0L),
            )
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            val restarted =
                FirestoreLotteryRepository(
                    fixture.owner.clients.firestore,
                    firebase.clock,
                    Random(999),
                )
            assertEquals(DrawResult.COMPLETED, restarted.draw("draw"))
            val recovered = restarted.observeEntries("draw").first()
            assertEquals(
                baseline.associate { it.accountId to it.state },
                recovered.associate {
                    it.accountId to
                        it.state
                },
            )
            assertEquals(preserved, recovered.single { it.accountId == preserved.accountId })
            assertEquals(seed, seedRef.get(Source.SERVER).await().getString("seed"))
            assertEquals(0L, restarted.get("draw")!!.waitingCount)
        }

    /** Concurrent operators converge on one outcome set with no duplicate event/work records. */
    @Test(timeout = 60000)
    fun concurrentDrawsConvergeOnOneOutcomeSet() =
        runBlocking {
            val fixture = firebase.drawFixture()
            val start = CompletableDeferred<Unit>()
            val operators =
                (1..2).map { seed ->
                    FirestoreLotteryRepository(
                        fixture.owner.clients.firestore,
                        firebase.clock,
                        Random(seed),
                    )
                }
            val draws =
                operators.map { operator ->
                    async {
                        start.await()
                        runCatching { operator.draw("draw") }
                    }
                }
            start.complete(Unit)
            val results = draws.awaitAll()
            assertTrue(results.any { it.isSuccess })
            val entries =
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
            assertEquals(2, entries.count { it.state == EntryState.OFFERED })
            assertEquals(1, entries.count { it.state == EntryState.LOST })
            assertEquals(
                0L,
                fixture.owner.container.lotteries
                    .get("draw")!!
                    .waitingCount,
            )
            val admin = firebase.registeredActor(Role.ADMIN)
            assertEquals(
                3,
                admin.container.notifications
                    .observeAll()
                    .first()
                    .size,
            )
        }

    /** Cancelling an already-started draw finishes its frozen outcomes before recording cancellation. */
    @Test(timeout = 60000)
    fun cancellationFinishesStartedDraw() =
        runBlocking {
            val fixture = firebase.drawFixture()
            firebase.seed("_draws/draw", mapOf("seed" to "persisted-seed"))
            fixture.owner.container.lotteries
                .cancel("draw", fixture.owner.id, "Cancelled")
            val cancelled =
                fixture.owner.container.lotteries
                    .get("draw")!!
            assertNotNull(cancelled.drawnAt)
            assertNotNull(cancelled.cancellation)
            assertEquals(0L, cancelled.waitingCount)
            assertTrue(
                fixture.owner.container.lotteries.observeEntries("draw").first().all {
                    it.state == EntryState.OFFERED || it.state == EntryState.LOST
                },
            )
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            fixture.owner.container.lotteries
                .cancel("draw", fixture.owner.id, "Retry")
            assertEquals(
                cancelled,
                fixture.owner.container.lotteries
                    .get("draw"),
            )
        }
}

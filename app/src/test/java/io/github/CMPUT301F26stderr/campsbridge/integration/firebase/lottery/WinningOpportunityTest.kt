package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns declining unused wins; reservation consumption is owned by booking tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class WinningOpportunityTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Declining is idempotent, retains its first timestamp and never schedules a replacement draw. */
    @Test(timeout = 60000)
    fun decliningUnusedWinPreservesCompletedDraw() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 1, winners = 1)
            val entrant = fixture.entrants.single()
            val completed =
                fixture.lottery.copy(
                    waitingCount = 0,
                    drawnAt = firebase.clock.instant(),
                )
            firebase.seed("lotteries/draw", LotteryDocument.encode(completed) + ("_revision" to 0L))
            firebase.seed(
                "lotteries/draw/entries/${entrant.id}",
                LotteryEntryDocument.encode(
                    LotteryEntry(
                        "draw",
                        entrant.id,
                        EntryState.OFFERED,
                        firebase.clock.instant().minusSeconds(7200),
                        firebase.clock.instant(),
                    ),
                ),
            )
            entrant.container.lotteries.decline("draw", entrant.id)
            val entryRef =
                entrant.clients.firestore.document(
                    "lotteries/draw/entries/${entrant.id}",
                )
            val declined = LotteryEntryDocument.decode(entryRef.get(Source.SERVER).await())
            assertEquals(EntryState.DECLINED, declined.state)
            firebase.clock.current = firebase.clock.current.plusSeconds(60)
            entrant.container.lotteries.decline("draw", entrant.id)
            assertEquals(declined, LotteryEntryDocument.decode(entryRef.get(Source.SERVER).await()))
            assertEquals(completed, entrant.container.lotteries.get("draw"))
        }

    /** Losing entries and expired booking windows cannot be turned into declines. */
    @Test(timeout = 60000)
    fun onlyUnusedWinsInBookingWindowCanBeDeclined() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 2, winners = 1)
            val entrant = fixture.entrants.first()
            val completed =
                fixture.lottery.copy(
                    waitingCount = 0,
                    drawnAt = firebase.clock.instant(),
                )
            firebase.seed("lotteries/draw", LotteryDocument.encode(completed) + ("_revision" to 0L))
            val loser =
                LotteryEntry(
                    "draw",
                    entrant.id,
                    EntryState.LOST,
                    firebase.clock.instant().minusSeconds(7200),
                    firebase.clock.instant(),
                )
            firebase.seed(
                "lotteries/draw/entries/${entrant.id}",
                LotteryEntryDocument.encode(loser),
            )
            assertPersistenceFailure(Reason.NOT_ALLOWED) {
                entrant.container.lotteries.decline("draw", entrant.id)
            }
            firebase.seed(
                "lotteries/draw/entries/${entrant.id}",
                LotteryEntryDocument.encode(loser.copy(state = EntryState.OFFERED)),
            )
            firebase.clock.current = completed.configuration.bookingClosesAt
            assertPersistenceFailure(Reason.WINDOW_CLOSED) {
                entrant.container.lotteries.decline("draw", entrant.id)
            }
            val stored =
                LotteryEntryDocument.decode(
                    entrant.clients.firestore
                        .document(
                            "lotteries/draw/entries/${entrant.id}",
                        ).get(Source.SERVER)
                        .await(),
                )
            assertEquals(EntryState.OFFERED, stored.state)
        }
}

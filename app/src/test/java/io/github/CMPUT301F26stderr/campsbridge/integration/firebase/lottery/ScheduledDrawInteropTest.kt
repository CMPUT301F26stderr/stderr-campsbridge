package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.lottery

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.erase
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.BufferedReader
import java.io.File
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Proves that Node and the authenticated Android repository share one resumable draw protocol. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ScheduledDrawInteropTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Scheduled execution preserves the mobile seed and every already committed outcome. */
    @Test(timeout = 120000)
    fun workerResumesMobileOutcomes() =
        runBlocking {
            val fixture = firebase.drawFixture()
            val lotteries = fixture.owner.container.lotteries
            lotteries.draw("draw")
            val baseline = lotteries.observeEntries("draw").first()
            val seed =
                fixture.owner.clients.firestore
                    .document("_draws/draw")
                    .get()
                    .await()
                    .getString("seed")
            resetUnfinished(fixture, baseline)
            worker(firebase.clock.instant(), "99").use {
                it.start()
                it.awaitCompletion()
            }
            assertEquals(
                seed,
                fixture.owner.clients.firestore
                    .document("_draws/draw")
                    .get()
                    .await()
                    .getString("seed"),
            )
            assertOutcomes(fixture, baseline)
        }

    /** The mobile path resumes a server seed and keeps its earlier outcome timestamp. */
    @Test(timeout = 120000)
    fun mobileResumesWorkerOutcomes() =
        runBlocking {
            val fixture = firebase.drawFixture()
            worker(firebase.clock.instant(), "17").use {
                it.start()
                it.awaitCompletion()
            }
            val baseline =
                fixture.owner.container.lotteries
                    .observeEntries("draw")
                    .first()
            assertTrue(baseline.all { it.state == EntryState.OFFERED || it.state == EntryState.LOST })
            resetUnfinished(fixture, baseline)
            firebase.clock.current = firebase.clock.current.plusSeconds(10)
            fixture.owner.container.lotteries
                .draw("draw")
            worker(firebase.clock.instant(), "99").use {
                it.start()
                it.awaitCompletion()
            }
            assertEquals(
                "17",
                fixture.owner.clients.firestore
                    .document("_draws/draw")
                    .get()
                    .await()
                    .getString("seed"),
            )
            assertOutcomes(fixture, baseline)
        }

    /** Concurrent server and authenticated owner execution converge; the next tick drains remaining work. */
    @Test(timeout = 120000)
    fun workerAndMobileExecutionConverge() =
        runBlocking {
            val fixture = firebase.drawFixture(entrants = 8)
            val lotteries = fixture.owner.container.lotteries
            lotteries.get("draw")
            worker(firebase.clock.instant(), "17").use { server ->
                val scheduled =
                    async(Dispatchers.IO) {
                        server.start()
                        server.awaitCompletion()
                    }
                val foreground = async { lotteries.draw("draw") }
                scheduled.await()
                foreground.await()
            }
            worker(firebase.clock.instant(), "99").use {
                it.start()
                it.awaitCompletion()
            }
            val entries = lotteries.observeEntries("draw").first()
            assertEquals(
                fixture.lottery.configuration.winnerCount
                    .toInt(),
                entries.count { it.state == EntryState.OFFERED },
            )
            assertTrue(entries.all { it.state == EntryState.OFFERED || it.state == EntryState.LOST })
            assertOutcomes(fixture, entries)
        }

    /** Restores a crash boundary after the first entry committed, retaining its seed and event.
     * @param fixture Original parent before drawing.
     * @param baseline Confirmed results used to retain one committed entry.
     */
    private suspend fun resetUnfinished(
        fixture: DrawFixture,
        baseline: List<LotteryEntry>,
    ) {
        for (entry in baseline.drop(1)) {
            firebase.seed(
                "lotteries/draw/entries/${entry.accountId}",
                LotteryEntryDocument.encode(entry.copy(state = EntryState.WAITING)),
            )
            val event = outcomeId(entry)
            firebase.erase("notifications/$event")
            firebase.erase("lotteries/draw/_notificationDeliveries/$event")
        }
        firebase.seed(
            "lotteries/draw",
            LotteryDocument.encode(fixture.lottery.copy(waitingCount = baseline.size.toLong() - 1)) +
                ("_revision" to 0L),
        )
    }

    /** Checks committed outcomes, completion and the actual entrant Inbox after both implementations ran.
     * @param fixture Actors and parent under test.
     * @param baseline Expected outcome identities; only the first timestamp is retained across partial replay.
     */
    private suspend fun assertOutcomes(
        fixture: DrawFixture,
        baseline: List<LotteryEntry>,
    ) {
        val lotteries = fixture.owner.container.lotteries
        val actual = lotteries.observeEntries("draw").first()
        assertEquals(baseline.associate { it.accountId to it.state }, actual.associate { it.accountId to it.state })
        assertEquals(baseline.first(), actual.single { it.accountId == baseline.first().accountId })
        assertEquals(0L, lotteries.get("draw")!!.waitingCount)
        assertNotNull(lotteries.get("draw")!!.drawnAt)
        for (actor in fixture.entrants) {
            val entry = actual.single { it.accountId == actor.id }
            assertTrue(
                actor.container.notifications
                    .getInbox(actor.id)
                    .any { it.notificationId == outcomeId(entry) },
            )
        }
    }

    /** Uses the same immutable event identity as both production implementations.
     * @param entry Saved winning or losing outcome.
     */
    private fun outcomeId(entry: LotteryEntry): String =
        DocumentKeys.compound(
            if (entry.state == EntryState.OFFERED) "LOTTERY_WON" else "LOTTERY_LOST",
            "draw",
            entry.accountId,
        )

    /** Starts the real Node worker, warming its emulator connection before the test releases it.
     * @param now Controlled operation timestamp.
     * @param seed Proposed seed, ignored when another implementation already saved one.
     */
    private fun worker(
        now: Instant,
        seed: String,
    ): WorkerProcess {
        val root =
            generateSequence(File(".").canonicalFile) {
                it.parentFile
            }.first { File(it, "firebase/functions").isDirectory }
        val config =
            JSONObject()
                .put("projectId", firebase.projectId)
                .put("seconds", now.epochSecond)
                .put("nanos", now.nano)
                .put("seed", seed)
        val process =
            ProcessBuilder("node", "test-support/pending-work-cli.js", config.toString())
                .directory(File(root, "firebase/functions"))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        val result = WorkerProcess(process, process.inputStream.bufferedReader())
        try {
            val readiness = CompletableFuture.supplyAsync { result.output.readLine() }
            assertEquals("READY", readiness.get(15, TimeUnit.SECONDS))
            return result
        } catch (failure: Throwable) {
            result.close()
            throw failure
        }
    }
}

/** Owns only the child process used by one cross-runtime contract.
 * @param process Node process inheriting this run's leased emulator address.
 * @param output Worker readiness and failure output.
 */
private class WorkerProcess(
    private val process: Process,
    val output: BufferedReader,
) : AutoCloseable {
    /** Releases the initialized worker alongside the foreground operation. */
    fun start() {
        process.outputStream.bufferedWriter().apply {
            write("START\n")
            flush()
        }
    }

    /** Requires a terminal successful worker result rather than treating process startup as success. */
    fun awaitCompletion() {
        assertTrue("Node worker timed out", process.waitFor(45, TimeUnit.SECONDS))
        assertEquals(output.readText(), 0, process.exitValue())
    }

    /** Stops only this contract's child if an assertion or coroutine exits early. */
    override fun close() {
        if (process.isAlive) process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
        output.close()
        process.outputStream.close()
    }
}

package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Uses the same expected vectors as Node, without deriving expectations from either implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawOrderTest {
    /** Signed seeds, non-ASCII identities, scarce entrants and exact Long limits agree across runtimes. */
    @Test fun sharedWorkerVectorsMatchTheAndroidDrawOrder() {
        val root =
            generateSequence(File(".").canonicalFile) { it.parentFile }
                .first { File(it, "firebase/fixtures/draw-order-vectors.json").isFile }
        val vectors = JSONArray(File(root, "firebase/fixtures/draw-order-vectors.json").readText())
        for (index in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(index)
            val ids = vector.getJSONArray("accountIds").strings()
            val expected = vector.getJSONArray("winners").strings()
            val seed = vector.getString("seed")
            val count = vector.getString("winnerCount").toLong()
            assertEquals(expected, selectDrawWinners(seed, ids, count))
            assertEquals(expected, selectDrawWinners(seed, ids.reversed(), count))
        }
    }

    /** Reads the string array from the shared, versioned test fixture. */
    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { getString(it) }
}

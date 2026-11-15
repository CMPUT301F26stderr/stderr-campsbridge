package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import java.security.MessageDigest

/** Reproduces a persisted seed's winner order across Android and the scheduled worker.
 * @param seed Persisted decimal draw seed.
 * @param accountIds Frozen, unique candidate identities, including applied outcomes on resume.
 * @param winnerCount Validated positive winner limit.
 */
internal fun selectDrawWinners(
    seed: String,
    accountIds: List<String>,
    winnerCount: Long,
): List<String> {
    val ranked =
        accountIds.map { account ->
            val hash =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest("$seed:$account".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            account to hash
        }
    return ranked
        .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
        .take(winnerCount.coerceAtMost(accountIds.size.toLong()).toInt())
        .map { it.first }
}

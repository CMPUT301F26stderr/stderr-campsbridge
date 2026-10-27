package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Read-only lottery fixture adapter; distributed writes remain unsupported. */
internal class TaskLotteryRepository : LotteryRepository by DemoLotteryRepository() {
    /** Returns the seeded draw when its actual identity matches.
     * @param id Requested saved identity.
     */
    override suspend fun get(id: String): Lottery? =
        TaskExampleData.lottery.takeIf { it.id == id }

    /** Observes one fixed saved draw.
     * @param id Requested identity.
     */
    override fun observe(id: String): Flow<Lottery?> =
        flowOf(
            TaskExampleData.lottery.takeIf {
                it.id ==
                    id
            },
        )

    /** Supplies saved allocations for the exact draw.
     * @param id Requested identity.
     */
    override fun observeOffers(id: String): Flow<List<LotteryOffer>> =
        flowOf(
            if (id ==
                TaskExampleData.lottery.id
            ) {
                TaskExampleData.offers
            } else {
                emptyList()
            },
        )

    /** Supplies only the requested draw's entries.
     * @param id Requested identity.
     */
    override fun observeEntries(id: String): Flow<List<LotteryEntry>> =
        flowOf(
            TaskExampleData.entries.filter {
                it.lotteryId ==
                    id
            },
        )

    /** Supplies only the current entrant's participation.
     * @param accountId Requested entrant identity.
     */
    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
        flowOf(
            TaskExampleData.entries.filter {
                it.accountId ==
                    accountId
            },
        )
}

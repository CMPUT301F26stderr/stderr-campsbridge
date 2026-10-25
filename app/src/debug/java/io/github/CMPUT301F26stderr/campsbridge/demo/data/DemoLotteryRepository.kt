package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Empty offline reader; distributed lottery mutations are unavailable in the demo. */
class DemoLotteryRepository : LotteryRepository {
    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    override suspend fun get(id: String): Lottery? =
        null

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    override fun observe(id: String): Flow<Lottery?> =
        flowOf(null)

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    override fun observeAll(): Flow<List<Lottery>> =
        flowOf(emptyList())

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Lottery>> =
        flowOf(emptyList())

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    override fun observeOffers(id: String): Flow<List<LotteryOffer>> =
        flowOf(emptyList())

    /**
     * Reads current allocations from the server for draft restoration and write recovery.
     * A confirmed empty result is distinct from a failed read; cached absence is insufficient.
     * @param id Lottery document ID.
     * @return All confirmed offers, in unspecified order; empty when none exist.
     * @throws Exception When the server read or a stored allocation's decoding fails.
     */
    override suspend fun getOffers(id: String): List<LotteryOffer> =
        emptyList()

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    override fun observeEntries(id: String): Flow<List<LotteryEntry>> =
        flowOf(emptyList())

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
        flowOf(emptyList())

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Stable draft identity allocated and retained before the first attempt.
     * @param ownerId Active owner creating the draft.
     * @param title Initial display title.
     * @param description Initial description.
     * @param configuration Initial registration/booking windows and limits.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ): Lottery =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param title Display title for the lottery or announcement.
     * @param description Listing or lottery description.
     * @param configuration Registration/booking windows and participation limits.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param offer Campsite/night allocation to store.
     */
    override suspend fun setOffer(
        id: String,
        ownerId: String,
        offer: LotteryOffer,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param campsiteId Campsite document ID.
     */
    override suspend fun removeOffer(
        id: String,
        ownerId: String,
        campsiteId: String,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun join(
        id: String,
        accountId: String,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun withdraw(
        id: String,
        accountId: String,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun decline(
        id: String,
        accountId: String,
    ) =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     */
    override suspend fun draw(id: String): DrawResult =
        unavailable()

    /**
     * Rejects this distributed operation in the offline demo.
     *
     * @param id Lottery document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    override suspend fun cancel(
        id: String,
        actorId: String,
        reason: String?,
    ) =
        unavailable()

    /**
     * Observes associated published, noncancelled lotteries.
     * @param campsiteId Published campsite whose lottery memberships are requested.
     */
    override fun observePublishedForCampsite(campsiteId: String): Flow<List<Lottery>> =
        flowOf(emptyList())

    /** Rejects distributed operations rather than pretending that the demo persisted them. */
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("Lottery writes require the Firebase host")
}

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fixed offline records; distributed lottery mutations remain unavailable.
 * @param records Supplied parent records.
 * @param offers Allocations grouped by their parent identity.
 * @param entries Supplied membership facts.
 */
class DemoLotteryRepository(
    private val records: List<Lottery> = emptyList(),
    private val offers: Map<String, List<LotteryOffer>> = emptyMap(),
    private val entries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {
    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    override suspend fun get(id: String): Lottery? =
        records.firstOrNull { it.id == id }

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    override fun observe(id: String): Flow<Lottery?> =
        flowOf(records.firstOrNull { it.id == id })

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    override fun observeAll(): Flow<List<Lottery>> =
        flowOf(records)

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Lottery>> =
        flowOf(records.filter { it.ownerId == ownerId })

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    override fun observeOffers(id: String): Flow<List<LotteryOffer>> =
        flowOf(offers[id].orEmpty())

    /**
     * Reads supplied local allocations for draft inspection; performs no server request.
     * @param id Lottery document ID.
     * @return Supplied allocations; empty when the fixture has none.
     */
    override suspend fun getOffers(id: String): List<LotteryOffer> =
        offers[id].orEmpty()

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    override fun observeEntries(id: String): Flow<List<LotteryEntry>> =
        flowOf(entries.filter { it.lotteryId == id })

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
        flowOf(entries.filter { it.accountId == accountId })

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
        flowOf(
            records.filter {
                it.publishedAt != null && it.cancellation == null &&
                    offers[it.id].orEmpty().any { offer -> offer.campsiteId == campsiteId }
            },
        )

    /** Rejects distributed operations rather than pretending that the demo persisted them. */
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("Lottery writes require the Firebase host")
}

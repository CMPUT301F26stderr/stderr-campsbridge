package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import java.time.Clock

/**
 * Firebase-backed implementation for lottery configuration, membership, resumable draws, and
 * cancellation.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 * @param random Injectable source for proposing the persisted draw seed.
 */
class FirestoreLotteryRepository(
    db: FirebaseFirestore,
    clock: Clock,
    random: kotlin.random.Random = kotlin.random.Random.Default,
) : LotteryRepository {
    private val queries = LotteryQueries(db)
    private val draws = LotteryDraw(db, clock, random)
    private val writes = LotteryWrites(db, clock)
    private val membership = LotteryMembership(db, clock)
    private val cancellation = LotteryCancellation(db, clock, draws)

    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    override suspend fun get(id: String) =
        queries.get(id)

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    override fun observe(id: String) =
        queries.observe(id)

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    override fun observeAll() =
        queries.observeAll()

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override fun observeForOwner(ownerId: String) =
        queries.observeForOwner(ownerId)

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    override fun observeOffers(id: String) =
        queries.observeOffers(id)

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    override fun observeEntries(id: String) =
        queries.observeEntries(id)

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    override fun observeForEntrant(accountId: String) =
        queries.observeForEntrant(accountId)

    /**
     * Creates one draft transactionally; retries preserve stored configuration and lifecycle.
     * Reads confirmed server values after commit; a read failure leaves creation safe to retry.
     * @param id Stable caller-allocated draft identity.
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
        writes.createDraft(id, ownerId, title, description, configuration)

    /**
     * Updates descriptions and permitted configuration; published configuration freezes at
     * registration start.
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
        writes.update(id, ownerId, title, description, configuration)

    /**
     * Validates owned offered nights and replaces an allocation while advancing the revision
     * guard.
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
        writes.setOffer(id, ownerId, offer)

    /**
     * Removes an editable campsite allocation and advances the revision guard.
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
        writes.removeOffer(id, ownerId, campsiteId)

    /**
     * Validates published campsites and offered nights before publication; a revision guard
     * rejects concurrent allocation edits and registration must still be in the future.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) =
        writes.publish(id, ownerId)

    /**
     * Joins or rejoins during registration, updating membership and waiting count
     * atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun join(
        id: String,
        accountId: String,
    ) =
        membership.join(id, accountId)

    /**
     * Withdraws during registration, updating membership and waiting count atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun withdraw(
        id: String,
        accountId: String,
    ) =
        membership.withdraw(id, accountId)

    /**
     * Declines an unused winning opportunity during booking; existing reservations prevent
     * decline.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    override suspend fun decline(
        id: String,
        accountId: String,
    ) =
        membership.decline(id, accountId)

    /** Starts or resumes the same deterministic operation used by foreground recovery.
     * @param id Lottery whose closed registration is being drawn.
     */
    override suspend fun draw(id: String): DrawResult =
        draws.draw(id)

    /**
     * Records owner/admin cancellation, finishing any already-started draw before
     * cancellation.
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
        cancellation.cancel(id, actorId, reason)

    /**
     * Only removal jobs call this after the original actor has already been authorized.
     *
     * @param id Lottery document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    internal suspend fun cancelForRemoval(
        id: String,
        actorId: String,
        reason: String?,
    ) =
        cancellation.cancelForRemoval(id, actorId, reason)

    /**
     * Observes canonical campsite memberships and their owner's published lottery records.
     * @param campsiteId Published campsite whose noncancelled lotteries are requested.
     */
    override fun observePublishedForCampsite(campsiteId: String) =
        queries.publishedForCampsite(campsiteId)
}

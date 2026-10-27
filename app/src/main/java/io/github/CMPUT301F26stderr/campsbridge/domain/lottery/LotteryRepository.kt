package io.github.CMPUT301F26stderr.campsbridge.domain.lottery

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for lottery configuration, membership, resumable draws, and cancellation.
 */
interface LotteryRepository {
    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    suspend fun get(id: String): Lottery?

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    fun observe(id: String): Flow<Lottery?>

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    fun observeAll(): Flow<List<Lottery>>

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    fun observeForOwner(ownerId: String): Flow<List<Lottery>>

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    fun observeOffers(id: String): Flow<List<LotteryOffer>>

    /**
     * Reads current allocations from the server for draft restoration and write recovery.
     * A confirmed empty result is distinct from a failed read; cached absence is insufficient.
     * @param id Lottery document ID.
     * @return All confirmed offers, in unspecified order; empty when none exist.
     * @throws Exception When the server read or a stored allocation's decoding fails.
     */
    suspend fun getOffers(id: String): List<LotteryOffer>

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    fun observeEntries(id: String): Flow<List<LotteryEntry>>

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>>

    /**
     * Creates a caller-identified unpublished draft, or returns the same owner's stored record.
     * Existing facts are never overwritten by a creation retry; other ownership conflicts.
     * @param id Stable draft identity allocated and retained before the first attempt.
     * @param ownerId Active owner creating the draft.
     * @param title Initial display title.
     * @param description Initial description.
     * @param configuration Initial registration/booking windows and limits.
     */
    suspend fun createDraft(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ): Lottery

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
    suspend fun update(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    )

    /**
     * Validates owned offered nights and replaces an allocation while advancing the revision
     * guard.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param offer Campsite/night allocation to store.
     */
    suspend fun setOffer(
        id: String,
        ownerId: String,
        offer: LotteryOffer,
    )

    /**
     * Removes an editable campsite allocation and advances the revision guard.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param campsiteId Campsite document ID.
     */
    suspend fun removeOffer(
        id: String,
        ownerId: String,
        campsiteId: String,
    )

    /**
     * Validates published campsites and offered nights before publication; a revision guard
     * rejects concurrent allocation edits and registration must still be in the future.
     *
     * @param id Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    suspend fun publish(
        id: String,
        ownerId: String,
    )

    /**
     * Joins or rejoins during registration, updating membership and waiting count
     * atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun join(
        id: String,
        accountId: String,
    )

    /**
     * Withdraws during registration, updating membership and waiting count atomically.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun withdraw(
        id: String,
        accountId: String,
    )

    /**
     * Declines an unused winning opportunity during booking; existing reservations prevent
     * decline.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     */
    suspend fun decline(
        id: String,
        accountId: String,
    )

    /**
     * Starts or resumes a deterministic seeded draw after registration closes. Outcomes and
     * receipt jobs commit per entry; draw completion gates booking and result delivery.
     * Returns whether already complete.
     *
     * @param id Lottery document ID.
     */
    suspend fun draw(id: String): DrawResult

    /**
     * Records owner/admin cancellation, finishing any already-started draw before
     * cancellation.
     *
     * @param id Lottery document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    suspend fun cancel(
        id: String,
        actorId: String,
        reason: String? = null,
    )

    /**
     * Observes associated published, noncancelled lotteries.
     * @param campsiteId Published campsite whose lottery memberships are requested.
     */
    fun observePublishedForCampsite(campsiteId: String): Flow<List<Lottery>>
}

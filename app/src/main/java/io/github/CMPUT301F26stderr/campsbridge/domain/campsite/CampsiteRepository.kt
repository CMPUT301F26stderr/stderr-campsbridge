package io.github.CMPUT301F26stderr.campsbridge.domain.campsite

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for listing publication and owner-managed per-night inventory.
 */
interface CampsiteRepository {
    /**
     * Returns the stored campsite, or null when its document is absent.
     *
     * @param id Campsite document ID.
     */
    suspend fun get(id: String): Campsite?

    /**
     * Observes one campsite, emitting null if its document is absent.
     *
     * @param id Campsite document ID.
     */
    fun observe(id: String): Flow<Campsite?>

    /**
     * Observes published, nonremoved campsites suitable for discovery.
     */
    fun observePublished(): Flow<List<Campsite>>

    /**
     * Observes listings belonging to the owner, including drafts and retained records.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    fun observeForOwner(ownerId: String): Flow<List<Campsite>>

    /**
     * Observes all readable campsite records, including drafts and removal history.
     */
    fun observeAll(): Flow<List<Campsite>>

    /**
     * Creates a caller-identified unpublished draft, or returns the same owner's stored record.
     * Existing facts are never overwritten by a creation retry; other ownership conflicts.
     * @param id Stable draft identity allocated and retained before the first attempt.
     * @param ownerId Active owner creating the draft.
     * @param details Initial validated listing facts, used only if the document is absent.
     */
    suspend fun createDraft(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    ): Campsite

    /**
     * Replaces editable listing details without changing ownership or publication history.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param details Editable campsite facts to store.
     */
    suspend fun update(
        id: String,
        ownerId: String,
        details: CampsiteDetails,
    )

    /**
     * Sets the first publication time; an already published listing keeps its original time.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    suspend fun publish(
        id: String,
        ownerId: String,
    )

    /**
     * Observes stored nights in the half-open date range; missing nights are not offered.
     *
     * @param id Campsite document ID.
     * @param start Inclusive first local date in the range.
     * @param endExclusive Exclusive end of the observed calendar range.
     */
    fun observeCalendar(
        id: String,
        start: java.time.LocalDate,
        endExclusive: java.time.LocalDate,
    ): Flow<List<CalendarNight>>

    /**
     * Changes each night in its own transaction; earlier nights may remain saved after
     * failure. Withdrawing an occupied night requires reservation cancellation first.
     *
     * @param id Campsite document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     * @param dates Local calendar nights to update.
     * @param offered Whether to offer or withdraw the requested nights.
     */
    suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<java.time.LocalDate>,
        offered: Boolean,
    )
}

package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationWrites
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.DrawResult
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.ALREADY_USED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CAPACITY_REACHED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_FOUND
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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
    private val db: FirebaseFirestore,
    private val clock: Clock,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) : LotteryRepository {
    /**
     * Builds a validated reference to the lottery document.
     *
     * @param id Lottery document ID.
     */
    private fun ref(id: String) =
        db.document(FirestorePaths.lottery(id))

    /**
     * Returns the lottery record, or null when absent.
     *
     * @param id Lottery document ID.
     */
    override suspend fun get(id: String) =
        ref(id).read(LotteryDocument)

    /**
     * Observes one lottery, emitting null when its document is absent.
     *
     * @param id Lottery document ID.
     */
    override fun observe(id: String) =
        ref(id).observe(LotteryDocument)

    /**
     * Observes all readable lotteries, including drafts and historical records.
     */
    override fun observeAll() =
        db.collection("lotteries").observe(LotteryDocument)

    /**
     * Observes lotteries created by the specified owner.
     *
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    override fun observeForOwner(ownerId: String) =
        db.collection("lotteries").whereEqualTo("ownerId", ownerId).observe(LotteryDocument)

    /**
     * Observes the campsite/night allocations belonging to a lottery.
     *
     * @param id Lottery document ID.
     */
    override fun observeOffers(id: String) =
        ref(id).collection("offers").observe(LotteryOfferDocument)

    /**
     * Observes participation records; callers show draw outcomes only after draw completion.
     *
     * @param id Lottery document ID.
     */
    override fun observeEntries(id: String) =
        ref(id).collection("entries").observe(LotteryEntryDocument)

    /**
     * Observes an account’s entries across lotteries using the queryable account ID.
     *
     * @param accountId Account ID whose participation or data is addressed.
     */
    override fun observeForEntrant(accountId: String) =
        db
            .collectionGroup("entries")
            .whereEqualTo("accountId", accountId)
            .observe(LotteryEntryDocument)

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
    ): Lottery {
        val reference = db.document(FirestorePaths.lottery(id))
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val existing = tx.read(reference, LotteryDocument)
            if (existing != null) {
                requireOperation(
                    existing.ownerId == ownerId,
                    CONFLICT,
                    "Draft ID belongs to another owner",
                )
                existing
            } else {
                val draft =
                    Lottery(id, ownerId, title, description, configuration, 0, clock.instant())
                tx.set(reference, LotteryDocument.encode(draft) + ("_revision" to 0L))
                draft
            }
        }
        return reference.read(LotteryDocument)
            ?: throw PersistenceFailure(PersistenceFailure.Reason.NOT_FOUND, reference.path)
    }

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
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            requireOperation(
                lottery.ownerId == ownerId && lottery.cancellation == null,
                NOT_ALLOWED,
                "Lottery is not editable",
            )
            requireOperation(
                lottery.configuration == configuration ||
                    lottery.publishedAt == null ||
                    clock.instant() < lottery.configuration.registrationOpensAt,
                WINDOW_CLOSED,
                "Lottery configuration is frozen",
            )
            val changed =
                lottery.copy(
                    title = title,
                    description = description,
                    configuration = configuration,
                )
            tx.set(
                ref(id),
                LotteryDocument.encode(changed) +
                    ("_revision" to ((snapshot.getLong("_revision") ?: 0) + 1)),
            )
        }
    }

    /**
     * Rejects a foreign, cancelled, or frozen lottery before changing its offered inventory.
     *
     * @param lottery Lottery document ID.
     * @param ownerId Account ID of the campsite or lottery owner.
     */
    private fun editable(
        lottery: Lottery,
        ownerId: String,
    ) {
        requireOperation(
            lottery.ownerId == ownerId && lottery.cancellation == null,
            NOT_ALLOWED,
            "Lottery is not editable",
        )
        requireOperation(
            (
                lottery.publishedAt == null ||
                    clock.instant() < lottery.configuration.registrationOpensAt
            ) &&
                lottery.drawnAt == null,
            WINDOW_CLOSED,
            "Offers are frozen",
        )
    }

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
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            val lottery = LotteryDocument.decode(snapshot)
            editable(lottery, ownerId)
            val site =
                tx.require(db.document(FirestorePaths.campsite(offer.campsiteId)), CampsiteDocument)
            requireOperation(
                site.ownerId == ownerId && site.removal == null,
                NOT_ALLOWED,
                "Campsite is not owned by lottery owner",
            )
            val nights =
                offer.nights.map {
                    tx.read(db.document(FirestorePaths.night(site.id, it)), CalendarNightDocument)
                }
            requireOperation(
                nights.all { it?.isOffered == true },
                INVALID_STATE,
                "Calendar nights must be offered first",
            )
            tx.set(
                db.document(FirestorePaths.offer(id, site.id)),
                LotteryOfferDocument.encode(offer),
            )
            tx.update(ref(id), "_revision", (snapshot.getLong("_revision") ?: 0) + 1)
        }
    }

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
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val snapshot = tx.get(ref(id))
            editable(LotteryDocument.decode(snapshot), ownerId)
            tx.delete(db.document(FirestorePaths.offer(id, campsiteId)))
            tx.update(ref(id), "_revision", (snapshot.getLong("_revision") ?: 0) + 1)
        }
    }

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
    ) {
        // Query membership outside the transaction, then guard it with the parent revision.
        val snapshot = ref(id).get(Source.SERVER).await()
        requireOperation(snapshot.exists(), NOT_FOUND, "Lottery missing")
        val revision = snapshot.getLong("_revision") ?: 0
        val offers = ref(id).collection("offers").read(LotteryOfferDocument)
        requireOperation(offers.isNotEmpty(), INVALID_STATE, "Lottery needs an offer")
        db.atomic { tx ->
            tx.activeAccount(db, ownerId, Role.OWNER)
            val current = tx.get(ref(id))
            val lottery = LotteryDocument.decode(current)
            requireOperation(
                (current.getLong("_revision") ?: 0) == revision,
                CONFLICT,
                "Offers changed; retry publication",
            )
            editable(lottery, ownerId)
            requireOperation(
                clock.instant() < lottery.configuration.registrationOpensAt,
                WINDOW_CLOSED,
                "Move registration into the future before publishing",
            )
            for (offer in offers) {
                val site =
                    tx.require(
                        db.document(FirestorePaths.campsite(offer.campsiteId)),
                        CampsiteDocument,
                    )
                requireOperation(
                    site.isPublished && site.ownerId == ownerId,
                    INACTIVE,
                    "Offered campsite must be published",
                )
                for (date in offer.nights) {
                    requireOperation(
                        tx
                            .read(
                                db.document(FirestorePaths.night(site.id, date)),
                                CalendarNightDocument,
                            )?.isOffered == true,
                        INVALID_STATE,
                        "Offered date withdrawn",
                    )
                }
            }
            tx.update(ref(id), "publishedAt", (lottery.publishedAt ?: clock.instant()).stored())
        }
    }

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
        membership(id, accountId, true)

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
        membership(id, accountId, false)

    /**
     * Applies an idempotent join/withdraw transaction while validating the window and
     * capacity.
     *
     * @param id Lottery document ID.
     * @param accountId Account ID whose participation or data is addressed.
     * @param joining True to join/rejoin, false to withdraw.
     */
    private suspend fun membership(
        id: String,
        accountId: String,
        joining: Boolean,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, accountId, Role.ENTRANT)
            val lottery = tx.require(ref(id), LotteryDocument)
            val now = clock.instant()
            requireOperation(
                lottery.registrationIsOpen(now),
                WINDOW_CLOSED,
                "Registration is closed",
            )
            tx.activeAccount(db, lottery.ownerId, Role.OWNER)
            val entryRef = db.document(FirestorePaths.entry(id, accountId))
            val old = tx.read(entryRef, LotteryEntryDocument)
            if (joining && old?.state == EntryState.WAITING) return@atomic
            if (!joining && (old == null || old.state == EntryState.WITHDRAWN)) return@atomic
            requireOperation(
                old == null || old.state in setOf(EntryState.WAITING, EntryState.WITHDRAWN),
                INVALID_STATE,
                "Entry cannot change membership",
            )
            val count = lottery.waitingCount + if (joining) 1 else -1
            requireOperation(
                !joining ||
                    lottery.configuration.waitlistCapacity == null ||
                    count <= lottery.configuration.waitlistCapacity,
                CAPACITY_REACHED,
                "Waitlist is full",
            )
            requireOperation(count >= 0, INVALID_STATE, "Invalid waiting count")
            val entry =
                LotteryEntry(
                    id,
                    accountId,
                    if (joining) EntryState.WAITING else EntryState.WITHDRAWN,
                    if (joining) now else old!!.joinedAt,
                    now,
                )
            tx.set(entryRef, LotteryEntryDocument.encode(entry))
            tx.update(ref(id), "waitingCount", count)
        }
    }

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
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, accountId, Role.ENTRANT)
            val lottery = tx.require(ref(id), LotteryDocument)
            requireOperation(
                lottery.bookingIsOpen(clock.instant()),
                WINDOW_CLOSED,
                "Booking window is closed",
            )
            val entryRef = db.document(FirestorePaths.entry(id, accountId))
            val entry = tx.require(entryRef, LotteryEntryDocument)
            val reservation =
                tx.read(
                    db.document(
                        FirestorePaths.reservation(DocumentKeys.reservation(id, accountId)),
                    ),
                    ReservationDocument,
                )
            requireOperation(reservation == null, ALREADY_USED, "Offer already used")
            if (entry.state == EntryState.DECLINED) return@atomic
            requireOperation(entry.state == EntryState.OFFERED, NOT_ALLOWED, "Entry did not win")
            tx.set(
                entryRef,
                LotteryEntryDocument.encode(
                    entry.copy(state = EntryState.DECLINED, stateChangedAt = clock.instant()),
                ),
            )
        }
    }

    /**
     * Starts or resumes a deterministic seeded draw after registration closes. Outcomes and
     * receipt jobs commit per entry; draw completion gates booking and result delivery.
     * Returns whether already complete.
     *
     * @param id Lottery document ID.
     */
    override suspend fun draw(id: String): DrawResult {
        val drawRef = db.collection("_draws").document(DocumentKeys.id(id))
        val proposedSeed = random.nextLong().toString()
        val seed =
            db.atomic { tx ->
                val lottery = tx.require(ref(id), LotteryDocument)
                val previous = tx.get(drawRef)
                if (lottery.drawnAt != null) return@atomic null
                requireOperation(
                    lottery.publishedAt != null && lottery.cancellation == null,
                    INACTIVE,
                    "Lottery is inactive",
                )
                requireOperation(
                    clock.instant() >= lottery.configuration.registrationClosesAt,
                    WINDOW_CLOSED,
                    "Registration has not closed",
                )
                if (previous.exists()) {
                    Fields(previous.reference.path, previous.data!!).string("seed")
                } else {
                    tx.set(drawRef, mapOf("seed" to proposedSeed))
                    // Conflict with in-flight membership changes; their retry rechecks the
                    // deadline.
                    tx.update(ref(id), "waitingCount", lottery.waitingCount)
                    proposedSeed
                }
            } ?: return DrawResult.ALREADY_DRAWN
        val lottery = get(id) ?: throw PersistenceFailure(NOT_FOUND, "Lottery missing")
        // After the freeze barrier, membership is fixed. Include applied outcomes on resume.
        val candidates =
            ref(id).collection("entries").read(LotteryEntryDocument).filter {
                it.state != EntryState.WITHDRAWN
            }

        /**
         * Hashes the persisted draw seed and account ID to reproduce candidate order on
         * retries.
         *
         * @param account Account document ID.
         */
        fun rank(account: String): String =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest("$seed:$account".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        val winners =
            candidates
                .sortedWith(compareBy<LotteryEntry> { rank(it.accountId) }.thenBy { it.accountId })
                .take(
                    lottery.configuration.winnerCount
                        .coerceAtMost(candidates.size.toLong())
                        .toInt(),
                ).map { it.accountId }
                .toSet()
        for (candidate in candidates) {
            db.atomic { tx ->
                val current = tx.require(ref(id), LotteryDocument)
                if (current.drawnAt != null) return@atomic
                requireOperation(current.cancellation == null, INACTIVE, "Lottery cancelled")
                val entryRef = db.document(FirestorePaths.entry(id, candidate.accountId))
                val entry = tx.require(entryRef, LotteryEntryDocument)
                if (entry.state != EntryState.WAITING) return@atomic
                val selected = entry.accountId in winners
                val now = clock.instant()
                requireOperation(current.waitingCount > 0, INVALID_STATE, "Invalid waiting count")
                tx.set(
                    entryRef,
                    LotteryEntryDocument.encode(
                        entry.copy(
                            state = if (selected) EntryState.OFFERED else EntryState.LOST,
                            stateChangedAt = now,
                        ),
                    ),
                )
                tx.update(ref(id), "waitingCount", current.waitingCount - 1)
                val type =
                    if (selected) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST
                val eventId = DocumentKeys.compound(type.name, id, entry.accountId)
                NotificationWrites.enqueue(
                    db,
                    tx,
                    Notification(
                        eventId,
                        null,
                        type,
                        NotificationSubject(SubjectType.LOTTERY, id),
                        null,
                        now,
                    ),
                    listOf(entry.accountId),
                    waitForDraw = id,
                )
            }
        }
        db.atomic { tx ->
            val current = tx.require(ref(id), LotteryDocument)
            if (current.drawnAt != null) return@atomic
            requireOperation(
                current.cancellation == null && current.waitingCount == 0L,
                INVALID_STATE,
                "Draw incomplete or cancelled",
            )
            tx.update(ref(id), "drawnAt", clock.instant().stored())
        }
        return DrawResult.COMPLETED
    }

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
        cancelCoordinated(id, actorId, reason, authorize = true)

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
        cancelCoordinated(id, actorId, reason, authorize = false)

    /**
     * Serializes draw start and cancellation; completes an in-progress draw before retrying
     * cancellation.
     *
     * @param id Lottery document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     * @param authorize Whether this call must authorize the actor rather than trust a prior
     * removal check.
     */
    private suspend fun cancelCoordinated(
        id: String,
        actorId: String,
        reason: String?,
        authorize: Boolean,
    ) {
        while (true) {
            val finishDraw =
                db.atomic { tx ->
                    val actor = if (authorize) tx.activeAccount(db, actorId) else null
                    val lottery = tx.require(ref(id), LotteryDocument)
                    val drawStarted = tx.get(db.collection("_draws").document(id)).exists()
                    requireOperation(
                        !authorize || actorId == lottery.ownerId || Role.ADMIN in actor!!.roles,
                        NOT_ALLOWED,
                        "Not owner or admin",
                    )
                    if (lottery.cancellation != null) return@atomic false
                    // Read both the draw marker and lottery so a concurrent draw start conflicts.
                    if (drawStarted && lottery.drawnAt == null) return@atomic true
                    tx.update(
                        ref(id),
                        "cancellation",
                        Cancellation(clock.instant(), actorId, reason).stored(),
                    )
                    false
                }
            if (!finishDraw) return
            // Complete already-started outcomes before cancellation; a retry uses the same seed.
            draw(id)
        }
    }

    /**
     * Draws due active lotteries and resumes interrupted draws; inactive races are skipped.
     */
    suspend fun resumeDraws() {
        for (lottery in db.collection("lotteries").read(LotteryDocument)) {
            if (
                lottery.publishedAt != null &&
                lottery.cancellation == null &&
                lottery.drawnAt == null &&
                clock.instant() >= lottery.configuration.registrationClosesAt
            ) {
                try {
                    draw(lottery.id)
                } catch (failure: PersistenceFailure) {
                    if (failure.reason != INACTIVE) throw failure
                }
            }
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
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
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.ALREADY_USED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CAPACITY_REACHED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.security.MessageDigest
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Local lottery coordination against the same inventory used by booking.
 * @param records Initial helper parents.
 * @param offers Initial helper allocations.
 * @param entries Initial helper participation.
 * @param store Shared demonstration state.
 */
class DemoLotteryRepository(
    records: List<Lottery> = emptyList(),
    offers: Map<String, List<LotteryOffer>> = emptyMap(),
    entries: List<LotteryEntry> = emptyList(),
    private val store: DemoStore =
        DemoStore(
            buildMap {
                records.forEach { put(Paths.lottery(it.id), LotteryDocument.encode(it)) }
                offers.forEach { (id, values) ->
                    values.forEach { put(Paths.offer(id, it.campsiteId), LotteryOfferDocument.encode(it)) }
                }
                entries.forEach { put(Paths.entry(it.lotteryId, it.accountId), LotteryEntryDocument.encode(it)) }
            },
        ),
) : LotteryRepository {
    /** Reads one saved parent.
     * @param id Lottery identity.
     */
    override suspend fun get(id: String) =
        store.read { get(Paths.lottery(id), LotteryDocument) }

    /** Observes one saved parent.
     * @param id Lottery identity.
     */
    override fun observe(id: String) =
        store.observe { get(Paths.lottery(id), LotteryDocument) }

    /** Observes all current parents. */
    override fun observeAll() =
        store.observe { list("lotteries", LotteryDocument) }

    /** Observes one owner's parents.
     * @param ownerId Owner identity.
     */
    override fun observeForOwner(ownerId: String) =
        store.observe {
            list("lotteries", LotteryDocument).filter {
                it.ownerId ==
                    ownerId
            }
        }

    /** Observes current allocations.
     * @param id Lottery identity.
     */
    override fun observeOffers(id: String) =
        store.observe { list("${Paths.lottery(id)}/offers", LotteryOfferDocument) }

    /** Reads current committed allocations.
     * @param id Lottery identity.
     */
    override suspend fun getOffers(id: String) =
        store.read { list("${Paths.lottery(id)}/offers", LotteryOfferDocument) }

    /** Observes participation for the saved parent.
     * @param id Lottery identity.
     */
    override fun observeEntries(id: String) =
        store.observe { list("${Paths.lottery(id)}/entries", LotteryEntryDocument) }

    /** Observes the person's entries across parents.
     * @param accountId Entrant identity.
     */
    override fun observeForEntrant(accountId: String) =
        store.observe {
            list(
                "lotteries",
                LotteryDocument,
            ).flatMap { list("${Paths.lottery(it.id)}/entries", LotteryEntryDocument) }.filter {
                it.accountId ==
                    accountId
            }
        }

    /** Creates a draft once, preserving saved facts on retry.
     * @param id Stable draft identity.
     * @param ownerId Active owner.
     * @param title Initial title.
     * @param description Initial description.
     * @param configuration Initial windows and limits.
     */
    override suspend fun createDraft(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ): Lottery =
        store.change {
            active(ownerId, Role.OWNER)
            val old = get(Paths.lottery(id), LotteryDocument)
            requireOperation(old == null || old.ownerId == ownerId, CONFLICT, "Draft belongs to another owner")
            old
                ?: Lottery(id, ownerId, title, description, configuration, 0, store.clock.instant()).also {
                    put(Paths.lottery(id), LotteryDocument, it)
                }
        }

    /** Saves descriptions and unfrozen configuration.
     * @param id Lottery identity.
     * @param ownerId Active owner.
     * @param title New title.
     * @param description New description.
     * @param configuration Requested windows and limits.
     */
    override suspend fun update(
        id: String,
        ownerId: String,
        title: String,
        description: String,
        configuration: LotteryConfiguration,
    ) {
        store.change {
            val old = owned(id, ownerId)
            requireOperation(
                configuration == old.configuration || editable(old),
                WINDOW_CLOSED,
                "Configuration is frozen",
            )
            put(
                Paths.lottery(id),
                LotteryDocument,
                old.copy(title = title, description = description, configuration = configuration),
            )
        }
    }

    /** Validates the owner's offered inventory before saving an allocation.
     * @param id Lottery identity.
     * @param ownerId Active owner.
     * @param offer Requested allocation.
     */
    override suspend fun setOffer(
        id: String,
        ownerId: String,
        offer: LotteryOffer,
    ) {
        store.change {
            requireOperation(editable(owned(id, ownerId)), WINDOW_CLOSED, "Offers are frozen")
            validateOffer(ownerId, offer, false)
            put(Paths.offer(id, offer.campsiteId), LotteryOfferDocument, offer)
        }
    }

    /** Removes an unfrozen allocation.
     * @param id Lottery identity.
     * @param ownerId Active owner.
     * @param campsiteId Allocation identity.
     */
    override suspend fun removeOffer(
        id: String,
        ownerId: String,
        campsiteId: String,
    ) {
        store.change {
            requireOperation(editable(owned(id, ownerId)), WINDOW_CLOSED, "Offers are frozen")
            raw.remove(Paths.offer(id, campsiteId))
        }
    }

    /** Publishes validated allocations before registration opens.
     * @param id Lottery identity.
     * @param ownerId Active owner.
     */
    override suspend fun publish(
        id: String,
        ownerId: String,
    ) {
        store.change {
            val old = owned(id, ownerId)
            requireOperation(
                editable(old) && store.clock.instant() < old.configuration.registrationOpensAt,
                WINDOW_CLOSED,
                "Registration must be in the future",
            )
            val allocations = list("${Paths.lottery(id)}/offers", LotteryOfferDocument)
            requireOperation(allocations.isNotEmpty(), INVALID_STATE, "Choose offered nights")
            allocations.forEach { validateOffer(ownerId, it, true) }
            put(Paths.lottery(id), LotteryDocument, old.copy(publishedAt = old.publishedAt ?: store.clock.instant()))
        }
    }

    /** Joins within registration, enforcing capacity and idempotent retries.
     * @param id Lottery identity.
     * @param accountId Active entrant.
     */
    override suspend fun join(
        id: String,
        accountId: String,
    ) {
        membership(id, accountId, true)
    }

    /** Withdraws an existing waiting entry during registration.
     * @param id Lottery identity.
     * @param accountId Active entrant.
     */
    override suspend fun withdraw(
        id: String,
        accountId: String,
    ) {
        membership(id, accountId, false)
    }

    /** Declines an unused offer, keeping an already applied decline idempotent.
     * @param id Lottery identity.
     * @param accountId Active entrant.
     */
    override suspend fun decline(
        id: String,
        accountId: String,
    ) {
        store.change {
            active(accountId, Role.ENTRANT)
            val parent = require(Paths.lottery(id), LotteryDocument)
            val entry = require(Paths.entry(id, accountId), LotteryEntryDocument)
            requireOperation(
                list("reservations", ReservationDocument).none {
                    it.lotteryId == id &&
                        it.entrantId == accountId
                },
                ALREADY_USED,
                "Offer already consumed",
            )
            if (entry.state != EntryState.DECLINED) {
                requireOperation(parent.bookingIsOpen(store.clock.instant()), WINDOW_CLOSED, "Booking is closed")
                requireOperation(entry.state == EntryState.OFFERED, NOT_ALLOWED, "No winning offer")
                put(
                    Paths.entry(id, accountId),
                    LotteryEntryDocument,
                    entry.copy(state = EntryState.DECLINED, stateChangedAt = store.clock.instant()),
                )
            }
        }
    }

    /** Draws once using a reproducible demo seed; outcomes and receipts commit together.
     * @param id Lottery identity.
     */
    override suspend fun draw(id: String): DrawResult =
        store.change {
            val parent = require(Paths.lottery(id), LotteryDocument)
            if (parent.drawnAt != null) return@change DrawResult.ALREADY_DRAWN
            requireOperation(parent.publishedAt != null && parent.cancellation == null, INACTIVE, "Lottery inactive")
            active(parent.ownerId, Role.OWNER)
            requireOperation(
                store.clock.instant() >= parent.configuration.registrationClosesAt,
                WINDOW_CLOSED,
                "Registration still open",
            )
            val candidates =
                list("${Paths.lottery(id)}/entries", LotteryEntryDocument).filter {
                    it.state ==
                        EntryState.WAITING
                }
            val winners =
                candidates
                    .sortedBy {
                        rank(id, it.accountId)
                    }.take(
                        parent.configuration.winnerCount
                            .coerceAtMost(candidates.size.toLong())
                            .toInt(),
                    ).map { it.accountId }
                    .toSet()
            val now = store.clock.instant()
            candidates.forEach { entry ->
                val won = entry.accountId in winners
                put(
                    Paths.entry(id, entry.accountId),
                    LotteryEntryDocument,
                    entry.copy(state = if (won) EntryState.OFFERED else EntryState.LOST, stateChangedAt = now),
                )
                val type = if (won) NotificationType.LOTTERY_WON else NotificationType.LOTTERY_LOST
                notify(
                    Notification(
                        DocumentKeys.compound(type.name, id, entry.accountId),
                        null,
                        type,
                        NotificationSubject(SubjectType.LOTTERY, id),
                        null,
                        now,
                    ),
                    listOf(entry.accountId),
                )
            }
            put(Paths.lottery(id), LotteryDocument, parent.copy(waitingCount = 0, drawnAt = now))
            DrawResult.COMPLETED
        }

    /** Records an authorized owner/admin cancellation once.
     * @param id Lottery identity.
     * @param actorId Active actor.
     * @param reason Optional recorded explanation.
     */
    override suspend fun cancel(
        id: String,
        actorId: String,
        reason: String?,
    ) {
        store.change {
            val actor = active(actorId)
            val old = require(Paths.lottery(id), LotteryDocument)
            requireOperation(
                old.ownerId == actorId || Role.ADMIN in actor.roles,
                NOT_ALLOWED,
                "Different lottery owner",
            )
            put(
                Paths.lottery(id),
                LotteryDocument,
                old.copy(
                    cancellation =
                        old.cancellation ?: Cancellation(store.clock.instant(), actorId, reason),
                ),
            )
        }
    }

    /** Observes current published allocations for a public listing.
     * @param campsiteId Listing identity.
     */
    override fun observePublishedForCampsite(campsiteId: String) =
        store.observe {
            list("lotteries", LotteryDocument).filter {
                it.publishedAt != null && it.cancellation == null &&
                    get(Paths.offer(it.id, campsiteId), LotteryOfferDocument) != null
            }
        }

    /** Changes entry and waiting count together.
     * @param id Lottery identity.
     * @param accountId Active entrant.
     * @param joining Requested membership.
     */
    private suspend fun membership(
        id: String,
        accountId: String,
        joining: Boolean,
    ) {
        store.change {
            active(accountId, Role.ENTRANT)
            val parent = require(Paths.lottery(id), LotteryDocument)
            active(parent.ownerId, Role.OWNER)
            val old = get(Paths.entry(id, accountId), LotteryEntryDocument)
            if ((joining && old?.state == EntryState.WAITING) ||
                (!joining && old?.state == EntryState.WITHDRAWN)
            ) {
                return@change
            }
            requireOperation(parent.registrationIsOpen(store.clock.instant()), WINDOW_CLOSED, "Registration is closed")
            if (!joining && old == null) return@change
            requireOperation(
                old == null || old.state in setOf(EntryState.WAITING, EntryState.WITHDRAWN),
                INVALID_STATE,
                "Entry already drawn",
            )
            val count = parent.waitingCount + if (joining) 1 else -1
            requireOperation(
                !joining || parent.configuration.waitlistCapacity == null ||
                    count <= parent.configuration.waitlistCapacity,
                CAPACITY_REACHED,
                "Waitlist full",
            )
            requireOperation(count >= 0, INVALID_STATE, "Invalid waiting count")
            val now = store.clock.instant()
            put(
                Paths.entry(id, accountId),
                LotteryEntryDocument,
                LotteryEntry(
                    id,
                    accountId,
                    if (joining) EntryState.WAITING else EntryState.WITHDRAWN,
                    if (joining) now else old!!.joinedAt,
                    now,
                ),
            )
            put(Paths.lottery(id), LotteryDocument, parent.copy(waitingCount = count))
        }
    }

    /** Checks actor authority for an editable parent.
     * @param id Lottery identity.
     * @param ownerId Active owner.
     */
    private fun DemoDocuments.owned(
        id: String,
        ownerId: String,
    ): Lottery {
        active(ownerId, Role.OWNER)
        return require(Paths.lottery(id), LotteryDocument).also {
            requireOperation(it.ownerId == ownerId && it.cancellation == null, NOT_ALLOWED, "Lottery unavailable")
        }
    }

    /** Checks the registration freeze boundary.
     * @param value Saved lottery.
     */
    private fun editable(value: Lottery) =
        value.drawnAt == null &&
            (value.publishedAt == null || store.clock.instant() < value.configuration.registrationOpensAt)

    /** Checks ownership, publication and offered dates.
     * @param ownerId Lottery owner.
     * @param offer Proposed allocation.
     * @param published Whether publication requires a published listing.
     */
    private fun DemoDocuments.validateOffer(
        ownerId: String,
        offer: LotteryOffer,
        published: Boolean,
    ) {
        val site = require(Paths.campsite(offer.campsiteId), CampsiteDocument)
        requireOperation(
            site.ownerId == ownerId && site.removal == null && (!published || site.isPublished),
            INACTIVE,
            "Campsite unavailable",
        )
        requireOperation(
            offer.nights.all {
                get(Paths.night(site.id, it), CalendarNightDocument)?.isOffered == true
            },
            INVALID_STATE,
            "Offer dates in the calendar first",
        )
    }

    /** Gives the offline demonstration a reproducible draw order.
     * @param lotteryId Saved draw identity.
     * @param accountId Candidate identity.
     */
    private fun rank(
        lotteryId: String,
        accountId: String,
    ) =
        MessageDigest.getInstance("SHA-256").digest("demo:$lotteryId:$accountId".toByteArray()).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
}

/*
 * Keeps offline moderation and resumable removal scoped to their recorded targets.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.Fields
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.removal
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.LocalDate
import java.time.ZoneId
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Shares saved moderation outcomes with every offline person.
 * @param store Durable records and common clock.
 * @param images Local attachment cleanup owner.
 * @param bookings Existing coordinated cancellation owner.
 * @param lotteries Existing lottery cancellation owner.
 */
class DemoModerationRepository(
    private val store: DemoStore,
    private val images: ImageRepository,
    private val bookings: DemoBookingRepository,
    private val lotteries: DemoLotteryRepository,
) : ModerationRepository {
    /** Clears only stars, preserving the review's text and children.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Active administrator.
     */
    override suspend fun removeRating(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) =
        clearFeedback(campsiteId, authorId, adminId) { it.copy(rating = null) }

    /** Clears only text while retaining its rating and thread.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Active administrator.
     */
    override suspend fun removeReviewText(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) =
        clearFeedback(campsiteId, authorId, adminId) { it.copy(body = null) }

    /** Saves a real component removal without changing timestamps on a no-op.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Acting administrator.
     * @param clear Pure component removal.
     */
    private suspend fun clearFeedback(
        campsiteId: String,
        authorId: String,
        adminId: String,
        clear: (Review) -> Review,
    ) {
        store.change {
            active(adminId, Role.ADMIN)
            val path = Paths.review(campsiteId, authorId)
            val current = require(path, ReviewDocument)
            val changed = clear(current)
            if (changed != current) put(path, ReviewDocument, changed.copy(updatedAt = store.clock.instant()))
        }
    }

    /** Marks or resumes one account's removal, keeping its first attribution after role loss.
     * @param accountId Target account.
     * @param actorId Self-removing person or administrator.
     * @param reason Original reason if this is the first request.
     */
    override suspend fun removeAccount(
        accountId: String,
        actorId: String,
        reason: String?,
    ) {
        val target = ImageTarget(ImageTargetType.ACCOUNT, accountId)
        store.change {
            val current = require(Paths.account(accountId), AccountDocument)
            val recorded = current.removal
            if (recorded != null) {
                authorizeRecorded(recorded, actorId)
            } else {
                if (accountId != actorId) active(actorId, Role.ADMIN) else active(actorId)
            }
            val removal = recorded ?: Removal(store.clock.instant(), actorId, reason)
            put(
                Paths.account(accountId),
                AccountDocument,
                current.copy(
                    name = "",
                    email = null,
                    phone = null,
                    roles = emptySet(),
                    preferences = Preferences(),
                    settings = AccountSettings(notificationsEnabled = false),
                    removal = removal,
                ),
            )
            raw[jobPath(target)] = removal.stored()
        }
        resume(target)
    }

    /** Marks or resumes one listing's removal without reassigning its recorded actor.
     * @param campsiteId Target campsite.
     * @param actorId Actual owner or administrator.
     * @param reason Original reason if this is the first request.
     */
    override suspend fun removeCampsite(
        campsiteId: String,
        actorId: String,
        reason: String?,
    ) {
        val target = ImageTarget(ImageTargetType.CAMPSITE, campsiteId)
        store.change {
            val site = require(Paths.campsite(campsiteId), CampsiteDocument)
            val recorded = site.removal
            if (recorded != null) {
                authorizeRecorded(recorded, actorId)
            } else {
                val actor = active(actorId)
                requireOperation(
                    actorId == site.ownerId || Role.ADMIN in actor.roles,
                    NOT_ALLOWED,
                    "Not owner or admin",
                )
            }
            val removal = recorded ?: Removal(store.clock.instant(), actorId, reason)
            put(Paths.campsite(campsiteId), CampsiteDocument, site.copy(removal = removal))
            raw[jobPath(target)] = removal.stored()
        }
        resume(target)
    }

    /** Drains saved local jobs for the explicit administrative maintenance entry point. */
    override suspend fun resumeRemovals() {
        while (true) {
            val targets =
                store.read {
                    raw.keys.filter { it.startsWith("_removals/") }.map { DocumentKeys.target(it.substringAfter('/')) }
                }
            if (targets.isEmpty()) return
            for (target in targets) resume(target)
        }
    }

    /** Keeps continuation scoped to the first actor or an active administrator.
     * @param removal Saved authority.
     * @param actorId Requesting person.
     */
    private fun DemoDocuments.authorizeRecorded(
        removal: Removal,
        actorId: String,
    ) {
        if (actorId != removal.actorId) active(actorId, Role.ADMIN)
    }

    /** Resolves the existing durable removal-job identity.
     * @param target Account or campsite slot.
     */
    private fun jobPath(target: ImageTarget): String =
        "_removals/${DocumentKeys.attachment(target)}"

    /** Resumes one target's financial, lottery and attachment work, then retires its job.
     * @param target Previously authorized target.
     */
    private suspend fun resume(target: ImageTarget) {
        val path = jobPath(target)
        val removal = store.read { raw[path]?.let { Fields(path, it).removal() } } ?: return
        if (target.type ==
            ImageTargetType.ACCOUNT
        ) {
            cleanupAccount(target.id, removal)
        } else {
            cleanupSite(target.id, removal)
        }
        check(images.delete(target).obsoleteBlobId == null) { "Image cleanup is still pending" }
        store.change { raw.remove(path) }
    }

    /** Scrubs dependent active participation while retaining historical account relationships.
     * @param accountId Removed identity.
     * @param removal First recorded attribution.
     */
    private suspend fun cleanupAccount(
        accountId: String,
        removal: Removal,
    ) {
        val sites =
            store.change {
                val owned = list("campsites", CampsiteDocument).filter { it.ownerId == accountId }
                for (site in owned) {
                    val first = site.removal ?: removal
                    put(Paths.campsite(site.id), CampsiteDocument, site.copy(removal = first))
                    raw[jobPath(ImageTarget(ImageTargetType.CAMPSITE, site.id))] = first.stored()
                }
                val now = store.clock.instant()
                for (lottery in list("lotteries", LotteryDocument)) {
                    val entry = get(Paths.entry(lottery.id, accountId), LotteryEntryDocument)
                    if (entry?.state == EntryState.WAITING && now < lottery.configuration.registrationClosesAt) {
                        requireOperation(lottery.waitingCount > 0, INVALID_STATE, "Invalid waiting count")
                        put(
                            Paths.entry(lottery.id, accountId),
                            LotteryEntryDocument,
                            entry.copy(state = EntryState.WITHDRAWN, stateChangedAt = now),
                        )
                        put(
                            Paths.lottery(lottery.id),
                            LotteryDocument,
                            lottery.copy(waitingCount = lottery.waitingCount - 1),
                        )
                    }
                }
                raw.keys
                    .filter {
                        it.startsWith(
                            "${Paths.account(accountId)}/devices/",
                        )
                    }.toList()
                    .forEach { raw.remove(it) }
                owned
            }
        val organized = store.read { list("lotteries", LotteryDocument).filter { it.ownerId == accountId } }
        for (lottery in organized) cancelActiveLottery(lottery.id, removal)
        val stays = store.read { list("reservations", ReservationDocument).filter { it.entrantId == accountId } }
        for (stay in stays) cancelFuture(stay.id, removal)
        for (site in sites) resume(ImageTarget(ImageTargetType.CAMPSITE, site.id))
    }

    /** Cancels only lotteries offering this site and refunds this site's uncompleted stays.
     * @param campsiteId Removed listing.
     * @param removal Original listing removal.
     */
    private suspend fun cleanupSite(
        campsiteId: String,
        removal: Removal,
    ) {
        val affected =
            store.read {
                val site = require(Paths.campsite(campsiteId), CampsiteDocument)
                list("lotteries", LotteryDocument).filter {
                    site.ownerId in it.participantOwnerIds &&
                        get(Paths.offer(it.id, campsiteId), LotteryOfferDocument) != null
                }
            }
        for (lottery in affected) cancelActiveLottery(lottery.id, removal)
        val stays = store.read { list("reservations", ReservationDocument).filter { it.campsiteId == campsiteId } }
        for (stay in stays) cancelFuture(stay.id, removal)
    }

    /** Preserves ended lottery history during removal.
     * @param id Affected lottery.
     * @param removal Recorded removal authority.
     */
    private suspend fun cancelActiveLottery(
        id: String,
        removal: Removal,
    ) {
        val lottery = store.read { require(Paths.lottery(id), LotteryDocument) }
        if (lottery.cancellation == null &&
            (lottery.publishedAt == null || lottery.configuration.bookingClosesAt > store.clock.instant())
        ) {
            lotteries.cancelForRemoval(id, removal)
        }
    }

    /** Reuses the booking owner for uncompleted stays and leaves completed history intact.
     * @param id Affected stay.
     * @param removal Recorded removal authority.
     */
    private suspend fun cancelFuture(
        id: String,
        removal: Removal,
    ) {
        val stay = store.read { require(Paths.reservation(id), ReservationDocument) }
        val today = LocalDate.ofInstant(store.clock.instant(), ZoneId.of(stay.bookedCampsite.timeZone))
        if (stay.cancellation == null && stay.checkOutDate > today) bookings.cancelForRemoval(id, removal)
    }
}

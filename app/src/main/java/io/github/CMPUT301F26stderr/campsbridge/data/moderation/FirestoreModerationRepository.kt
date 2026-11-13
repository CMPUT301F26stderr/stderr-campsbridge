/*
 * Removal marks the target first; durable jobs resume the dependent cleanup.
 */

package io.github.CMPUT301F26stderr.campsbridge.data.moderation

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.CallableFunctions
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.Clock

/**
 * Removal marks the target first; durable jobs resume the dependent cleanup.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param images Image repository used to remove attachments and queue blob cleanup.
 * @param lotteries Lottery implementation providing coordinated cancellation during removal.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 * @param functions Trusted booking client configured for the same environment.
 */
class FirestoreModerationRepository(
    private val db: FirebaseFirestore,
    images: ImageRepository,
    lotteries: FirestoreLotteryRepository,
    private val clock: Clock,
    functions: CallableFunctions = CallableFunctions(db.app),
) : ModerationRepository {
    private val processor = RemovalProcessor(db, images, lotteries, clock, functions)

    /** Clears only the stored stars, retaining text, thread and original creation time.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Acting administrator.
     */
    override suspend fun removeRating(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) =
        clearFeedback(campsiteId, authorId, adminId) { it.copy(rating = null) }

    /** Clears only written feedback without changing its remaining stars or thread.
     * @param campsiteId Feedback campsite.
     * @param authorId Feedback author.
     * @param adminId Acting administrator.
     */
    override suspend fun removeReviewText(
        campsiteId: String,
        authorId: String,
        adminId: String,
    ) =
        clearFeedback(campsiteId, authorId, adminId) { it.copy(body = null) }

    /** Applies one administrative removal; an already-empty component leaves all timestamps unchanged.
     * @param campsiteId Feedback campsite.
     * @param authorId Stored author identity.
     * @param adminId Active administrator.
     * @param clear Pure edit of only the selected feedback component.
     */
    private suspend fun clearFeedback(
        campsiteId: String,
        authorId: String,
        adminId: String,
        clear: (Review) -> Review,
    ) {
        db.atomic { tx ->
            tx.activeAccount(db, adminId, Role.ADMIN)
            val ref = db.document(FirestorePaths.review(campsiteId, authorId))
            val current = tx.require(ref, ReviewDocument)
            val changed = clear(current)
            if (changed != current) tx.set(ref, ReviewDocument.encode(changed.copy(updatedAt = clock.instant())))
        }
    }

    /**
     * Scrubs profile data while retaining identity/history, then resumes dependent removal
     * work. Self-removal is allowed; removing another account requires admin capability.
     *
     * @param accountId Account ID whose participation or data is addressed.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    override suspend fun removeAccount(
        accountId: String,
        actorId: String,
        reason: String?,
    ) {
        db.atomic { tx ->
            val account =
                tx.require(db.document(FirestorePaths.account(accountId)), AccountDocument)
            val removal = account.removal
            if (removal != null) {
                authorizeRecorded(tx, removal, actorId)
                RemovalWrites.enqueue(db, tx, ImageTarget(ImageTargetType.ACCOUNT, accountId), removal)
            } else {
                if (accountId != actorId) tx.activeAccount(db, actorId, Role.ADMIN)
                val requested = Removal(clock.instant(), actorId, reason)
                tx.set(
                    db.document(FirestorePaths.account(accountId)),
                    AccountDocument.encode(
                        account.copy(
                            name = "",
                            email = null,
                            phone = null,
                            roles = emptySet(),
                            preferences = Preferences(),
                            settings = AccountSettings(notificationsEnabled = false),
                            removal = requested,
                        ),
                    ),
                )
                RemovalWrites.enqueue(db, tx, ImageTarget(ImageTargetType.ACCOUNT, accountId), requested)
            }
        }
        processor.resume(ImageTarget(ImageTargetType.ACCOUNT, accountId))
    }

    /**
     * Marks a listing removed for its owner/admin and resumes dependent cancellation and
     * cleanup.
     *
     * @param campsiteId Campsite document ID.
     * @param actorId Account ID responsible for the requested operation.
     * @param reason Optional explanation recorded with the operation.
     */
    override suspend fun removeCampsite(
        campsiteId: String,
        actorId: String,
        reason: String?,
    ) {
        db.atomic { tx ->
            val site =
                tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            val recorded = site.removal
            if (recorded != null) {
                authorizeRecorded(tx, recorded, actorId)
                RemovalWrites.enqueue(db, tx, ImageTarget(ImageTargetType.CAMPSITE, campsiteId), recorded)
            } else {
                val actor = tx.activeAccount(db, actorId)
                requireOperation(
                    actor.id == site.ownerId || Role.ADMIN in actor.roles,
                    NOT_ALLOWED,
                    "Not owner or admin",
                )
                RemovalWrites.markSite(db, tx, site, Removal(clock.instant(), actorId, reason))
            }
        }
        processor.resume(ImageTarget(ImageTargetType.CAMPSITE, campsiteId))
    }

    /** Keeps retry authority on the first removal even when the original actor has lost ordinary roles.
     * @param tx Caller transaction reading current administrative capability when needed.
     * @param removal First recorded attribution.
     * @param actorId Account requesting continuation.
     */
    private fun authorizeRecorded(
        tx: Transaction,
        removal: Removal,
        actorId: String,
    ) {
        if (actorId != removal.actorId) tx.activeAccount(db, actorId, Role.ADMIN)
    }

    /**
     * Drains readable removal jobs, including dependent jobs created while processing.
     */
    override suspend fun resumeRemovals() =
        processor.resumeAll()
}

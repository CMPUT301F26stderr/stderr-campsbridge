package io.github.CMPUT301F26stderr.campsbridge.data.moderation

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
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
 */
class FirestoreModerationRepository(
    private val db: FirebaseFirestore,
    images: ImageRepository,
    lotteries: FirestoreLotteryRepository,
    private val clock: Clock,
) : ModerationRepository {
    private val processor = RemovalProcessor(db, images, lotteries, clock)

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
            if (accountId != actorId) tx.activeAccount(db, actorId, Role.ADMIN)
            val removal = account.removal ?: Removal(clock.instant(), actorId, reason)
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
                        removal = removal,
                    ),
                ),
            )
            RemovalWrites.enqueue(db, tx, ImageTarget(ImageTargetType.ACCOUNT, accountId), removal)
        }
        processor.resumeFor(actorId)
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
            val actor = tx.activeAccount(db, actorId)
            val site =
                tx.require(db.document(FirestorePaths.campsite(campsiteId)), CampsiteDocument)
            requireOperation(
                actor.id == site.ownerId || Role.ADMIN in actor.roles,
                NOT_ALLOWED,
                "Not owner or admin",
            )
            RemovalWrites.markSite(db, tx, site, Removal(clock.instant(), actorId, reason))
        }
        processor.resumeFor(actorId)
    }

    /**
     * Drains readable removal jobs, including dependent jobs created while processing.
     */
    override suspend fun resumeRemovals() =
        processor.resumeFor(null)
}

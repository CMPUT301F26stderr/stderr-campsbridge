package io.github.CMPUT301F26stderr.campsbridge.data.maintenance

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.image.FirebaseImageRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDeliveries
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import java.time.Clock

/**
 * Explicit global maintenance for an administrator; ordinary startup uses scoped work.
 * @param db Authenticated administrative client.
 * @param moderation Durable account/listing removal processor.
 * @param lotteries The same draw operation used by manual and session actions.
 * @param images Durable obsolete-blob cleanup processor.
 * @param clock Time used to identify due draws.
 */
class AdminMaintenance(
    private val db: FirebaseFirestore,
    private val moderation: ModerationRepository,
    private val lotteries: FirestoreLotteryRepository,
    private val images: FirebaseImageRepository,
    private val clock: Clock,
) {
    /**
     * Resumes global removal, draw, receipt and image work after checking the admin account.
     * Failure propagates and durable progress is safe to retry.
     * @param adminId Active administrator requesting this maintenance pass.
     */
    suspend fun resume(adminId: String) {
        db.atomic { tx -> tx.activeAccount(db, adminId, Role.ADMIN) }
        moderation.resumeRemovals()
        for (lottery in db.collection("lotteries").read(LotteryDocument)) {
            if (lottery.publishedAt != null && lottery.cancellation == null &&
                lottery.drawnAt == null &&
                clock.instant() >= lottery.configuration.registrationClosesAt
            ) {
                try {
                    lotteries.draw(lottery.id)
                } catch (failure: PersistenceFailure) {
                    if (failure.reason != PersistenceFailure.Reason.INACTIVE) throw failure
                }
            }
        }
        NotificationDeliveries(db).resumeAll()
        images.resumeCleanup()
    }
}

package io.github.CMPUT301F26stderr.campsbridge.data.lottery

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.Clock

/**
 * Coordinates cancellation with the same resumable draw operation.
 * @param db Firestore client for this operation family.
 * @param clock Shared time source for validation and recorded facts.
 * @param draws Existing draw owner used to finish outcomes before cancellation.
 */
internal class LotteryCancellation(
    private val db: FirebaseFirestore,
    private val clock: Clock,
    private val draws: LotteryDraw,
) {
    /**
     * Builds a validated reference to the lottery document.
     *
     * @param id Lottery document ID.
     */
    private fun ref(id: String) =
        db.document(FirestorePaths.lottery(id))

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
            draws.draw(id)
        }
    }
}

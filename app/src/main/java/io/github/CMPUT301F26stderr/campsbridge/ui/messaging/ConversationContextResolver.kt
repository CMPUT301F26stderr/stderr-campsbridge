package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Actual two-person saved relationship, independent of workspace roles.
 * @param reservation Original complete saved stay.
 * @param ownerId Immutable owner of that stay's campsite.
 * @param actorId Current participant requesting the conversation.
 */
data class ConversationContext(
    val reservation: Reservation,
    val ownerId: String,
    val actorId: String,
) {
    val participantIds: Set<String> get() = setOf(reservation.entrantId, ownerId)
    val otherId: String get() = participantIds.single { it != actorId }
}

/** Confirms the actual owner before entry; account roles alone do not grant conversation access.
 * @param campsites Saved campsite relationship source.
 */
class ConversationContextResolver(
    private val campsites: CampsiteRepository,
) {
    /** Returns the participant context, or null for missing/unrelated saved facts; read failures propagate.
     * @param reservation Actual saved stay supplied by booking or immutable-event loading.
     * @param actorId Current account identity.
     */
    suspend fun resolve(
        reservation: Reservation,
        actorId: String,
    ): ConversationContext? {
        val site = campsites.get(reservation.campsiteId)
        currentCoroutineContext().ensureActive()
        if (site == null || site.id != reservation.campsiteId) return null
        val participants = setOf(reservation.entrantId, site.ownerId)
        return if (participants.size == 2 &&
            actorId in participants
        ) {
            ConversationContext(reservation, site.ownerId, actorId)
        } else {
            null
        }
    }
}

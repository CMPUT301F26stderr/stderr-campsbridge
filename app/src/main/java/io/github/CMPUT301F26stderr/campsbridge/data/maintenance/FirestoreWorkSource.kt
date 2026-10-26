package io.github.CMPUT301F26stderr.campsbridge.data.maintenance

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observeRelated
import io.github.CMPUT301F26stderr.campsbridge.data.notification.DeliveryScope
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDeliveries
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Discovers work from owner lotteries and entrant/owner reservations without global scans.
 * @param lotteries Lottery observations and the shared draw operation.
 * @param bookings Reservation observations deriving owner membership through campsites.
 * @param deliveries Parent-scoped durable job discovery and fan-out.
 */
internal class FirestoreWorkSource(
    private val lotteries: LotteryRepository,
    private val bookings: BookingRepository,
    private val deliveries: NotificationDeliveries,
) : WorkSource {
    /** Supplies only the current account's eligible work.
     * @param session Active account and complete capabilities.
     */
    override fun observe(session: WorkSession): Flow<List<PendingOperation>> =
        flow {
            coroutineScope {
                val ownedLotteries =
                    (
                        if (Role.OWNER in session.roles) {
                            lotteries.observeForOwner(session.accountId)
                        } else {
                            flowOf(emptyList())
                        }
                    ).shareIn(this, SharingStarted.Lazily, replay = 1)
                val draws =
                    ownedLotteries.map { records ->
                        records
                            .filter {
                                it.publishedAt != null && it.cancellation == null &&
                                    it.drawnAt == null
                            }.map {
                                PendingOperation.Draw(it.id, it.configuration.registrationClosesAt)
                            }
                    }
                val lotteryJobs =
                    observeRelated(ownedLotteries.map { records -> records.map { it.id } }) { id ->
                        deliveries.observe(DeliveryScope.Lottery(id))
                    }
                val entrantStays =
                    if (Role.ENTRANT in
                        session.roles
                    ) {
                        bookings.observeForEntrant(session.accountId)
                    } else {
                        flowOf(emptyList())
                    }
                val ownedStays =
                    if (Role.OWNER in
                        session.roles
                    ) {
                        bookings.observeForOwner(session.accountId)
                    } else {
                        flowOf(emptyList())
                    }
                val reservationIds =
                    combine(entrantStays, ownedStays) { entrant, owner ->
                        (entrant + owner).map { it.id }
                    }
                val reservationJobs =
                    observeRelated(reservationIds) { id ->
                        deliveries.observe(DeliveryScope.Reservation(id))
                    }
                emitAll(
                    combine(
                        draws,
                        lotteryJobs,
                        reservationJobs,
                    ) { pendingDraws, lottery, reservation ->
                        pendingDraws + (lottery + reservation).map { PendingOperation.Delivery(it) }
                    },
                )
            }
        }

    /** Runs the repository operation owning the invariant.
     * @param operation Due draw or durable notification job.
     */
    override suspend fun execute(operation: PendingOperation) {
        when (operation) {
            is PendingOperation.Draw -> lotteries.draw(operation.id)
            is PendingOperation.Delivery -> deliveries.deliver(operation.job)
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.di

import io.github.CMPUT301F26stderr.campsbridge.data.account.FirestoreAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.data.booking.FirestoreBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.FirestoreCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirebaseClients
import io.github.CMPUT301F26stderr.campsbridge.data.image.FirebaseImageRepository
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.FirestoreLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.data.maintenance.AdminMaintenance
import io.github.CMPUT301F26stderr.campsbridge.data.maintenance.FirestoreWorkSource
import io.github.CMPUT301F26stderr.campsbridge.data.maintenance.SessionPendingWork
import io.github.CMPUT301F26stderr.campsbridge.data.messaging.FirestoreMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.data.moderation.FirestoreModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.data.notification.FirestoreNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDeliveries
import io.github.CMPUT301F26stderr.campsbridge.data.review.FirestoreReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.MessageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import java.time.Clock
import kotlin.random.Random

/**
 * Application-scoped persistence composition root; platform services are wired in AppServices.
 *
 * @param clients Initialized Firestore/Storage clients for the selected project or emulators.
 * @param clock Injectable time source for recorded timestamps and deadline checks.
 * @param random Injectable source for proposing the persisted draw seed.
 */
class AppContainer(
    clients: FirebaseClients,
    clock: Clock = Clock.systemUTC(),
    random: Random = Random.Default,
) : AppRepositories {
    override val accounts: AccountRepository = FirestoreAccountRepository(clients.firestore, clock)
    override val campsites: CampsiteRepository =
        FirestoreCampsiteRepository(clients.firestore, clock)
    private val lotteryImplementation = FirestoreLotteryRepository(clients.firestore, clock, random)
    override val lotteries: LotteryRepository = lotteryImplementation
    override val bookings: BookingRepository = FirestoreBookingRepository(clients.firestore, clock)
    private val imageImplementation =
        FirebaseImageRepository(clients.firestore, clients.storage, clock)
    override val images: ImageRepository = imageImplementation
    override val reviews: ReviewRepository = FirestoreReviewRepository(clients.firestore, clock)
    override val notifications: NotificationRepository =
        FirestoreNotificationRepository(clients.firestore, clock)
    val messages: MessageRepository = FirestoreMessageRepository(clients.firestore, clock)
    val moderation: ModerationRepository =
        FirestoreModerationRepository(clients.firestore, images, lotteryImplementation, clock)

    override val pendingWork: PendingWork =
        SessionPendingWork(
            FirestoreWorkSource(lotteries, bookings, NotificationDeliveries(clients.firestore)),
            clock,
        )

    val maintenance =
        AdminMaintenance(
            clients.firestore,
            moderation,
            lotteryImplementation,
            imageImplementation,
            clock,
        )
}

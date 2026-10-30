package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Local calendar store; sample edits never reach Firebase or perform reservation transactions. */
internal class TaskCampsiteRepository :
    CampsiteRepository by DemoCampsiteRepository(TaskExampleData.sites) {
    private val nights = MutableStateFlow(TaskExampleData.nights)

    /** Observes the requested sample range through the same repository contract as production.
     * @param id Fixture listing identity.
     * @param start Inclusive start date.
     * @param endExclusive Exclusive end date.
     */
    override fun observeCalendar(
        id: String,
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<CalendarNight>> =
        nights.map { rows ->
            if (id ==
                TaskExampleData.sites[0].id
            ) {
                rows.filter { it.date >= start && it.date < endExclusive }
            } else {
                emptyList()
            }
        }

    /** Applies an explicitly local Offer/Withdraw action while retaining occupied-night protection.
     * @param id Fixture listing identity.
     * @param ownerId Fixture owner.
     * @param dates Explicit desired local dates.
     * @param offered Desired inventory value.
     */
    override suspend fun setOffered(
        id: String,
        ownerId: String,
        dates: Set<LocalDate>,
        offered: Boolean,
    ) {
        require(id == TaskExampleData.sites[0].id && ownerId == TaskExampleData.OWNER_ID)
        require(nights.value.none { it.date in dates && it.reservationId != null })
        val values = nights.value.associateBy { it.date }.toMutableMap()
        dates.forEach { values[it] = CalendarNight(it, offered) }
        nights.value = values.values.toList()
    }
}

/** Read-only lottery fixture adapter; distributed writes remain unsupported. */
internal class TaskLotteryRepository : LotteryRepository by DemoLotteryRepository() {
    /** Returns the seeded draw when its actual identity matches.
     * @param id Requested saved identity.
     */
    override suspend fun get(id: String): Lottery? =
        TaskExampleData.lottery.takeIf { it.id == id }

    /** Observes one fixed saved draw.
     * @param id Requested identity.
     */
    override fun observe(id: String): Flow<Lottery?> =
        flowOf(
            TaskExampleData.lottery.takeIf {
                it.id ==
                    id
            },
        )

    /** Supplies saved allocations for the exact draw.
     * @param id Requested identity.
     */
    override fun observeOffers(id: String): Flow<List<LotteryOffer>> =
        flowOf(
            if (id ==
                TaskExampleData.lottery.id
            ) {
                TaskExampleData.offers
            } else {
                emptyList()
            },
        )

    /** Supplies only the requested draw's entries.
     * @param id Requested identity.
     */
    override fun observeEntries(id: String): Flow<List<LotteryEntry>> =
        flowOf(
            TaskExampleData.entries.filter {
                it.lotteryId ==
                    id
            },
        )

    /** Supplies only the current entrant's participation.
     * @param accountId Requested entrant identity.
     */
    override fun observeForEntrant(accountId: String): Flow<List<LotteryEntry>> =
        flowOf(
            TaskExampleData.entries.filter {
                it.accountId ==
                    accountId
            },
        )
}

/** Saved-history reads only; reservation writes retain the ordinary demo's explicit failure. */
internal class TaskBookingRepository : BookingRepository by DemoBookingRepository() {
    /** Reads the exact sample aggregate.
     * @param id Requested reservation identity.
     */
    override suspend fun get(id: String): Reservation? =
        TaskExampleData.reservations.find {
            it.id ==
                id
        }

    /** Observes the same sample aggregate.
     * @param id Requested reservation identity.
     */
    override fun observe(id: String): Flow<Reservation?> =
        flowOf(
            TaskExampleData.reservations.find {
                it.id ==
                    id
            },
        )

    /** Confirms consumption only for the supplied lottery/entrant tuple.
     * @param lotteryId Requested draw.
     * @param entrantId Requested entrant.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ): Reservation? =
        TaskExampleData.reservations.find { it.lotteryId == lotteryId && it.entrantId == entrantId }

    /** Supplies the fixture entrant's actual saved history.
     * @param entrantId Requested entrant.
     */
    override fun observeForEntrant(entrantId: String): Flow<List<Reservation>> =
        flowOf(
            TaskExampleData.reservations.filter {
                it.entrantId ==
                    entrantId
            },
        )

    /** Supplies the fixture owner's history without querying another dataset.
     * @param ownerId Requested owner.
     */
    override fun observeForOwner(ownerId: String): Flow<List<Reservation>> =
        flowOf(
            if (ownerId ==
                TaskExampleData.OWNER_ID
            ) {
                TaskExampleData.reservations
            } else {
                emptyList()
            },
        )
}

/** Fixed Inbox events with local receipt updates for inspecting the real unread helper. */
internal class TaskNotificationRepository : NotificationRepository by DemoNotificationRepository() {
    private val receipts = MutableStateFlow(TaskExampleData.receipts)

    /** Reads an immutable event by identity.
     * @param id Requested event.
     */
    override suspend fun get(id: String): Notification? =
        TaskExampleData.events.find { it.id == id }

    /** Observes only the sample recipient's receipts.
     * @param accountId Requested recipient.
     */
    override fun observeInbox(accountId: String): Flow<List<InboxReceipt>> =
        receipts.map {
            if (accountId ==
                TaskExampleData.ACCOUNT_ID
            ) {
                it
            } else {
                emptyList()
            }
        }

    /** Marks the selected local receipt read, independently of navigation.
     * @param accountId Current sample recipient.
     * @param notificationId Opened event identity.
     */
    override suspend fun markRead(
        accountId: String,
        notificationId: String,
    ) {
        require(accountId == TaskExampleData.ACCOUNT_ID)
        receipts.value =
            receipts.value.map {
                if (it.notificationId == notificationId &&
                    it.readAt == null
                ) {
                    it.copy(readAt = TaskExampleData.now)
                } else {
                    it
                }
            }
    }
}

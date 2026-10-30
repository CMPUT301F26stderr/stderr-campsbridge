package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Fixed fixture facts shared by examples and their tests, with no reserved-rule algorithms. */
object TaskExampleData {
    const val ACCOUNT_ID = "task-entrant"
    const val OWNER_ID = "task-owner"
    val now: Instant = Instant.parse("2027-05-10T18:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val today: LocalDate = LocalDate.of(2027, 5, 10)
    val account =
        Account(
            ACCOUNT_ID,
            "Alex Chen",
            "alex@example.com",
            null,
            setOf(Role.ENTRANT),
            Preferences(),
            AccountSettings(),
            now.minusSeconds(86400),
        )
    val owner =
        account.copy(
            id = OWNER_ID,
            name = "Jordan Lee",
            roles = setOf(Role.ENTRANT, Role.OWNER),
        )
    val sites: List<Campsite> =
        listOf(
            sampleCampsite("task-river", OWNER_ID),
            sampleCampsite("task-alpine", OWNER_ID).let {
                it.copy(
                    details =
                        it.details.copy(
                            name = "Alpine Meadow",
                            description = "Mountain walks",
                            maxGuests = 4,
                        ),
                )
            },
            sampleCampsite("task-forest", OWNER_ID).let {
                it.copy(
                    details =
                        it.details.copy(
                            name = "Forest Clearing",
                            description = "Woodland camping",
                            maxGuests = 6,
                        ),
                )
            },
        )
    val lottery =
        Lottery(
            "task-draw",
            OWNER_ID,
            "May camping draw",
            "Local sample records",
            LotteryConfiguration(
                now.minusSeconds(172800),
                now.minusSeconds(86400),
                now.minusSeconds(86400),
                now.plusSeconds(864000),
                1,
                null,
            ),
            0,
            now.minusSeconds(259200),
            now.minusSeconds(172800),
            now.minusSeconds(86400),
        )
    val offers = listOf(LotteryOffer(sites[0].id, (11..14).map { today.withDayOfMonth(it) }))
    val entries =
        listOf(
            LotteryEntry(
                lottery.id,
                ACCOUNT_ID,
                EntryState.OFFERED,
                now.minusSeconds(172800),
                now.minusSeconds(86400),
            ),
            LotteryEntry(
                lottery.id,
                "task-other-entrant",
                EntryState.LOST,
                now.minusSeconds(172800),
                now.minusSeconds(86400),
            ),
        )
    val nights =
        (1..31).filter { it != 14 }.map { day ->
            CalendarNight(
                today.withDayOfMonth(day),
                day in 11..13,
                if (day ==
                    13
                ) {
                    "task-occupied"
                } else {
                    null
                },
            )
        }
    val reservations: List<Reservation> =
        listOf(
            stay("task-stay-late", 14),
            stay("task-stay-early", 11),
            stay("task-stay-past", 7),
        )
    val categories =
        mapOf(
            "task-stay-late" to ReservationCategory.UPCOMING,
            "task-stay-early" to ReservationCategory.UPCOMING,
            "task-stay-past" to ReservationCategory.PAST,
        )
    val events = listOf(event("task-b", 0), event("task-new", 60), event("task-a", 0))
    val receipts =
        listOf(
            InboxReceipt("task-b", null),
            InboxReceipt("task-new", null),
            InboxReceipt("task-a", now),
        )

    /** Supplies a saved stay in a separate draw so the winner example remains unconsumed.
     * @param id Stable fixture identity.
     * @param day Inclusive May arrival day.
     */
    private fun stay(
        id: String,
        day: Int,
    ): Reservation {
        val site = sites[0]
        return Reservation(
            id,
            "task-history-draw",
            ACCOUNT_ID,
            site.id,
            today.withDayOfMonth(day),
            today.withDayOfMonth(day + 2),
            2,
            BookedCampsite(
                site.details.name,
                site.details.locationLabel,
                site.details.location,
                site.details.timeZone,
            ),
            AcceptedTerms(site.details.rules, BookingPolicy.VERSION, now.minusSeconds(86400)),
            now.minusSeconds(86400),
            Payment(13000, now.minusSeconds(86400)),
        )
    }

    /** Builds an actual typed event pointing to the supplied saved lottery.
     * @param id Stable event identity and ordering tie-breaker.
     * @param offset Seconds after the common fixture event time.
     */
    private fun event(
        id: String,
        offset: Long,
    ): Notification =
        Notification(
            id,
            null,
            NotificationType.LOTTERY_WON,
            NotificationSubject(SubjectType.LOTTERY, lottery.id),
            null,
            now.minusSeconds(3600).plusSeconds(offset),
        )
}

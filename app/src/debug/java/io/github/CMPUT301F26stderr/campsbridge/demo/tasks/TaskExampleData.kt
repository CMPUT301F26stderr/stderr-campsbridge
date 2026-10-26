package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
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
}

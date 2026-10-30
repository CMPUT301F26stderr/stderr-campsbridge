package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies saved sample relationships independently of the presentation or missing helpers. */
class DemoPreviewDataTest {
    /** Every visible saved target resolves, with multiple lifecycle states and an unused win. */
    @Test fun recordsHaveCoherentIdentitiesAndDistinctOpportunities() {
        for (scenario in listOf(DemoScenario.ENTRANT, DemoScenario.OWNER)) {
            val data = DemoPreviewData(scenario)
            val accountIds = data.accounts.map { it.id }.toSet()
            val sites = data.campsites.map { it.id }.toSet()
            val draws = data.lotteries.map { it.id }.toSet()
            val stays = data.reservations.map { it.id }.toSet()
            assertTrue(data.campsites.all { it.ownerId in accountIds })
            assertTrue(data.offers.keys.all { it in draws })
            assertTrue(
                data.offers.values
                    .flatten()
                    .all { it.campsiteId in sites },
            )
            assertTrue(data.entries.all { it.accountId in accountIds && it.lotteryId in draws })
            assertTrue(data.entries.any { it.state == EntryState.WAITING })
            assertTrue(data.entries.any { it.state == EntryState.LOST })
            val unused =
                data.entries.single {
                    it.lotteryId == "demo-won" &&
                        it.accountId == data.entrantId
                }
            assertEquals(EntryState.OFFERED, unused.state)
            assertFalse(data.reservations.any { it.lotteryId == unused.lotteryId })
            assertTrue(
                data.reservations.all {
                    it.campsiteId in sites && it.entrantId in accountIds &&
                        it.lotteryId in draws
                },
            )
            assertTrue(
                data.calendars.values.flatten().filter { it.reservationId != null }.all {
                    it.reservationId in
                        stays
                },
            )
            assertTrue(
                data.events.all {
                    when (it.subject.type) {
                        SubjectType.LOTTERY -> it.subject.id in draws
                        SubjectType.RESERVATION -> it.subject.id in stays
                        SubjectType.MESSAGE -> true
                    }
                },
            )
            assertTrue(data.inboxes.keys.all { it in accountIds })
            assertTrue(
                data.inboxes.values.flatten().all { receipt ->
                    data.events.any {
                        it.id ==
                            receipt.notificationId
                    }
                },
            )
            assertTrue(
                data.reservations.any { it.checkOutDate < java.time.LocalDate.of(2027, 5, 10) },
            )
            assertTrue(
                data.reservations.any { it.checkInDate > java.time.LocalDate.of(2027, 5, 10) },
            )
        }
    }
}

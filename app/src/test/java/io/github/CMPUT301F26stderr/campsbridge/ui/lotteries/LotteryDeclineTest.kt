/*
 * WN-02 contracts for current win eligibility and one explicitly confirmed command.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** WN-02 contracts for current win eligibility and one explicitly confirmed command. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDeclineTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = Instant.parse("2027-05-10T18:00:00Z")
    private val parent =
        CampingFixtures
            .lottery(
                "draw",
                "owner",
            ).copy(
                configuration =
                    LotteryConfiguration(
                        now.minusSeconds(7200),
                        now.minusSeconds(3600),
                        now,
                        now.plusSeconds(7200),
                        1,
                        null,
                    ),
                createdAt = now.minusSeconds(10800),
                publishedAt = now.minusSeconds(9000),
                drawnAt = now.minusSeconds(3600),
            )
    private val entry =
        LotteryEntry(parent.id, "entrant", EntryState.OFFERED, now.minusSeconds(5400), now.minusSeconds(3600))

    /** WN-02: exact booking boundaries and the fixed consumption/outcome vectors gate decline. */
    @Test fun onlyAConfirmedUnusedWinAtBookingOpenCanDecline() =
        runTest {
            for (vector in listOf("open", "close", "reserved", "lost", "declined", "unresolved", "failed")) {
                val gate = CompletableDeferred<io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation?>()
                val store = ViewModelStore()
                val clock =
                    MutableClock(
                        if (vector ==
                            "close"
                        ) {
                            parent.configuration.bookingClosesAt
                        } else {
                            parent.configuration.bookingOpensAt
                        },
                    )
                val lotteries =
                    FakeLotteryRepository(listOf(parent)).apply {
                        entries.value =
                            listOf(
                                entry.copy(
                                    state =
                                        when (vector) {
                                            "lost" -> EntryState.LOST
                                            "declined" -> EntryState.DECLINED
                                            else -> EntryState.OFFERED
                                        },
                                ),
                            )
                    }
                val bookings =
                    FakeBookingRepository().apply {
                        when (vector) {
                            "reserved" -> {
                                records.value =
                                    listOf(
                                        CampingFixtures.reservation().let {
                                            it.copy(
                                                createdAt = now,
                                                acceptedTerms = it.acceptedTerms.copy(acceptedAt = now),
                                                payment = it.payment.copy(paidAt = now),
                                            )
                                        },
                                    )
                            }

                            "unresolved" -> {
                                opportunityHandler = { _, _ -> gate.await() }
                            }

                            "failed" -> {
                                opportunityHandler = { _, _ -> throw IOException("consumption unavailable") }
                            }
                        }
                    }
                try {
                    val model =
                        LotteryDetailViewModel(
                            "entrant",
                            parent.id,
                            lotteries,
                            FakeCampsiteRepository(),
                            clock,
                            bookings,
                            LotteryContext.ENTRANT,
                            FakeAccountRepository(),
                        )
                    store.put("detail", model)
                    runCurrent()
                    assertEquals(vector, vector == "open", model.decline.value.canDecline)
                    model.requestDecline()
                    assertEquals(vector, vector == "open", model.decline.value.isConfirming)
                    model.dismissDecline()
                    model.confirmDecline()
                    runCurrent()
                    assertTrue(vector, lotteries.events.none { it.startsWith("decline:") })
                } finally {
                    store.clear()
                    gate.complete(null)
                    runCurrent()
                }
            }
        }

    /** WN-02: cancellation writes nothing; confirmation freezes the real IDs and blocks overlaps. */
    @Test fun cancelThenConfirmSubmitsOnePendingRequestForTheActualEntrant() =
        runTest {
            val store = ViewModelStore()
            val gate = CompletableDeferred<Unit>()
            val requests = mutableListOf<Pair<String, String>>()
            val lotteries =
                FakeLotteryRepository(listOf(parent)).apply {
                    entries.value = listOf(entry)
                    declineHandler = { id, actor ->
                        requests.add(id to actor)
                        gate.await()
                    }
                }
            try {
                val model =
                    LotteryDetailViewModel(
                        "entrant",
                        parent.id,
                        lotteries,
                        FakeCampsiteRepository(),
                        MutableClock(parent.configuration.bookingOpensAt),
                        FakeBookingRepository(),
                        LotteryContext.ENTRANT,
                        FakeAccountRepository(),
                    )
                store.put("detail", model)
                runCurrent()
                model.requestDecline()
                model.dismissDecline()
                model.confirmDecline()
                runCurrent()
                assertTrue(requests.isEmpty())
                model.requestDecline()
                model.confirmDecline()
                model.confirmDecline()
                model.retryDecline()
                runCurrent()
                assertEquals(listOf(parent.id to "entrant"), requests)
                assertTrue(model.decline.value.isSubmitting)
                assertFalse(model.decline.value.canDecline)
                lotteries.entries.value = listOf(entry.copy(state = EntryState.DECLINED))
                runCurrent()
                assertTrue(model.decline.value.isSubmitting)
                gate.complete(Unit)
                runCurrent()
                assertFalse(model.decline.value.isSubmitting)
                assertEquals(
                    LotteryMembershipRead.Content(LotteryMembershipStatus.DECLINED),
                    (model.state.value as LotteryDetailUiState.Content).membership,
                )
                assertEquals(listOf(parent.id to "entrant"), requests)
            } finally {
                store.clear()
                gate.complete(Unit)
                runCurrent()
            }
        }
}

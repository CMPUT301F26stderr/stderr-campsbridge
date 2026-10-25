package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration

/** Scoped queries, category precedence, linked-source honesty and read-owner lifetime. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = CampingFixtures.now
    private val clock = MutableClock(now)
    private val base = CampingFixtures.lottery("draw", "account-1")
    private val lotteries = FakeLotteryRepository()
    private val bookings = FakeBookingRepository()

    /** Owner lists use only the scoped query and distinguish published records from drafts. */
    @Test fun ownerCategoriesUseOwnerQueryWithoutEntrantOrGlobalReads() =
        withModel {
            lotteries.records.value =
                listOf(
                    base,
                    base.copy(id = "draft", publishedAt = null),
                    base.copy(id = "other", ownerId = "other"),
                )
            runCurrent()
            assertEquals(listOf("draw"), content(it).items.map { row -> row.lottery.id })
            it.selectCategory(LotteryListCategory.DRAFTS)
            assertEquals(listOf("draft"), content(it).items.map { row -> row.lottery.id })
            assertEquals(listOf("account-1"), lotteries.ownerRequests)
            assertTrue(lotteries.entrantRequests.isEmpty())
            assertTrue(bookings.entrantRequests.isEmpty())
            assertTrue(lotteries.observedRecordIds.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Scoped read failure is retryable and duplicate Retry cannot multiply listeners. */
    @Test fun failedOwnerSourceIsNotEmptyAndRetriesOnlyOnce() =
        withModel {
            lotteries.ownerFailure.value = IOException("offline")
            runCurrent()
            assertEquals(LotteryListUiState.Failure, it.state.value)
            lotteries.ownerFailure.value = null
            it.retry()
            it.retry()
            runCurrent()
            assertEquals(LotteryListUiState.Content(emptyList()), it.state.value)
            assertEquals(2, lotteries.ownerRequests.size)
            assertEquals(1, lotteries.activeOwnerObservers)
        }

    /** A management filter confirms allocations and retains unknown/failed linked sources. */
    @Test fun ownerCampsiteFilterObservesActualOffersAndCanClear() =
        withModel {
            lotteries.records.value = listOf(base, base.copy(id = "other"))
            lotteries.liveOffers.value =
                mapOf("draw" to listOf(LotteryOffer("site/A", listOf(CampingFixtures.arrival))))
            runCurrent()
            it.selectCampsite("site/A")
            runCurrent()
            assertEquals(listOf("draw"), content(it).items.map { row -> row.lottery.id })
            assertEquals(2, lotteries.activeOfferObservers)
            lotteries.offerFailure.value = IOException("allocations offline")
            runCurrent()
            assertTrue(content(it).items.isEmpty())
            assertEquals(
                setOf("draw", "other"),
                content(it)
                    .unresolved
                    .map { row ->
                        row.lotteryId
                    }.toSet(),
            )
            assertTrue(
                content(it).unresolved.all { row ->
                    row.issue ==
                        LotteryReferenceIssue.FAILURE
                },
            )
            lotteries.offerFailure.value = null
            it.retry()
            it.retry()
            runCurrent()
            assertEquals(listOf("draw"), content(it).items.map { row -> row.lottery.id })
            assertEquals(2, lotteries.activeOfferObservers)
            it.selectCampsite(null)
            runCurrent()
            assertEquals(2, content(it).items.size)
            assertEquals(0, lotteries.activeOfferObservers)
        }

    /** Only small category/filter primitives restore; a category from another scope is ignored. */
    @Test fun categoryAndFilterRestoreWithoutRestoringRecords() {
        val saved = SavedStateHandle()
        withModel(saved = saved) {
            it.selectCategory(LotteryListCategory.DRAFTS)
            it.selectCampsite("site/A")
        }
        withModel(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })) {
            assertEquals(LotteryListCategory.DRAFTS, it.category.value)
            assertEquals("site/A", it.campsiteId.value)
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(LotteryListCategory.DRAFTS, it.category.value)
        }
        withModel(context = LotteryContext.ENTRANT, saved = saved) {
            assertEquals(LotteryListCategory.WAITING, it.category.value)
            assertEquals(null, it.campsiteId.value)
        }
    }

    /** Each entrant category follows the agreed precedence, including partial draw outcomes. */
    @Test fun entrantCategoryPrecedenceUsesConfirmedDrawAndConsumedWins() =
        withModel(LotteryContext.ENTRANT) {
            val cancelled = Cancellation(now, "account-1", null)
            val rows =
                listOf(
                    base.copy(id = "waiting"),
                    base.copy(id = "partial-win"),
                    base.copy(id = "partial-loss"),
                    base.copy(id = "offered", drawnAt = now),
                    base.copy(id = "used", drawnAt = now),
                    base.copy(id = "lost", drawnAt = now),
                    base.copy(id = "withdrawn"),
                    base.copy(id = "declined"),
                    base.copy(id = "cancelled", cancellation = cancelled),
                )
            lotteries.records.value = rows
            lotteries.entries.value =
                rows.map { row ->
                    entry(
                        row.id,
                        when (row.id) {
                            "partial-win", "offered", "used", "cancelled" -> EntryState.OFFERED
                            "partial-loss", "lost" -> EntryState.LOST
                            "withdrawn" -> EntryState.WITHDRAWN
                            "declined" -> EntryState.DECLINED
                            else -> EntryState.WAITING
                        },
                    )
                }
            bookings.records.value =
                listOf(
                    CampingFixtures.reservation().copy(lotteryId = "used", entrantId = "account-1"),
                )
            runCurrent()
            assertEquals(setOf("waiting", "partial-win", "partial-loss"), ids(it))
            assertTrue(
                content(it)
                    .items
                    .filter { row ->
                        row.lottery.id.startsWith("partial-")
                    }.all { row ->
                        row.entryState ==
                            null
                    },
            )
            it.selectCategory(LotteryListCategory.ACTION_NEEDED)
            assertEquals(setOf("offered"), ids(it))
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(setOf("used", "lost", "withdrawn", "declined", "cancelled"), ids(it))
            assertEquals(
                "reservation",
                content(it)
                    .items
                    .first { row ->
                        row.lottery.id == "used"
                    }.reservationId,
            )
            assertEquals(1, bookings.entrantRequests.size)
            assertEquals(listOf("account-1"), lotteries.entrantRequests)
            assertTrue(lotteries.ownerRequests.isEmpty())
            assertTrue(lotteries.events.isEmpty())
        }

    /** Cancelling a reservation never turns its consumed win into another actionable win. */
    @Test fun cancelledReservationRemainsConsumedHistory() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base.copy(drawnAt = now))
            lotteries.entries.value = listOf(entry("draw", EntryState.OFFERED))
            bookings.records.value =
                listOf(
                    CampingFixtures.reservation().copy(
                        entrantId = "account-1",
                        cancellation =
                            ReservationCancellation(
                                now,
                                "account-1",
                                Role.ENTRANT,
                                null,
                            ),
                    ),
                )
            runCurrent()
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(setOf("draw"), ids(it))
            it.selectCategory(LotteryListCategory.ACTION_NEEDED)
            assertTrue(content(it).items.isEmpty())
        }

    /** A winning-but-prebooking entry is actionable until the exact exclusive booking close. */
    @Test fun deadlineMovesUnusedWinToHistoryWithoutWrites() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base.copy(drawnAt = now))
            lotteries.entries.value = listOf(entry("draw", EntryState.OFFERED))
            runCurrent()
            it.selectCategory(LotteryListCategory.ACTION_NEEDED)
            assertEquals(setOf("draw"), ids(it))
            assertEquals(LotteryLifecycle.AWAITING_BOOKING, content(it).items.single().lifecycle)
            clock.advance(Duration.ofSeconds(240))
            advanceTimeBy(240000)
            runCurrent()
            assertTrue(content(it).items.isEmpty())
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(setOf("draw"), ids(it))
            assertEquals(LotteryLifecycle.CLOSED, content(it).items.single().lifecycle)
            assertTrue(lotteries.events.isEmpty())
        }

    /** Foreground re-evaluation uses current time without waiting for a stale scheduled delay. */
    @Test fun foregroundRefreshUsesCurrentClock() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base.copy(drawnAt = now))
            lotteries.entries.value = listOf(entry("draw", EntryState.OFFERED))
            runCurrent()
            clock.advance(Duration.ofSeconds(240))
            it.refreshTime()
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(setOf("draw"), ids(it))
            assertEquals(1, bookings.entrantRequests.size)
        }

    /** Stable newest-join ordering has an exact ID tie-breaker and excludes other accounts. */
    @Test fun entrantRowsOrderByJoiningThenExactIdentity() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value =
                listOf(
                    base.copy(id = "Z"),
                    base.copy(id = "A"),
                    base.copy(id = "new"),
                    base.copy(id = "foreign"),
                )
            lotteries.entries.value =
                listOf(
                    entry("Z"),
                    entry("A"),
                    entry("new").copy(joinedAt = now.plusSeconds(1)),
                    entry("foreign").copy(accountId = "other"),
                )
            runCurrent()
            assertEquals(listOf("new", "A", "Z"), content(it).items.map { row -> row.lottery.id })
            assertEquals(setOf("A", "Z", "new"), lotteries.observedRecordIds.toSet())
        }

    /** Missing or failed linked parents remain visible in every selected category until retry. */
    @Test fun missingAndFailedReferencesAreNotSilentlyOmitted() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.entries.value = listOf(entry("draw"))
            runCurrent()
            assertEquals(
                listOf(LotteryListReference("draw", LotteryReferenceIssue.MISSING)),
                content(it).unresolved,
            )
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals(1, content(it).unresolved.size)
            lotteries.recordFailure.value = IOException("parent offline")
            runCurrent()
            assertEquals(LotteryReferenceIssue.FAILURE, content(it).unresolved.single().issue)
            lotteries.recordFailure.value = null
            lotteries.records.value = listOf(base)
            it.retry()
            it.retry()
            runCurrent()
            assertTrue(content(it).unresolved.isEmpty())
            it.selectCategory(LotteryListCategory.WAITING)
            assertEquals(setOf("draw"), ids(it))
            assertEquals(1, lotteries.activeRecordObservers)
            assertEquals(1, bookings.entrantRequests.size)
        }

    /** Required bookings failure cannot label potentially used wins as available to act on. */
    @Test fun bookingsFailureIsExplicitAndRetryDoesNotRestartMemberships() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base.copy(drawnAt = now))
            lotteries.entries.value = listOf(entry("draw", EntryState.OFFERED))
            bookings.entrantFailure.value = IOException("bookings offline")
            runCurrent()
            assertEquals(LotteryListUiState.Failure, it.state.value)
            bookings.entrantFailure.value = null
            it.retry()
            it.retry()
            runCurrent()
            it.selectCategory(LotteryListCategory.ACTION_NEEDED)
            assertEquals(setOf("draw"), ids(it))
            assertEquals(2, bookings.entrantRequests.size)
            assertEquals(1, lotteries.entrantRequests.size)
        }

    /** Removing an entry retires only its parent listener and retains the one bookings stream. */
    @Test fun changedMembershipsRetireOnlyObsoleteReaders() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base, base.copy(id = "second"))
            lotteries.entries.value = listOf(entry("draw"), entry("second"))
            runCurrent()
            assertEquals(2, lotteries.activeRecordObservers)
            lotteries.entries.value = listOf(entry("second"))
            runCurrent()
            assertEquals(setOf("second"), ids(it))
            assertEquals(1, lotteries.activeRecordObservers)
            assertEquals(1, bookings.activeEntrantObservers)
            assertEquals(1, lotteries.observedRecordIds.count { id -> id == "second" })
        }

    /** Membership failure retires stale references; retry retains the bookings aggregate. */
    @Test fun membershipFailureRetiresRowsAndRetriesOnlyThatSource() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base)
            lotteries.entries.value = listOf(entry("draw"))
            runCurrent()
            assertEquals(setOf("draw"), ids(it))
            lotteries.entrantFailure.value = IOException("memberships offline")
            runCurrent()
            assertEquals(LotteryListUiState.Failure, it.state.value)
            assertEquals(0, lotteries.activeRecordObservers)
            lotteries.entrantFailure.value = null
            it.retry()
            it.retry()
            runCurrent()
            assertEquals(setOf("draw"), ids(it))
            assertEquals(2, lotteries.entrantRequests.size)
            assertEquals(1, bookings.entrantRequests.size)
        }

    /** A live saved reservation moves the used win without adding a per-row lookup/subscription. */
    @Test fun observedReservationConsumesTheWinImmediately() =
        withModel(LotteryContext.ENTRANT) {
            lotteries.records.value = listOf(base.copy(drawnAt = now))
            lotteries.entries.value = listOf(entry("draw", EntryState.OFFERED))
            runCurrent()
            it.selectCategory(LotteryListCategory.ACTION_NEEDED)
            assertEquals(setOf("draw"), ids(it))
            bookings.records.value =
                listOf(CampingFixtures.reservation().copy(entrantId = "account-1"))
            runCurrent()
            assertTrue(content(it).items.isEmpty())
            it.selectCategory(LotteryListCategory.HISTORY)
            assertEquals("reservation", content(it).items.single().reservationId)
            assertEquals(1, bookings.entrantRequests.size)
        }

    /** Creates one scoped owner and always clears it before runTest drains scheduled time.
     * @param context Requested scoped query path.
     * @param saved Small restoration values.
     * @param block Observable behavior assertions.
     */
    private fun withModel(
        context: LotteryContext = LotteryContext.OWNER,
        saved: SavedStateHandle = SavedStateHandle(),
        block: suspend TestScope.(LotteryListViewModel) -> Unit,
    ) =
        runTest {
            val store = ViewModelStore()
            try {
                val model =
                    LotteryListViewModel("account-1", context, lotteries, bookings, clock, saved)
                store.put("list", model)
                block(model)
            } finally {
                store.clear()
                runCurrent()
                assertEquals(0, lotteries.activeOwnerObservers)
                assertEquals(0, lotteries.activeEntrantObservers)
                assertEquals(0, lotteries.activeRecordObservers)
                assertEquals(0, lotteries.activeOfferObservers)
                assertEquals(0, bookings.activeEntrantObservers)
            }
        }

    /** Requires confirmed list content before reading rows.
     * @param model Actual source owner under test.
     */
    private fun content(model: LotteryListViewModel): LotteryListUiState.Content {
        assertTrue(
            "Expected content, got ${model.state.value}",
            model.state.value is LotteryListUiState.Content,
        )
        return model.state.value as LotteryListUiState.Content
    }

    /** Reads selected resolved identities without relying on unspecified row ordering.
     * @param model Actual source owner under test.
     */
    private fun ids(model: LotteryListViewModel): Set<String> =
        content(model).items.map { it.lottery.id }.toSet()

    /** Builds one own membership in the requested state.
     * @param id Referenced lottery.
     * @param state Persisted membership outcome.
     */
    private fun entry(
        id: String,
        state: EntryState = EntryState.WAITING,
    ): LotteryEntry =
        LotteryEntry(id, "account-1", state, now, now)
}

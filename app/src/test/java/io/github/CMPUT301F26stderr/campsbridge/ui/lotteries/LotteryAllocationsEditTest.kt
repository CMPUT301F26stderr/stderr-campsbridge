/*
 * Fixed owned-set editing contracts; Firebase tests own transaction and permission invariants.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Fixed owned-set editing contracts; Firebase tests own transaction and permission invariants. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryAllocationsEditTest {
    @get:Rule val main = MainDispatcherRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val clock = MutableClock(anchor)
    private val day = LocalDate.of(2027, 5, 18)
    private val organizerSite = sampleCampsite("site-a", "owner-a")
    private val ownedSite = sampleCampsite("site-b", "owner-b")
    private val sites = FakeCampsiteRepository(listOf(organizerSite, ownedSite))
    private val lottery =
        Lottery(
            "lottery-a",
            organizerSite.ownerId,
            "May family stays",
            "Quiet stays.",
            LotteryConfiguration(
                anchor.plusSeconds(3600),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(172800),
                5,
                null,
            ),
            0,
            anchor,
            anchor,
            participantOwnerIds = setOf(organizerSite.ownerId, ownedSite.ownerId),
        )
    private val organizerOffer = LotteryOffer(organizerSite.id, listOf(day))
    private val ownedOffer = LotteryOffer(ownedSite.id, listOf(day.plusDays(1)))
    private val lotteries = FakeLotteryRepository(listOf(lottery))
    private val replacements = mutableListOf<Triple<String, String, List<LotteryOffer>>>()
    private val store = ViewModelStore()
    private var modelNumber = 0

    /** Supplies related offers and an explicit fake replacement outcome without claiming atomicity. */
    @Before fun configure() {
        lotteries.serverOffers[lottery.id] = listOf(organizerOffer, ownedOffer)
        lotteries.liveOffers.value = mapOf(lottery.id to listOf(organizerOffer, ownedOffer))
        sites.calendarNights.value = listOf(0L, 1L, 4L).map { CalendarNight(day.plusDays(it), true) }
        lotteries.replaceOwnerOffersHandler = { id, actor, offers ->
            replacements += Triple(id, actor, offers)
            lotteries.serverOffers[id] = listOf(organizerOffer) + offers
            lotteries.liveOffers.value = mapOf(id to checkNotNull(lotteries.serverOffers[id]))
        }
    }

    /** Releases required-source and working-calendar subscriptions. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.activeCalendarObservers)
        assertEquals(0, lotteries.activeRecordObservers)
        assertEquals(0, lotteries.activeOfferObservers)
    }

    /** LA-01: draft/published editors save only their complete owned set, including explicit clearing. */
    @Test fun savesTheWholeOwnedSetAndPreservesOtherOwners() =
        runTest {
            for (published in listOf(false, true)) {
                for (empty in listOf(false, true)) {
                    store.clear()
                    runCurrent()
                    configure()
                    replacements.clear()
                    lotteries.records.value = listOf(lottery.copy(publishedAt = if (published) anchor else null))
                    val editor = model()
                    runCurrent()
                    assertEquals(listOf(ownedOffer), content(editor).offers)
                    val expected =
                        if (empty) {
                            editor.removeAllocation(ownedSite.id)
                            emptyList()
                        } else {
                            assertTrue(editor.beginAllocation(ownedSite.id))
                            runCurrent()
                            editor.selectAllocationDates(setOf(day.plusDays(1), day.plusDays(4)))
                            assertTrue(editor.applyAllocation())
                            listOf(LotteryOffer(ownedSite.id, listOf(day.plusDays(1), day.plusDays(4))))
                        }
                    editor.save()
                    runCurrent()
                    assertEquals(listOf(Triple(lottery.id, ownedSite.ownerId, expected)), replacements)
                    assertEquals(
                        listOf(organizerOffer),
                        lotteries.serverOffers.getValue(lottery.id).filter {
                            it.campsiteId ==
                                organizerSite.id
                        },
                    )
                    assertTrue(lotteries.detailsRequests.isEmpty())
                    assertEquals(lottery.id, editor.completion.value)
                }
            }
        }

    /** LA-02: an unavailable saved choice remains visible and cannot be submitted as valid inventory. */
    @Test fun unavailableOwnedChoicesDoNotBecomeASaveableEmptySet() =
        runTest {
            sites.records.value = listOf(organizerSite, ownedSite.copy(publishedAt = null))
            val editor = model()
            runCurrent()
            assertEquals(listOf(ownedOffer), content(editor).offers)
            assertFalse(content(editor).canSave)
            editor.save()
            runCurrent()
            assertTrue(replacements.isEmpty())
        }

    /** LA-02: failed ownership and offered-night reads remain failures instead of editable empty sets. */
    @Test fun requiredReadFailuresCannotClearSavedOffers() =
        runTest {
            for (source in listOf("ownership", "offers")) {
                store.clear()
                runCurrent()
                sites.readFailure = if (source == "ownership") IOException("site unavailable") else null
                lotteries.getOffersHandler =
                    if (source == "offers") { _ -> throw IOException("offers unavailable") } else null
                val editor = model()
                runCurrent()
                assertEquals(LotteryAllocationsEditUiState.Failure, editor.state.value)
                editor.save()
                runCurrent()
                assertTrue(replacements.isEmpty())
            }
        }

    /** LA-02: unknown/unoffered inventory cannot Apply; confirmed Apply changes only local offers. */
    @Test fun calendarApplyRequiresKnownNightsAndDoesNotPersist() =
        runTest {
            sites.calendarNights.value = emptyList()
            val editor = model()
            runCurrent()
            assertTrue(editor.beginAllocation(ownedSite.id))
            runCurrent()
            editor.selectAllocationDates(setOf(day.plusDays(4)))
            assertFalse(editor.applyAllocation())
            assertTrue(replacements.isEmpty())
            sites.calendarNights.value = listOf(1L, 4L).map { CalendarNight(day.plusDays(it), true) }
            runCurrent()
            editor.selectAllocationDates(setOf(day.plusDays(4)))
            assertTrue(editor.applyAllocation())
            assertEquals(listOf(LotteryOffer(ownedSite.id, listOf(day.plusDays(4)))), content(editor).offers)
            assertTrue(replacements.isEmpty())
            assertEquals(0, sites.writeCalls)
        }

    /** LA-02: failed explicit writes retain the complete requested set for another action. */
    @Test fun failedReplacementRetainsRequestedOffers() =
        runTest {
            lotteries.replaceOwnerOffersHandler = { _, _, _ -> throw IOException("offline") }
            val editor = model()
            runCurrent()
            editor.removeAllocation(ownedSite.id)
            editor.save()
            runCurrent()
            assertTrue(content(editor).saveFailed)
            assertEquals(emptyList<LotteryOffer>(), content(editor).offers)
            assertEquals(listOf(organizerOffer, ownedOffer), lotteries.serverOffers.getValue(lottery.id))
        }

    /** LA-03: a conflict refreshes context while keeping desired offers until an explicit resave. */
    @Test fun conflictRefreshNeverResubmitsOrDropsLocalEdits() =
        runTest {
            lotteries.replaceOwnerOffersHandler = { id, actor, offers ->
                replacements += Triple(id, actor, offers)
                throw PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Allocations changed")
            }
            val editor = model()
            runCurrent()
            editor.removeAllocation(ownedSite.id)
            editor.save()
            runCurrent()
            assertTrue(content(editor).contextChanged)
            assertTrue(content(editor).offers.isEmpty())
            assertEquals(1, replacements.size)
            assertTrue(lotteries.events.count { it == "offers:${lottery.id}" } >= 2)
            lotteries.replaceOwnerOffersHandler = { id, actor, offers -> replacements += Triple(id, actor, offers) }
            editor.save()
            runCurrent()
            assertEquals(2, replacements.size)
            assertEquals(replacements.first(), replacements.last())
            assertEquals(lottery.id, editor.completion.value)
        }

    /** LA-03: opening registration, removed membership and cancellation block saving without losing input. */
    @Test fun changedEditingEligibilityBlocksSubmission() =
        runTest {
            for (change in listOf("opening", "membership", "cancellation")) {
                store.clear()
                runCurrent()
                clock.advance(Duration.between(clock.instant(), anchor))
                lotteries.records.value = listOf(lottery)
                val editor = model()
                runCurrent()
                editor.removeAllocation(ownedSite.id)
                when (change) {
                    "opening" -> {
                        clock.advance(Duration.ofHours(1))
                    }

                    "membership" -> {
                        lotteries.records.value =
                            listOf(lottery.copy(participantOwnerIds = setOf(organizerSite.ownerId)))
                    }

                    "cancellation" -> {
                        lotteries.records.value =
                            listOf(lottery.copy(cancellation = Cancellation(anchor, organizerSite.ownerId)))
                    }
                }
                editor.refreshTime()
                runCurrent()
                editor.save()
                runCurrent()
                assertTrue(replacements.isEmpty())
                assertTrue(content(editor).offers.isEmpty())
                assertFalse(content(editor).canSave)
            }
        }

    /** Creates the co-owner's actual saved editor with controlled dependencies.
     * @param saved Primitive raw offers and working-calendar input.
     */
    private fun model(saved: SavedStateHandle = SavedStateHandle()): LotteryAllocationsEditViewModel =
        LotteryAllocationsEditViewModel(lottery.id, ownedSite.ownerId, sites, lotteries, clock, saved).also {
            store.put("allocations-${modelNumber++}", it)
        }

    /** Requires a confirmed context with retained local offers.
     * @param editor Controller under this case.
     */
    private fun content(editor: LotteryAllocationsEditViewModel): LotteryAllocationsEditUiState.Content {
        assertTrue(
            "Expected owned offers content, got ${editor.state.value}",
            editor.state.value is LotteryAllocationsEditUiState.Content,
        )
        return editor.state.value as LotteryAllocationsEditUiState.Content
    }
}

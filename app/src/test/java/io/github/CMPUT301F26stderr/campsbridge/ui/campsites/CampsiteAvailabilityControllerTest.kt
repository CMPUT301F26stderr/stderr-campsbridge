package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/** Lead-owned range/save orchestration contracts; comparison results are supplied test fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteAvailabilityControllerTest {
    @get:Rule val main = MainDispatcherRule()
    private val sites = FakeCampsiteRepository()
    private val scope = CoroutineScope(SupervisorJob() + main.dispatcher)
    private val controllers = mutableListOf<CampsiteAvailabilityController>()
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(14)
    private val second = month.atDay(15)
    private val third = month.atDay(16)

    // Every requested entry in these offer fixtures is a known difference; this is not F08.
    private val allFixtureEntries: AvailabilityDifference = { requested, _ -> requested }

    /** Cancels all owned readers and checks that each scope releases its actual subscription. */
    @After fun close() {
        controllers.forEach { it.close() }
        scope.cancel()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.activeCalendarObservers)
    }

    /** A local form has known empty inventory without querying an invented campsite. */
    @Test fun localTargetKeepsMonthChoicesWithoutReadsOrWrites() =
        runTest {
            val calendar = controller(null)
            runCurrent()
            assertEquals(
                CampsiteInventoryUiState.Content(emptyList()),
                calendar.state.value.inventory,
            )
            calendar.toggleDate(first)
            calendar.changeMonth(month.plusMonths(1))
            calendar.toggleDate(first.plusMonths(1))
            calendar.toggleDate(first)
            assertEquals(
                mapOf(first to false, first.plusMonths(1) to true),
                calendar.state.value.requested,
            )
            assertEquals(month.plusMonths(1), calendar.state.value.month)
            assertTrue(sites.observedCalendars.isEmpty())
            assertEquals(0, sites.writeCalls)
        }

    /** Each displayed month replaces the preceding reader and preserves earlier local edits. */
    @Test fun savedRangesAreScopedAndOldReadersClose() =
        runTest {
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(first)
            calendar.changeMonth(month.plusMonths(1))
            runCurrent()
            assertEquals(1, sites.activeCalendarObservers)
            assertEquals(
                listOf(
                    Triple("site", month.atDay(1), month.plusMonths(1).atDay(1)),
                    Triple("site", month.plusMonths(1).atDay(1), month.plusMonths(2).atDay(1)),
                ),
                sites.observedCalendars,
            )
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            calendar.toggleDate(second)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
        }

    /** Unknown reads cannot be treated as confirmed absence or edited before an explicit retry. */
    @Test fun failedRangeBlocksEditsAndRetriesReadOnly() =
        runTest {
            sites.calendarFailure.value = IOException("offline")
            val calendar = controller()
            runCurrent()
            assertEquals(CampsiteInventoryUiState.Failure, calendar.state.value.inventory)
            calendar.toggleDate(first)
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            sites.calendarFailure.value = null
            calendar.retryCalendar()
            calendar.retryCalendar()
            runCurrent()
            calendar.toggleDate(first)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            assertEquals(1, sites.activeCalendarObservers)
            assertEquals(0, sites.writeCalls)
        }

    /** Successfully loaded missing dates reach F08 as known false, independently of unknown ranges. */
    @Test fun loadedAbsenceIsKnownFalseAtTheComparisonBoundary() =
        runTest {
            var received: Pair<Map<LocalDate, Boolean>, Map<LocalDate, Boolean>>? = null
            val calendar =
                controller(difference = { requested, confirmed ->
                    received = requested to confirmed
                    mapOf(first to true)
                })
            runCurrent()
            calendar.toggleDate(first)
            assertEquals(mapOf(first to true) to mapOf(first to false), received)
            assertEquals(mapOf(first to true), calendar.state.value.pendingEdits)
        }

    /** Booked nights remain visible and cannot acquire a withdrawal request from the UI. */
    @Test fun bookedNightsCannotBeLocallyWithdrawn() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(first, true, "reservation"))
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(first)
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            assertEquals(0, sites.writeCalls)
        }

    /** Explicit batch choices reject occupied and out-of-range dates before any save operation. */
    @Test fun explicitBatchChoiceKeepsOnlyKnownUnoccupiedDates() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(second, true, "reservation"))
            val calendar = controller()
            runCurrent()
            calendar.setRequestedOffered(setOf(first, second, first.plusMonths(1)), true)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            calendar.setRequestedOffered(setOf(first), false)
            assertEquals(mapOf(first to false), calendar.state.value.requested)
            assertEquals(0, sites.writeCalls)
        }

    /** Missing teammate behavior preserves the desired values and blocks real inventory writes. */
    @Test fun missingComparisonNeverPretendsToSaveRequestedDates() =
        runTest {
            val calendar = controller(difference = null)
            runCurrent()
            calendar.toggleDate(first)
            assertNull(calendar.state.value.pendingEdits)
            assertFalse(calendar.save())
            assertEquals(AvailabilitySaveFailure.COMPARISON_PENDING, calendar.state.value.failure)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            assertEquals(0, sites.writeCalls)
        }

    /** Local selections cannot write until an actual saved parent has been explicitly bound. */
    @Test fun savingRequiresAnActualSavedParent() =
        runTest {
            val calendar = controller(null)
            runCurrent()
            calendar.toggleDate(first)
            assertFalse(calendar.save())
            assertEquals(0, sites.writeCalls)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            calendar.bindSavedTarget("created-site")
            runCurrent()
            sites.offerHandler = { _, _, _, _ -> }
            assertTrue(calendar.save())
            assertEquals("created-site", sites.offerRequests.single().id)
            assertTrue(sites.observedCalendars.all { it.first == "created-site" })
        }

    /** Each write has one ascending date and is removed from requested intent only after success. */
    @Test fun savesAscendingSingletonsAndAcknowledgesEach() =
        runTest {
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(third)
            calendar.toggleDate(first)
            calendar.toggleDate(second)
            sites.offerHandler = { _, owner, dates, offered ->
                assertEquals("owner", owner)
                assertEquals(1, dates.size)
                assertTrue(offered)
                assertTrue(
                    calendar.state.value.requested
                        .containsKey(dates.single()),
                )
            }
            assertTrue(calendar.save())
            assertEquals(
                listOf(first, second, third),
                sites.offerRequests.map { it.dates.single() },
            )
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            assertFalse(calendar.state.value.isSaving)
        }

    /** A failed second write keeps it and later dates while confirmed progress is never repeated. */
    @Test fun partialFailureStopsAndRetriesOnlyUnacknowledgedDates() =
        runTest {
            val calendar = controller()
            runCurrent()
            listOf(third, first, second).forEach(calendar::toggleDate)
            sites.offerHandler = { _, _, dates, _ ->
                if (dates.single() == second) throw IOException("second write failed")
            }
            assertFalse(calendar.save())
            assertEquals(listOf(first, second), sites.offerRequests.map { it.dates.single() })
            assertEquals(mapOf(second to true, third to true), calendar.state.value.requested)
            assertEquals(AvailabilitySaveFailure.WRITE, calendar.state.value.failure)
            sites.offerHandler = { _, _, _, _ -> }
            assertTrue(calendar.save())
            assertEquals(
                listOf(first, second, second, third),
                sites.offerRequests.map { it.dates.single() },
            )
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
        }

    /** A listener showing the requested value cannot acknowledge a failed repository operation. */
    @Test fun matchingCachedEmissionCannotConfirmAFailedWithdrawal() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            val outcomes =
                mapOf(
                    (mapOf(first to false) to mapOf(first to true)) to mapOf(first to false),
                    (mapOf(first to false) to mapOf(first to false)) to emptyMap(),
                )
            val calendar =
                controller(difference = { requested, confirmed ->
                    checkNotNull(
                        outcomes[requested to confirmed],
                    ) { "Unexpected comparison fixture" }
                })
            runCurrent()
            calendar.toggleDate(first)
            sites.offerHandler = { _, _, _, _ ->
                sites.calendarNights.value = listOf(CalendarNight(first, false))
                throw IOException("acknowledgement lost")
            }
            assertFalse(calendar.save())
            runCurrent()
            assertEquals(mapOf(first to false), calendar.state.value.requested)
            assertEquals(mapOf(first to false), calendar.state.value.pendingEdits)
            sites.offerHandler = { _, _, _, offered -> assertFalse(offered) }
            assertTrue(calendar.save())
            assertEquals(2, sites.offerRequests.size)
        }

    /** Restoration retains an unresolved withdrawal even when the visible range fails to reload. */
    @Test fun restoredWithdrawalSurvivesUnknownInventoryAndRetriesExplicitly() =
        runTest {
            val saved = SavedStateHandle()
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            val original = controller(saved = saved)
            runCurrent()
            original.toggleDate(first)
            original.changeMonth(month.plusMonths(1))
            runCurrent()
            sites.offerHandler = { _, _, _, _ -> throw IOException("unacknowledged") }
            assertFalse(original.save())
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            original.close()
            sites.calendarFailure.value = IOException("range unavailable")
            val restored = controller(saved = snapshot)
            runCurrent()
            assertEquals(month.plusMonths(1), restored.state.value.month)
            assertEquals(CampsiteInventoryUiState.Failure, restored.state.value.inventory)
            assertEquals(mapOf(first to false), restored.state.value.requested)
            assertEquals(1, sites.offerRequests.size)
            sites.offerHandler = { _, _, _, offered -> assertFalse(offered) }
            assertTrue(restored.save())
            assertEquals(2, sites.offerRequests.size)
            assertTrue(
                restored.state.value.requested
                    .isEmpty(),
            )
        }

    /** A restored historical baseline cannot clear a desired value when the new range is unknown. */
    @Test fun restoredHistoricalEqualityCannotElideAnUnknownRequest() =
        runTest {
            val saved = SavedStateHandle()
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            val original = controller(saved = saved, difference = { _, _ -> emptyMap() })
            runCurrent()
            original.toggleDate(first)
            original.toggleDate(first)
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            original.close()
            sites.calendarNights.value = emptyList()
            sites.calendarFailure.value = IOException("no fresh baseline")
            val restored = controller(saved = snapshot, difference = { _, _ -> emptyMap() })
            runCurrent()
            assertEquals(mapOf(first to true), restored.state.value.pendingEdits)
            sites.offerHandler = { _, _, _, offered -> assertTrue(offered) }
            assertTrue(restored.save())
            assertEquals(1, sites.offerRequests.size)
        }

    /** A failed current range invalidates equality inferred from an earlier successful snapshot. */
    @Test fun failedRangeCannotElideAnEarlierEqualChoice() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            val calendar = controller(difference = { _, _ -> emptyMap() })
            runCurrent()
            calendar.toggleDate(first)
            calendar.toggleDate(first)
            sites.calendarFailure.value = IOException("range is now unknown")
            runCurrent()
            assertEquals(mapOf(first to true), calendar.state.value.pendingEdits)
            sites.offerHandler = { _, _, _, offered -> assertTrue(offered) }
            assertTrue(calendar.save())
            assertEquals(1, sites.offerRequests.size)
        }

    /** Releasing a month reader also releases its authority to skip an offscreen requested write. */
    @Test fun offscreenEqualityCannotElideARequestedDate() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            val calendar = controller(difference = { _, _ -> emptyMap() })
            runCurrent()
            calendar.toggleDate(first)
            calendar.toggleDate(first)
            calendar.changeMonth(month.plusMonths(1))
            runCurrent()
            sites.calendarNights.value = listOf(CalendarNight(first, false))
            runCurrent()
            assertEquals(mapOf(first to true), calendar.state.value.pendingEdits)
            sites.offerHandler = { _, _, dates, offered ->
                assertEquals(setOf(first), dates)
                assertTrue(offered)
            }
            assertTrue(calendar.save())
            assertEquals(1, sites.offerRequests.size)
        }

    /** Submitted values remain frozen while an operation is held; repeated saves do not overlap. */
    @Test fun heldWriteBlocksInputMonthDiscardAndDuplicateSave() =
        runTest {
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(first)
            val gate = CompletableDeferred<Unit>()
            sites.offerHandler = { _, _, _, _ -> gate.await() }
            val saving = launch { assertTrue(calendar.save()) }
            runCurrent()
            assertTrue(calendar.state.value.isSaving)
            calendar.toggleDate(second)
            calendar.changeMonth(month.plusMonths(1))
            calendar.discard()
            assertFalse(calendar.save())
            assertEquals(month, calendar.state.value.month)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            assertEquals(1, sites.offerRequests.size)
            gate.complete(Unit)
            saving.join()
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
        }

    /** Coroutine cancellation remains cancellation and never acknowledges a suspended write. */
    @Test fun cancellationRetainsUnacknowledgedIntent() =
        runTest {
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(first)
            val gate = CompletableDeferred<Unit>()
            sites.offerHandler = { _, _, _, _ -> gate.await() }
            val saving = launch { calendar.save() }
            runCurrent()
            saving.cancelAndJoin()
            assertTrue(saving.isCancelled)
            assertEquals(mapOf(first to true), calendar.state.value.requested)
            assertFalse(calendar.state.value.isSaving)
            sites.offerHandler = { _, _, _, _ -> }
            assertTrue(calendar.save())
            assertEquals(2, sites.offerRequests.size)
        }

    /** F08 may confirm that local choices equal the known baseline without issuing a mutation. */
    @Test fun confirmedEqualChoicesNeedNoWrites() =
        runTest {
            val calendar = controller(difference = { _, _ -> emptyMap() })
            runCurrent()
            calendar.toggleDate(first)
            calendar.toggleDate(first)
            assertEquals(mapOf(first to false), calendar.state.value.requested)
            assertEquals(emptyMap<LocalDate, Boolean>(), calendar.state.value.pendingEdits)
            assertTrue(calendar.save())
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            assertEquals(0, sites.writeCalls)
        }

    /** Discard abandons only local choices and close cancels the active range. */
    @Test fun discardAndCloseNeverMutateInventory() =
        runTest {
            val calendar = controller()
            runCurrent()
            calendar.toggleDate(first)
            calendar.discard()
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            calendar.close()
            runCurrent()
            calendar.toggleDate(second)
            assertTrue(
                calendar.state.value.requested
                    .isEmpty(),
            )
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(0, sites.writeCalls)
        }

    /** Creates one feature-owned controller with explicit fixture-only comparison behavior.
     * @param id Actual saved parent, or null for local setup.
     * @param saved Primitive state retained across controller reconstruction.
     * @param difference Fixed expected comparison outcomes; null leaves F08 pending.
     */
    private fun controller(
        id: String? = "site",
        saved: SavedStateHandle = SavedStateHandle(),
        difference: AvailabilityDifference? = allFixtureEntries,
    ): CampsiteAvailabilityController =
        CampsiteAvailabilityController(
            "owner",
            id,
            sites,
            month,
            saved,
            "calendar.",
            scope,
            difference,
        ).also(controllers::add)
}

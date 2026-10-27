package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Editor contracts reuse the shared save owner; fixed comparisons are not an F08 implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val site = sampleCampsite()
    private val sites = FakeCampsiteRepository(listOf(site))
    private val clock = Clock.fixed(Instant.parse("2027-06-01T01:00:00Z"), ZoneOffset.UTC)
    private val month = YearMonth.of(2027, 5)
    private val first = month.atDay(14)
    private val second = month.atDay(15)
    private val store = ViewModelStore()
    private var created = 0

    /** Releases every saved-parent controller and outstanding write after the case. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, sites.activeCalendarObservers)
    }

    /** The saved listing's zone determines the first range, independently of the device's UTC month. */
    @Test fun loadsActualParentAndUsesItsLocalMonth() =
        runTest {
            val model = model()
            runCurrent()
            assertEquals(site.id, content(model).campsite.id)
            assertEquals(month, content(model).availability.month)
            assertEquals(listOf(site.id), sites.readIds)
            assertEquals(
                Triple(site.id, month.atDay(1), month.plusMonths(1).atDay(1)),
                sites.observedCalendars.single(),
            )
            assertEquals(0, sites.writeCalls)
        }

    /** Confirmed unavailable targets never open a calendar or create replacement listings. */
    @Test fun unavailableParentsCannotAcquireAnEditor() =
        runTest {
            for (record in listOf(
                null,
                site.copy(ownerId = "other"),
                site.copy(removal = Removal(Instant.EPOCH, "admin")),
            )) {
                sites.records.value = listOfNotNull(record)
                val model = model()
                runCurrent()
                assertEquals(AvailabilityUiState.Unavailable, model.state.value)
                model.save()
                model.requestExit()
                assertTrue(model.exitRequested.value)
            }
            assertTrue(sites.observedCalendars.isEmpty())
            assertEquals(0, sites.writeCalls)
        }

    /** Failed parent reads retry once without enabling unknown inventory or duplicate loads. */
    @Test fun failedParentReadRetriesWithAHeldLoadingGuard() =
        runTest {
            sites.readFailure = IOException("offline")
            val model = model()
            runCurrent()
            assertEquals(AvailabilityUiState.Failure, model.state.value)
            sites.readFailure = null
            val gate = CompletableDeferred<Unit>()
            sites.readGate = gate
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(AvailabilityUiState.Loading, model.state.value)
            assertEquals(2, sites.readIds.size)
            model.selectDates(setOf(first))
            model.save()
            assertEquals(0, sites.writeCalls)
            gate.complete(Unit)
            runCurrent()
            assertEquals(site.id, content(model).campsite.id)
        }

    /** Selection and Offer/Withdraw are local; booked and offscreen dates cannot become work. */
    @Test fun selectionPrecedesExplicitOfferAndWithdrawal() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(second, true, "reservation"))
            val model = model()
            runCurrent()
            model.selectDates(setOf(first, second, first.plusMonths(1)))
            assertEquals(setOf(first), content(model).selectedDates)
            assertTrue(content(model).availability.requested.isEmpty())
            model.setRequestedOffered(true)
            runCurrent()
            assertEquals(mapOf(first to true), content(model).availability.requested)
            assertTrue(content(model).selectedDates.isEmpty())
            model.selectDates(setOf(first))
            model.setRequestedOffered(false)
            runCurrent()
            assertEquals(mapOf(first to false), content(model).availability.requested)
            assertEquals(0, sites.writeCalls)
        }

    /** Month changes clear only the working selection, retaining earlier requested date changes. */
    @Test fun changingMonthRetainsWorkAndReplacesItsRange() =
        runTest {
            val model = model()
            runCurrent()
            model.selectDates(setOf(first))
            model.setRequestedOffered(true)
            model.selectDates(setOf(second))
            model.changeMonth(month.plusMonths(1))
            runCurrent()
            assertEquals(mapOf(first to true), content(model).availability.requested)
            assertTrue(content(model).selectedDates.isEmpty())
            assertEquals(month.plusMonths(1), content(model).availability.month)
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Missing comparison behavior blocks commit while preserving an inspectable requested change. */
    @Test fun missingComparisonCannotClaimSaveSuccess() =
        runTest {
            val model = model(difference = null)
            runCurrent()
            model.selectDates(setOf(first))
            model.setRequestedOffered(true)
            model.save()
            runCurrent()
            assertNull(content(model).availability.pendingEdits)
            assertFalse(content(model).saved)
            assertEquals(mapOf(first to true), content(model).availability.requested)
            assertEquals(0, sites.writeCalls)
        }

    /** A held save freezes changes and exits synchronously, then confirms success in place. */
    @Test fun heldSaveGuardsDuplicateActionsAndRemainsInEditor() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.offerHandler = { _, _, _, _ -> gate.await() }
            val model = model()
            runCurrent()
            model.selectDates(setOf(first))
            model.setRequestedOffered(true)
            model.save()
            model.save()
            model.changeMonth(month.plusMonths(1))
            model.selectDates(setOf(second))
            model.setRequestedOffered(false)
            model.requestExit()
            model.confirmDiscard()
            runCurrent()
            assertTrue(content(model).availability.isSaving)
            assertEquals(month, content(model).availability.month)
            assertEquals(mapOf(first to true), content(model).availability.requested)
            assertEquals(1, sites.offerRequests.size)
            assertFalse(model.exitRequested.value)
            gate.complete(Unit)
            runCurrent()
            assertTrue(content(model).saved)
            assertTrue(content(model).availability.requested.isEmpty())
            assertFalse(model.exitRequested.value)
            model.requestExit()
            assertTrue(model.exitRequested.value)
        }

    /** Partial acknowledgement clears only confirmed dates; a listener cannot authorize failed progress. */
    @Test fun partialSaveRetainsUnconfirmedDatesAndRetriesInPlace() =
        runTest {
            sites.offerHandler =
                { _, _, dates, _ -> if (dates.single() == second) throw IOException("conflict") }
            val model = model()
            runCurrent()
            model.selectDates(setOf(second, first))
            model.setRequestedOffered(true)
            model.save()
            runCurrent()
            assertFalse(content(model).saved)
            assertEquals(AvailabilitySaveFailure.WRITE, content(model).availability.failure)
            assertEquals(mapOf(second to true), content(model).availability.requested)
            sites.calendarNights.value =
                listOf(CalendarNight(first, true), CalendarNight(second, true))
            runCurrent()
            assertEquals(mapOf(second to true), content(model).availability.pendingEdits)
            sites.offerHandler = { _, _, _, _ -> }
            model.save()
            runCurrent()
            assertEquals(
                listOf(first, second, second),
                sites.offerRequests.map { it.dates.single() },
            )
            assertTrue(content(model).saved)
        }

    /** Recreated unresolved withdrawals remain explicit work and never automatically replay a write. */
    @Test fun restorationKeepsRequestedWithdrawalAndVisibleMonth() =
        runTest {
            sites.calendarNights.value = listOf(CalendarNight(first, true))
            sites.offerHandler = { _, _, _, _ -> throw IOException("unknown acknowledgement") }
            val saved = SavedStateHandle()
            val firstModel = model(saved)
            runCurrent()
            firstModel.selectDates(setOf(first))
            firstModel.setRequestedOffered(false)
            firstModel.save()
            runCurrent()
            firstModel.changeMonth(month.plusMonths(1))
            runCurrent()
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            store.clear()
            runCurrent()
            val restored = model(snapshot)
            runCurrent()
            assertEquals(month.plusMonths(1), content(restored).availability.month)
            assertEquals(mapOf(first to false), content(restored).availability.pendingEdits)
            assertEquals(1, sites.offerRequests.size)
            assertFalse(content(restored).saved)
        }

    /** Range failure cannot be edited; retry preserves the actual target and local work. */
    @Test fun calendarFailureRequiresItsOwnReadOnlyRetry() =
        runTest {
            sites.calendarFailure.value = IOException("range offline")
            val model = model()
            runCurrent()
            assertEquals(CalendarInventoryRead.Failure, content(model).availability.inventory)
            model.selectDates(setOf(first))
            model.setRequestedOffered(true)
            assertTrue(content(model).selectedDates.isEmpty())
            assertTrue(content(model).availability.requested.isEmpty())
            sites.calendarFailure.value = null
            model.retryCalendar()
            runCurrent()
            assertTrue(content(model).availability.inventory is CalendarInventoryRead.Content)
            assertEquals(listOf(site.id), sites.readIds)
            assertEquals(0, sites.writeCalls)
        }

    /** Discard clears only local intent and never attempts an undo of a confirmed first date. */
    @Test fun discardAfterPartialSaveReturnsWithoutReversingConfirmedDates() =
        runTest {
            sites.offerHandler =
                { _, _, dates, _ -> if (dates.single() == second) throw IOException("failed") }
            val model = model()
            runCurrent()
            model.selectDates(setOf(first, second))
            model.setRequestedOffered(true)
            model.save()
            runCurrent()
            model.requestExit()
            assertTrue(model.discardPrompt.value)
            model.dismissDiscard()
            assertFalse(model.discardPrompt.value)
            model.requestExit()
            model.confirmDiscard()
            runCurrent()
            assertTrue(model.exitRequested.value)
            assertEquals(2, sites.offerRequests.size)
            assertTrue(content(model).availability.requested.isEmpty())
        }

    /** A held restored parent read cannot silently discard pending requested inventory work. */
    @Test fun restoredRequestsRequireDiscardBeforeParentReadCompletes() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.readGate = gate
            val model =
                model(
                    SavedStateHandle(
                        mapOf(
                            "availability.requested.dates" to arrayListOf(first.toString()),
                            "availability.requested.offered" to booleanArrayOf(false),
                            "availability.month" to month.toString(),
                        ),
                    ),
                )
            runCurrent()
            model.requestExit()
            assertFalse(model.exitRequested.value)
            assertTrue(model.discardPrompt.value)
            model.dismissDiscard()
            gate.complete(Unit)
            runCurrent()
            assertFalse(model.discardPrompt.value)
            assertEquals(mapOf(first to false), content(model).availability.requested)
            assertEquals(0, sites.writeCalls)
        }

    /** Failed restored metadata retains a working selection until explicit abandonment. */
    @Test fun restoredSelectionRequiresDiscardAfterParentReadFails() =
        runTest {
            sites.readFailure = IOException("parent offline")
            val model =
                model(
                    SavedStateHandle(
                        mapOf("availability.selection" to arrayListOf(first.toString())),
                    ),
                )
            runCurrent()
            assertEquals(AvailabilityUiState.Failure, model.state.value)
            model.requestExit()
            assertFalse(model.exitRequested.value)
            assertTrue(model.discardPrompt.value)
            model.confirmDiscard()
            assertTrue(model.exitRequested.value)
            assertEquals(0, sites.writeCalls)
        }

    /** Builds a separately scoped editor with a fixed supplied comparison outcome.
     * @param saved Primitive restoration store.
     * @param difference Explicit fixture or missing teammate integration.
     */
    private fun model(
        saved: SavedStateHandle = SavedStateHandle(),
        difference: AvailabilityDifference? = { requested, _ -> requested },
    ): AvailabilityViewModel =
        AvailabilityViewModel(site.ownerId, site.id, sites, clock, saved, difference).also {
            store.put("availability-${created++}", it)
        }

    /** Requires loaded content before asserting its observable fields.
     * @param model Editor under test.
     */
    private fun content(model: AvailabilityViewModel): AvailabilityUiState.Content {
        assertTrue(
            "Expected resolved saved-parent editor",
            model.state.value is AvailabilityUiState.Content,
        )
        return model.state.value as AvailabilityUiState.Content
    }
}

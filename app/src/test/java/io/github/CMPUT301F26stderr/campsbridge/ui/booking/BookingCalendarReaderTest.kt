package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/** Two precise inventory scopes stay independent without duplicating an in-month subscription. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingCalendarReaderTest {
    private val month = YearMonth.of(2027, 5)
    private val start = month.atDay(31)
    private val inside = month.atDay(10)
    private val monthRows =
        listOf(
            CalendarNight(inside, true),
            CalendarNight(inside.plusDays(1), true),
            CalendarNight(start, true),
        )
    private val stayRows =
        listOf(CalendarNight(start, true), CalendarNight(start.plusDays(1), true))
    private val sites =
        FakeCampsiteRepository(listOf(sampleCampsite("site"))).apply {
            calendarNights.value =
                monthRows +
                listOf(
                    CalendarNight(start.plusDays(1), true),
                    CalendarNight(start.plusDays(2), true),
                )
        }
    private val readers = mutableListOf<BookingCalendarReader>()

    /** Retires every plain child after the test scope has cancelled its background work. */
    @After fun close() {
        readers.forEach { it.close() }
    }

    /** Construction and irrelevant retries do not start speculative source work. */
    @Test fun idleReaderHasNoRequestedScopes() =
        runTest {
            val reader = reader(backgroundScope)
            reader.retryVisible()
            reader.retrySelection()
            runCurrent()
            assertEquals(BookingCalendarReadState(), reader.state.value)
            assertEquals(emptyList<Triple<String, LocalDate, LocalDate>>(), sites.observedCalendars)
        }

    /** One visible month reads exactly its half-open range and identical updates reuse it. */
    @Test fun visibleMonthUsesOneHalfOpenQuery() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month)
            assertEquals(CalendarInventoryRead.Loading, reader.state.value.visible)
            reader.update(month)
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.visible)
            assertEquals(null, reader.state.value.selected)
            assertEquals(
                listOf(Triple("site", month.atDay(1), month.plusMonths(1).atDay(1))),
                sites.observedCalendars,
            )
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** A stay wholly covered by the visible month shares that source instead of querying again. */
    @Test fun inMonthSelectionReusesVisibleSource() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month, inside, inside.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.visible)
            assertEquals(reader.state.value.visible, reader.state.value.selected)
            assertEquals(1, sites.observedCalendars.size)
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Crossing months adds the exact occupied range, excluding checkout and unrelated dates. */
    @Test fun crossMonthSelectionHasItsOwnExactRange() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.visible)
            assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
            assertEquals(
                setOf(
                    Triple("site", month.atDay(1), month.plusMonths(1).atDay(1)),
                    Triple("site", start, start.plusDays(2)),
                ),
                sites.observedCalendars.toSet(),
            )
            assertEquals(2, sites.activeCalendarObservers)
        }

    /** An unfinished selection retains its arrival night even after that month goes offscreen. */
    @Test fun arrivalWithoutCheckoutReadsOneSelectedNight() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(null, start)
            runCurrent()
            assertEquals(null, reader.state.value.visible)
            assertEquals(
                CalendarInventoryRead.Content(listOf(CalendarNight(start, true))),
                reader.state.value.selected,
            )
            assertEquals(listOf(Triple("site", start, start.plusDays(1))), sites.observedCalendars)
        }

    /** A later visible month confirms only its preceding night for a pending checkout. */
    @Test fun unfinishedCrossMonthStayConfirmsOnlyTheVisibleBoundaryNight() =
        runTest {
            val reader = reader(backgroundScope)
            val nextMonth = month.plusMonths(1)
            reader.update(nextMonth, inside)
            runCurrent()
            assertEquals(
                CalendarInventoryRead.Content(listOf(CalendarNight(start, true))),
                reader.state.value.selected,
            )
            assertTrue(sites.observedCalendars.contains(Triple("site", start, nextMonth.atDay(1))))
            assertEquals(2, sites.activeCalendarObservers)
            reader.update(null, inside)
            runCurrent()
            assertEquals(
                CalendarInventoryRead.Content(listOf(CalendarNight(inside, true))),
                reader.state.value.selected,
            )
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Browsing ahead reads one supporting night before checkout is selected. */
    @Test fun partialSelectionReadsOnlyOneBoundaryNightWhenBrowsingFarAhead() =
        runTest {
            val reader = reader(backgroundScope)
            val distant = month.plusYears(3)
            reader.update(distant, inside)
            runCurrent()
            assertTrue(
                sites.observedCalendars.contains(
                    Triple(
                        "site",
                        distant.atDay(1).minusDays(1),
                        distant.atDay(1),
                    ),
                ),
            )
            assertEquals(CalendarInventoryRead.Content(emptyList()), reader.state.value.selected)
            assertEquals(2, sites.activeCalendarObservers)
        }

    /** Leaving the dates page keeps a complete selected range live without its unused month. */
    @Test fun hidingCalendarRetainsOnlyExactSelection() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month, inside, inside.plusDays(2))
            runCurrent()
            reader.update(null, inside, inside.plusDays(2))
            runCurrent()
            assertEquals(null, reader.state.value.visible)
            assertEquals(
                CalendarInventoryRead.Content(monthRows.take(2)),
                reader.state.value.selected,
            )
            assertEquals(2, sites.observedCalendars.size)
            assertEquals(Triple("site", inside, inside.plusDays(2)), sites.observedCalendars.last())
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** Changing an unrelated month retains the exact selected-range reader. */
    @Test fun changingVisibleMonthRetainsOffscreenSelectionReader() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month.plusMonths(2), start, start.plusDays(2))
            runCurrent()
            reader.update(month.plusMonths(3), start, start.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
            assertEquals(1, sites.observedCalendars.count { it.second == start })
            assertEquals(3, sites.observedCalendars.size)
            assertEquals(2, sites.activeCalendarObservers)
        }

    /** Replacing a selection retires just that query and cannot retain its former content. */
    @Test fun changingOffscreenSelectionRetainsVisibleReader() =
        runTest {
            val reader = reader(backgroundScope)
            val visible = month.plusMonths(2)
            reader.update(visible, inside, inside.plusDays(2))
            runCurrent()
            reader.update(visible, start, start.plusDays(2))
            assertEquals(CalendarInventoryRead.Loading, reader.state.value.selected)
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
            assertEquals(1, sites.observedCalendars.count { it.second == visible.atDay(1) })
            assertEquals(3, sites.observedCalendars.size)
            assertEquals(2, sites.activeCalendarObservers)
        }

    /** A confirmed empty query is content, distinct from loading or failed inventory. */
    @Test fun emptyInventoryIsKnownContent() =
        runTest {
            sites.calendarNights.value = emptyList()
            val reader = reader(backgroundScope)
            reader.update(month, inside, inside.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(emptyList()), reader.state.value.visible)
            assertEquals(reader.state.value.visible, reader.state.value.selected)
        }

    /** Month failure and retry leave the independent selected range confirmed and subscribed. */
    @Test fun failedVisibleSourceRetriesWithoutRestartingSelection() =
        runTest {
            var failMonth = true
            sites.calendarHandler = { _, first, _ ->
                if (first == month.atDay(1) && failMonth) {
                    flow { throw IOException("month offline") }
                } else {
                    MutableStateFlow(if (first == month.atDay(1)) monthRows else stayRows)
                }
            }
            val reader = reader(backgroundScope)
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Failure, reader.state.value.visible)
            assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
            failMonth = false
            reader.retryVisible()
            reader.retryVisible()
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.visible)
            assertEquals(1, sites.observedCalendars.count { it.second == start })
            assertEquals(3, sites.observedCalendars.size)
        }

    /** Selection failure and retry leave the visible month alone. */
    @Test fun failedSelectionRetriesWithoutRestartingVisibleMonth() =
        runTest {
            var failSelection = true
            sites.calendarHandler = { _, first, _ ->
                if (first == start && failSelection) {
                    flow { throw IOException("stay offline") }
                } else {
                    MutableStateFlow(if (first == start) stayRows else monthRows)
                }
            }
            val reader = reader(backgroundScope)
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.visible)
            assertEquals(CalendarInventoryRead.Failure, reader.state.value.selected)
            failSelection = false
            reader.retrySelection()
            reader.retrySelection()
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
            assertEquals(1, sites.observedCalendars.count { it.second == month.atDay(1) })
            assertEquals(3, sites.observedCalendars.size)
        }

    /** A failed shared month is retried once through either public retry action. */
    @Test fun sharedSelectionRetryDelegatesToTheMonthSource() =
        runTest {
            sites.calendarFailure.value = IOException("offline")
            val reader = reader(backgroundScope)
            reader.update(month, inside, inside.plusDays(2))
            runCurrent()
            assertEquals(CalendarInventoryRead.Failure, reader.state.value.visible)
            assertEquals(CalendarInventoryRead.Failure, reader.state.value.selected)
            sites.calendarFailure.value = null
            reader.retrySelection()
            reader.retryVisible()
            runCurrent()
            assertEquals(CalendarInventoryRead.Content(monthRows), reader.state.value.selected)
            assertEquals(2, sites.observedCalendars.size)
            assertEquals(1, sites.activeCalendarObservers)
        }

    /** A noncooperative retired range cannot overwrite its replacement or keep a live listener. */
    @Test fun lateSelectedRangeCannotReplaceTheCurrentRange() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            sites.calendarHandler = { _, first, _ ->
                if (first ==
                    inside
                ) {
                    flow {
                        withContext(NonCancellable) { gate.await() }
                        emit(monthRows.take(2))
                    }
                } else {
                    MutableStateFlow(stayRows)
                }
            }
            val reader = reader(backgroundScope)
            try {
                reader.update(null, inside, inside.plusDays(2))
                runCurrent()
                reader.update(null, start, start.plusDays(2))
                runCurrent()
                assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
                gate.complete(Unit)
                runCurrent()
                assertEquals(CalendarInventoryRead.Content(stayRows), reader.state.value.selected)
                assertEquals(1, sites.activeCalendarObservers)
            } finally {
                gate.complete(Unit)
            }
        }

    /** A temporary parent stop releases both scopes and permits a later fresh same-parent read. */
    @Test fun stopRetiresBothQueriesAndCanReactivate() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(2, sites.activeCalendarObservers)
            reader.stop()
            runCurrent()
            assertEquals(BookingCalendarReadState(), reader.state.value)
            assertEquals(0, sites.activeCalendarObservers)
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(2, sites.activeCalendarObservers)
            assertEquals(4, sites.observedCalendars.size)
        }

    /** Closing before collection prevents both queued work and every later stale public action. */
    @Test fun closedReaderCannotRestartThroughStaleCallbacks() =
        runTest {
            val reader = reader(backgroundScope)
            reader.update(month)
            assertEquals(CalendarInventoryRead.Loading, reader.state.value.visible)
            reader.close()
            runCurrent()
            reader.update(month, start, start.plusDays(2))
            reader.retryVisible()
            reader.retrySelection()
            runCurrent()
            assertEquals(BookingCalendarReadState(), reader.state.value)
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(0, sites.observedCalendars.size)
        }

    /** Coroutine cancellation stays cancellation rather than a user-visible transport failure. */
    @Test fun cancelledSourceDoesNotBecomeFailure() =
        runTest {
            sites.calendarHandler = { _, _, _ -> flow { throw CancellationException("retired") } }
            val reader = reader(backgroundScope)
            reader.update(month)
            runCurrent()
            assertEquals(CalendarInventoryRead.Loading, reader.state.value.visible)
            assertEquals(1, sites.observedCalendars.size)
            assertEquals(0, sites.activeCalendarObservers)
        }

    /** Incomplete/invalid endpoints cannot create a reversed or overflowing calendar query. */
    @Test fun invalidOrMissingSelectionHasNoOwnQuery() =
        runTest {
            val reader = reader(backgroundScope)
            for ((first, end) in listOf(
                null to inside,
                inside to inside,
                inside to inside.minusDays(1),
                LocalDate.MAX to null,
            )) {
                reader.update(null, first, end)
                runCurrent()
                assertEquals(BookingCalendarReadState(), reader.state.value)
            }
            assertEquals(0, sites.observedCalendars.size)
        }

    /** Immediate source callbacks cannot reopen a second scope after their parent stops reading. */
    @Test fun reentrantStopCannotStartAQueuedSelectionReader() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val reader = reader(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
            backgroundScope.launch(dispatcher) {
                reader.state.collect {
                    if (it.visible is CalendarInventoryRead.Content) {
                        reader
                            .stop()
                    }
                }
            }
            reader.update(month, start, start.plusDays(2))
            runCurrent()
            assertEquals(BookingCalendarReadState(), reader.state.value)
            assertEquals(1, sites.observedCalendars.size)
            assertEquals(0, sites.activeCalendarObservers)
        }

    /** Creates one fixed-ID reader without a new ViewModel, clock, F15 fallback or draft owner.
     * @param scope Explicit test-controlled lifetime.
     */
    private fun reader(scope: CoroutineScope): BookingCalendarReader =
        BookingCalendarReader("site", sites, scope).also { readers.add(it) }
}

/*
 * Tests actor-scoped reservation lists with injected category and ordering outcomes.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** Fixed owner contracts with supplied category/order outcomes, not helper implementations. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val base = CampingFixtures.reservation()
    private val clock = MutableClock(Instant.parse("2027-05-16T23:59:59Z"))
    private val bookings = FakeBookingRepository()
    private val accounts = FakeAccountRepository()
    private val ownerRequests = mutableListOf<String>()
    private val ownerFailure = MutableStateFlow<Exception?>(null)
    private var ownerHandler: (() -> Flow<List<Reservation>>)? = null
    private var activeOwners = 0
    private val categories = mutableMapOf<String, ReservationCategory>()
    private val repository =
        object : BookingRepository by bookings {
            /** Supplies only the controlled owner aggregate and records its actual lifetime.
             * @param ownerId Exact requested owner identity.
             */
            override fun observeForOwner(ownerId: String): Flow<List<Reservation>> =
                flow {
                    ownerRequests += ownerId
                    activeOwners++
                    try {
                        val source =
                            ownerHandler?.invoke()
                                ?: combine(bookings.records, ownerFailure) { rows, failure ->
                                    failure?.let { throw it }
                                    rows
                                }
                        source.collect { emit(it) }
                    } finally {
                        activeOwners--
                    }
                }
        }

    /** A known empty account remains distinct from Loading and from unimplemented helpers. */
    @Test fun emptyEntrantSourceIsConfirmedWithBothDependenciesExplicit() =
        listTest {
            val model = model(classify = null, order = null)
            assertEquals(ReservationListUiState.Loading, model.state.value)
            runCurrent()
            assertTrue(content(model).items.isEmpty())
            assertTrue(content(model).classificationPending)
            assertTrue(content(model).orderingPending)
            assertEquals(listOf("entrant"), bookings.entrantRequests)
            assertTrue(ownerRequests.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
            assertTrue(bookings.reserveRequests.isEmpty())
            assertTrue(accounts.readIds.isEmpty())
        }

    /** Owner uses one aggregate across campsites, without entrant or per-record reads. */
    @Test fun ownerReadsAllItsScopedReservationsOnce() =
        listTest {
            bookings.records.value =
                listOf(base, base.copy(id = "other", campsiteId = "site-2", entrantId = "other"))
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertEquals(listOf("reservation", "other"), ids(model))
            assertEquals(listOf("owner"), ownerRequests)
            assertEquals(1, activeOwners)
            assertTrue(bookings.entrantRequests.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
            assertEquals(setOf("site", "site-2"), content(model).campsites.map { it.id }.toSet())
        }

    /** The supplied classifier receives the original aggregate and each booked-local date. */
    @Test fun classifierUsesBookedZonesAndSuppliedActiveCategories() =
        listTest {
            val utc =
                base.copy(
                    id = "utc",
                    bookedCampsite = base.bookedCampsite.copy(timeZone = "UTC"),
                )
            val tokyo =
                base.copy(
                    id = "tokyo",
                    bookedCampsite = base.bookedCampsite.copy(timeZone = "Asia/Tokyo"),
                )
            val old = base.copy(id = "old")
            val cancelled = base.copy(id = "cancelled")
            bookings.records.value = listOf(utc, tokyo, old, cancelled)
            val seen = mutableMapOf<String, Pair<Reservation, LocalDate>>()
            val results =
                mapOf(
                    "utc" to ReservationCategory.UPCOMING,
                    "tokyo" to ReservationCategory.IN_PROGRESS,
                    "old" to ReservationCategory.PAST,
                    "cancelled" to ReservationCategory.CANCELLED,
                )
            val model =
                model(classify = { record, today ->
                    seen[record.id] = record to today
                    results.getValue(record.id)
                })
            runCurrent()
            assertEquals(listOf("utc", "tokyo"), ids(model))
            assertEquals(utc to LocalDate.parse("2027-05-16"), seen[utc.id])
            assertEquals(tokyo to LocalDate.parse("2027-05-17"), seen[tokyo.id])
            assertEquals(
                listOf(
                    ReservationCategory.UPCOMING,
                    ReservationCategory.IN_PROGRESS,
                ),
                content(model).items.map {
                    it.category
                },
            )
        }

    /** Missing classification never substitutes a production algorithm or claims active rows. */
    @Test fun missingClassificationHasNoFallbackAndMissingOrderingStaysExplicit() =
        listTest {
            bookings.records.value = listOf(base)
            val missing = model(classify = null)
            runCurrent()
            assertTrue(content(missing).classificationPending)
            assertTrue(ids(missing).isEmpty())
            val unordered = model(key = "unordered", order = null)
            runCurrent()
            assertEquals(listOf(base.id), ids(unordered))
            assertFalse(content(unordered).classificationPending)
            assertTrue(content(unordered).orderingPending)
        }

    /** Ordering receives only active selected-site records and its exact returned order is used. */
    @Test fun orderingRunsAfterClassificationAndTheOwnerFilter() =
        listTest {
            val b = base.copy(id = "b")
            val c = base.copy(id = "c", campsiteId = "other")
            val old = base.copy(id = "old")
            categories[old.id] = ReservationCategory.PAST
            bookings.records.value = listOf(base, b, c, old)
            val calls = mutableListOf<List<Reservation>>()
            val model =
                model(ReservationContext.OWNER, initial = "site", order = {
                    calls += it
                    it.reversed()
                })
            runCurrent()
            assertEquals(listOf(b.id, base.id), ids(model))
            assertEquals(listOf(base, b), calls.last())
            assertEquals(base, content(model).items.last().reservation)
        }

    /** Filter changes are local projections and clearing does not acquire another aggregate. */
    @Test fun ownerFilterCanChangeAndClearWithoutAnotherQuery() =
        listTest {
            bookings.records.value = listOf(base, base.copy(id = "other", campsiteId = "other"))
            val model = model(ReservationContext.OWNER)
            runCurrent()
            model.selectCampsite("other")
            assertEquals(listOf("other"), ids(model))
            model.selectCampsite(null)
            assertEquals(2, ids(model).size)
            assertEquals(listOf("owner"), ownerRequests)
            assertEquals(1, activeOwners)
        }

    /** Only a primitive filter is restored; an explicit restored All wins over initial entry. */
    @Test fun filterRestoresAndAnExplicitClearWinsOverTheInitialManagementEntry() =
        listTest {
            val saved = SavedStateHandle()
            val first = model(ReservationContext.OWNER, saved = saved, initial = "site")
            assertEquals("site", first.campsiteId.value)
            first.selectCampsite(null)
            val restored =
                model(
                    ReservationContext.OWNER,
                    key = "restored",
                    saved =
                        SavedStateHandle(
                            saved
                                .keys()
                                .associateWith {
                                    saved.get<Any?>(it)
                                },
                        ),
                    initial = "site",
                )
            assertNull(restored.campsiteId.value)
            restored.selectCampsite("other")
            assertEquals("other", restored.campsiteId.value)
            assertTrue(saved.keys().all { it == "reservationCampsite" })
        }

    /** Entrant context cannot inherit an owner's filter or start profile-label lookups. */
    @Test fun entrantIgnoresOwnerFilterAndDoesNotLoadProfiles() =
        listTest {
            bookings.records.value = listOf(base)
            val model =
                model(
                    saved = SavedStateHandle(mapOf("reservationCampsite" to "other")),
                    initial = "other",
                )
            runCurrent()
            model.selectCampsite("other")
            assertNull(model.campsiteId.value)
            assertEquals(listOf(base.id), ids(model))
            assertNull(content(model).items.single().name)
            assertTrue(accounts.readIds.isEmpty())
        }

    /** A filtered empty result retains saved filter choices, without consulting live listings. */
    @Test fun filteredEmptyKeepsChoicesFromScopedBookedSnapshots() =
        listTest {
            bookings.records.value = listOf(base)
            val model = model(ReservationContext.OWNER, initial = "missing")
            runCurrent()
            assertTrue(ids(model).isEmpty())
            assertEquals(
                listOf(ReservationCampsiteChoice(base.campsiteId, base.bookedCampsite.name)),
                content(model).campsites,
            )
            assertEquals("missing", model.campsiteId.value)
        }

    /** An aggregate failure cannot retain partial rows as confirmed; retry retains the filter. */
    @Test fun failureIsNotPartialSuccessAndDuplicateRetryKeepsOneSource() =
        listTest {
            bookings.records.value = listOf(base)
            val model = model(ReservationContext.OWNER, initial = "site")
            runCurrent()
            ownerFailure.value = IOException("one site query failed")
            runCurrent()
            assertEquals(ReservationListUiState.Failure, model.state.value)
            assertEquals("site", model.campsiteId.value)
            ownerFailure.value = null
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(listOf(base.id), ids(model))
            assertEquals(2, ownerRequests.size)
            assertEquals(1, activeOwners)
        }

    /** Foreground refresh cannot turn an unresolved source into empty or multiply its query. */
    @Test fun heldInitialSourceRemainsLoadingThroughForegroundRefresh() =
        listTest {
            val held = CompletableDeferred<List<Reservation>>()
            ownerHandler = { flow { emit(held.await()) } }
            val model = model(ReservationContext.OWNER)
            runCurrent()
            model.refreshTime()
            runCurrent()
            assertEquals(ReservationListUiState.Loading, model.state.value)
            assertEquals(1, ownerRequests.size)
            held.complete(listOf(base))
            runCurrent()
            assertEquals(listOf(base.id), ids(model))
        }

    /** The nearest booked midnight reevaluates classification without a fresh query or write. */
    @Test fun bookedLocalMidnightUpdatesActiveMembershipWithoutAQuery() =
        listTest {
            val utc = base.copy(bookedCampsite = base.bookedCampsite.copy(timeZone = "UTC"))
            bookings.records.value = listOf(utc)
            val model =
                model(classify = { _, today ->
                    if (today ==
                        LocalDate.parse("2027-05-16")
                    ) {
                        ReservationCategory.IN_PROGRESS
                    } else {
                        ReservationCategory.PAST
                    }
                })
            runCurrent()
            assertEquals(listOf(base.id), ids(model))
            clock.advance(Duration.ofSeconds(1))
            advanceTimeBy(1000)
            runCurrent()
            assertTrue(ids(model).isEmpty())
            assertEquals(1, bookings.entrantRequests.size)
            assertTrue(bookings.reserveRequests.isEmpty())
        }

    /** Foreground date refresh uses booked zones even when the device timezone differs. */
    @Test fun foregroundRefreshDoesNotUseTheDeviceZone() =
        listTest {
            val previous = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
                bookings.records.value = listOf(base)
                val seen = mutableListOf<LocalDate>()
                val model =
                    model(classify = {
                        _,
                        today,
                        ->
                        seen += today
                        ReservationCategory.UPCOMING
                    })
                runCurrent()
                clock.advance(Duration.ofDays(2))
                model.refreshTime()
                assertEquals(
                    clock.instant().atZone(ZoneId.of(base.bookedCampsite.timeZone)).toLocalDate(),
                    seen.lastOrNull(),
                )
                assertEquals(1, bookings.entrantRequests.size)
            } finally {
                TimeZone.setDefault(previous)
            }
        }

    /** Missing/removed/mismatched labels never hide rows; shared entrant identities read once. */
    @Test fun ownerNamesAreIndependentAndUnavailableProfilesKeepTheSavedStay() =
        listTest {
            val missing = base.copy(id = "missing", entrantId = "missing")
            val removed = base.copy(id = "removed", entrantId = "removed")
            val mismatch = base.copy(id = "mismatch", entrantId = "mismatch")
            bookings.records.value =
                listOf(base, base.copy(id = "same-person"), missing, removed, mismatch)
            accounts.getHandler = { id ->
                when (id) {
                    "entrant" -> {
                        CampingFixtures.account().copy(name = "Alex")
                    }

                    "removed" -> {
                        CampingFixtures
                            .account(
                                id,
                            ).copy(removal = Removal(CampingFixtures.now, "admin"))
                    }

                    "mismatch" -> {
                        CampingFixtures.account("different")
                    }

                    else -> {
                        null
                    }
                }
            }
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertEquals(5, ids(model).size)
            assertEquals(
                ReservationEntrantName.Available("Alex"),
                content(model).items.first().name,
            )
            assertTrue(
                content(model).items.drop(2).all { it.name == ReservationEntrantName.Unavailable },
            )
            assertEquals(
                setOf("entrant", "missing", "removed", "mismatch"),
                accounts.readIds.toSet(),
            )
            assertEquals(4, accounts.readIds.size)
        }

    /** Label retry is isolated from the aggregate and successful names; held reads stay Loading. */
    @Test fun failedAndHeldNamesKeepRowsAndRetryOnlyTheirOwnReads() =
        listTest {
            val held =
                CompletableDeferred<Account?>()
            var failed = true
            bookings.records.value = listOf(base, base.copy(id = "second", entrantId = "held"))
            accounts.getHandler = { id ->
                if (id == "held") {
                    held.await()
                } else if (failed) {
                    throw IOException("profile offline")
                } else {
                    CampingFixtures.account(id)
                }
            }
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertEquals(ReservationEntrantName.Failure, content(model).items.first().name)
            assertEquals(ReservationEntrantName.Loading, content(model).items.last().name)
            failed = false
            model.retryNames()
            model.retryNames()
            runCurrent()
            assertEquals(3, accounts.readIds.size)
            assertEquals(1, ownerRequests.size)
            held.complete(CampingFixtures.account("held"))
            runCurrent()
            assertTrue(content(model).items.all { it.name is ReservationEntrantName.Available })
        }

    /** Retiring a selected row rejects a noncooperative late profile read. */
    @Test fun changingFilterRetiresHeldLabelsAndIgnoresLateResults() =
        listTest {
            val held =
                CompletableDeferred<Account?>()
            accounts.getHandler = { withContext(NonCancellable) { held.await() } }
            bookings.records.value = listOf(base)
            val model = model(ReservationContext.OWNER)
            runCurrent()
            model.selectCampsite("other")
            held.complete(CampingFixtures.account())
            runCurrent()
            assertTrue(ids(model).isEmpty())
            assertEquals("other", model.campsiteId.value)
        }

    /** New aggregates replace saved facts and supplied category results immediately. */
    @Test fun liveChangesReprojectTheSameSavedRecordsAndRetireInactiveRows() =
        listTest {
            bookings.records.value = listOf(base)
            val model = model()
            runCurrent()
            val changed =
                base.copy(
                    bookedCampsite = base.bookedCampsite.copy(name = "Booked name remains"),
                )
            bookings.records.value = listOf(changed)
            runCurrent()
            assertEquals(changed, content(model).items.single().reservation)
            categories[base.id] = ReservationCategory.CANCELLED
            bookings.records.value =
                listOf(
                    changed.copy(
                        cancellation =
                            ReservationCancellation(
                                clock.instant(),
                                "entrant",
                                Role.ENTRANT,
                                null,
                            ),
                    ),
                )
            runCurrent()
            assertTrue(ids(model).isEmpty())
        }

    /** Clearing the graph releases the aggregate and rejects noncooperative late labels. */
    @Test fun graphClearReleasesItsSourcesAndRetiresLateResults() =
        listTest {
            val held =
                CompletableDeferred<Account?>()
            accounts.getHandler = { withContext(NonCancellable) { held.await() } }
            bookings.records.value = listOf(base)
            val model = model(ReservationContext.OWNER)
            runCurrent()
            val before = model.state.value
            try {
                assertEquals(listOf(base.id), ids(model))
                store.clear()
                runCurrent()
                assertEquals(0, activeOwners)
            } finally {
                held.complete(CampingFixtures.account())
            }
            runCurrent()
            assertEquals(before, model.state.value)
            clock.advance(Duration.ofDays(1))
            model.refreshTime()
            assertEquals(before, model.state.value)
        }

    /** Source cancellation is neither a confirmed empty list nor a displayed transport failure. */
    @Test fun cancelledSourceCanBeExplicitlyRetried() =
        listTest {
            ownerHandler = { flow { throw CancellationException("interrupted read") } }
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertEquals(ReservationListUiState.Loading, model.state.value)
            ownerHandler = null
            bookings.records.value = listOf(base)
            model.retry()
            runCurrent()
            assertEquals(listOf(base.id), ids(model))
            assertEquals(2, ownerRequests.size)
        }

    /** Creates one explicit owner with supplied fixed helper results, never real helper bodies.
     * @param context Actor workspace.
     * @param key Retained graph identity for this test.
     * @param saved Small restored filter.
     * @param initial Optional management-entry filter.
     * @param classify Supplied category results, or missing dependency.
     * @param order Supplied ordering result, or missing dependency.
     */
    private fun model(
        context: ReservationContext = ReservationContext.ENTRANT,
        key: String = "list",
        saved: SavedStateHandle = SavedStateHandle(),
        initial: String? = null,
        classify: ((Reservation, LocalDate) -> ReservationCategory)? = { record, _ ->
            categories[record.id]
                ?: ReservationCategory.UPCOMING
        },
        order: ((List<Reservation>) -> List<Reservation>)? = { it },
    ): ReservationListViewModel =
        ReservationListViewModel(
            if (context ==
                ReservationContext.OWNER
            ) {
                "owner"
            } else {
                "entrant"
            },
            context,
            repository,
            accounts,
            FakeImageRepository(),
            clock,
            saved,
            initial,
            classify,
            order,
        ).also { store.put(key, it) }

    /** Returns confirmed list content, so Loading/Failure cannot accidentally pass assertions.
     * @param model Actual graph owner.
     */
    private fun content(model: ReservationListViewModel): ReservationListUiState.Content {
        assertTrue(
            "Expected confirmed content: ${model.state.value}",
            model.state.value is ReservationListUiState.Content,
        )
        return model.state.value as ReservationListUiState.Content
    }

    /** Reads stable displayed identities without duplicating saved aggregate fields.
     * @param model Actual graph owner.
     */
    private fun ids(model: ReservationListViewModel): List<String> =
        content(model).items.map { it.reservation.id }

    /** Bounds all feature jobs to their owning store, including failed assertions.
     * @param block Fixed scenario using controllable scheduler/time/repositories.
     */
    private fun listTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
            }
        }
}

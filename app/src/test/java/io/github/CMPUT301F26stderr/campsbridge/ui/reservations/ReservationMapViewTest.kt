/*
 * Checks the reservation history List/Map choice owned by the list ViewModel.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** The map is an entrant view over the same list source; choosing it never reads or writes records. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationMapViewTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val bookings = FakeBookingRepository()

    /** An entrant's Map choice survives recreation and keeps the one existing source. */
    @Test fun entrantMapChoiceSurvivesRecreation() =
        runTest {
            bookings.records.value = listOf(CampingFixtures.reservation())
            val saved = SavedStateHandle()
            val first = model(ReservationContext.ENTRANT, saved, "first")
            assertTrue(first.canShowMap)
            assertEquals(ReservationListView.LIST, first.view.value)
            first.selectView(ReservationListView.MAP)
            runCurrent()
            assertEquals(ReservationListView.MAP, first.view.value)
            assertEquals(listOf("entrant"), bookings.entrantRequests)
            assertTrue(bookings.reserveRequests.isEmpty())
            val restored = model(ReservationContext.ENTRANT, saved, "restored")
            assertEquals(ReservationListView.MAP, restored.view.value)
            store.clear()
        }

    /** Owners keep the list; the reservation map is an entrant feature. */
    @Test fun ownerHistoryStaysOnTheList() =
        runTest {
            val owner = model(ReservationContext.OWNER, SavedStateHandle(), "owner")
            assertFalse(owner.canShowMap)
            owner.selectView(ReservationListView.MAP)
            assertEquals(ReservationListView.LIST, owner.view.value)
            store.clear()
        }

    /** Builds the actual list owner with fixed time and fake repositories.
     * @param context Workspace presentation.
     * @param saved Shared saved state used to simulate recreation.
     * @param key Store key for cleanup.
     */
    private fun model(
        context: ReservationContext,
        saved: SavedStateHandle,
        key: String,
    ) =
        ReservationListViewModel(
            if (context == ReservationContext.OWNER) "owner" else "entrant",
            context,
            bookings,
            FakeAccountRepository(),
            FakeImageRepository(),
            MutableClock(Instant.parse("2027-05-16T23:59:59Z")),
            saved,
            classify = { _, _ -> ReservationCategory.UPCOMING },
            order = { it },
        ).also { store.put(key, it) }
}

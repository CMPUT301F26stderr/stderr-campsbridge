package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset

/** Checks the list's use of shared photo readers without repeating image-slot contracts. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReservationListPhotosTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val bookings = FakeBookingRepository()
    private val images = FakeImageRepository()
    private val record = CampingFixtures.reservation()
    private val target = ImageTarget(ImageTargetType.CAMPSITE, record.campsiteId)

    /** Only visible permitted rows acquire photos; shared targets and hidden readers are bounded. */
    @Test fun visiblePhotosFollowDisplayedRowsAndReleaseWithTheList() =
        photoTest {
            val second = record.copy(id = "second")
            val other = record.copy(id = "other", campsiteId = "other-site")
            bookings.records.value = listOf(record, second, other)
            val model = model(ReservationContext.OWNER)
            runCurrent()
            assertTrue(images.observedTargets.isEmpty())
            model.showVisibleReservations(setOf(record.id, second.id, "not-a-row"))
            runCurrent()
            assertEquals(listOf(target), images.observedTargets)
            assertEquals(1, images.activeObservers)
            model.selectCampsite(other.campsiteId)
            runCurrent()
            assertEquals(0, images.activeObservers)
            assertTrue(model.photos.value.isEmpty())
            model.showVisibleReservations(setOf(record.id, other.id))
            runCurrent()
            assertEquals(
                ImageTarget(ImageTargetType.CAMPSITE, other.campsiteId),
                images.observedTargets.last(),
            )
            assertEquals(1, images.activeObservers)
            store.clear()
            runCurrent()
            assertEquals(0, images.activeObservers)
            assertTrue(model.photos.value.isEmpty())
        }

    /** A failed photo retries independently while saved rows and the booking source remain intact. */
    @Test fun failedPhotoRetryKeepsSavedRowsAndTheSameReservationSource() =
        photoTest {
            bookings.records.value = listOf(record)
            images.observationFailure = IOException("photo offline")
            val model = model(ReservationContext.ENTRANT)
            runCurrent()
            model.showVisibleReservations(setOf(record.id))
            runCurrent()
            assertEquals(
                ImageLoadState.FAILED,
                model.photos.value
                    .getValue(target)
                    .loadState,
            )
            assertEquals(
                record,
                (model.state.value as ReservationListUiState.Content).items.single().reservation,
            )
            images.observationFailure = null
            model.retryPhoto(record.campsiteId)
            model.retryPhoto(record.campsiteId)
            runCurrent()
            assertEquals(
                ImageLoadState.READY,
                model.photos.value
                    .getValue(target)
                    .loadState,
            )
            assertEquals(listOf(target, target), images.observedTargets)
            assertEquals(listOf(record.entrantId), bookings.entrantRequests)
            assertEquals(1, bookings.activeEntrantObservers)
            assertEquals(
                record,
                (model.state.value as ReservationListUiState.Content).items.single().reservation,
            )
            assertTrue(bookings.reserveRequests.isEmpty())
            assertTrue(bookings.cancellationRequests.isEmpty())
            assertEquals(0, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
        }

    /** Supplies fixed category/order outcomes so these tests own only photo integration.
     * @param context Workspace whose rows are displayed.
     */
    private fun model(context: ReservationContext): ReservationListViewModel =
        ReservationListViewModel(
            if (context == ReservationContext.OWNER) "owner" else record.entrantId,
            context,
            bookings,
            FakeAccountRepository(),
            images,
            Clock.fixed(CampingFixtures.now, ZoneOffset.UTC),
            classify = { _, _ -> ReservationCategory.UPCOMING },
            order = { it },
        ).also { store.put("list", it) }

    /** Releases subscriptions and date waits even after a failed assertion.
     * @param block Controlled list/photo interaction.
     */
    private fun photoTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
            }
        }
}

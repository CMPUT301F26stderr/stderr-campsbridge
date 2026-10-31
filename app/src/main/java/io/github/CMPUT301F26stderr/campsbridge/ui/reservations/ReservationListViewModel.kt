package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

/** Declares the scoped list contract before implementing its reads.
 * @param accountId Current actor.
 * @param context Current workspace.
 * @param bookings Saved stay source.
 * @param accounts Supplemental names.
 * @param images Visible photos.
 * @param clock Booked-local date source.
 * @param savedState Retained campsite selection.
 * @param initialCampsiteId Management entry filter.
 * @param classify Supplied date rule.
 * @param order Supplied arrival ordering.
 */
@Suppress("UNUSED_PARAMETER")
class ReservationListViewModel(
    accountId: String,
    val context: ReservationContext,
    bookings: BookingRepository,
    accounts: AccountRepository,
    images: ImageRepository,
    clock: Clock,
    savedState: SavedStateHandle = SavedStateHandle(),
    initialCampsiteId: String? = null,
    classify: ((Reservation, LocalDate) -> ReservationCategory)? = null,
    order: ((List<Reservation>) -> List<Reservation>)? = null,
) : ViewModel() {
    val state =
        MutableStateFlow<ReservationListUiState>(
            ReservationListUiState.Loading,
        ).asStateFlow()
    val campsiteId = MutableStateFlow<String?>(null).asStateFlow()
    val photos = MutableStateFlow<Map<ImageTarget, ImageSlotState>>(emptyMap()).asStateFlow()

    /** Retries a failed source. */
    fun retry() =
        Unit

    /** Retries failed owner labels. */
    fun retryNames() =
        Unit

    /** Refreshes booked-local dates. */
    fun refreshTime() =
        Unit

    /** Selects the owner filter.
     * @param id Campsite or all.
     */
    fun selectCampsite(id: String?) =
        Unit

    /** Reports visible saved identities.
     * @param ids Displayed rows.
     */
    fun showVisibleReservations(ids: Set<String>) =
        Unit

    /** Retries a current photo.
     * @param campsiteId Image target.
     */
    fun retryPhoto(campsiteId: String) =
        Unit
}

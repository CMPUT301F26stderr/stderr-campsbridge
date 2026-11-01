package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth

/** Independent inventory outcomes; null means that range has not been requested.
 * @param visible Displayed-month outcome, or null while hidden.
 * @param selected Selected-stay or checkout-support outcome, or null before selection.
 */
data class BookingCalendarReadState(
    val visible: CalendarInventoryRead? = null,
    val selected: CalendarInventoryRead? = null,
)

/** BK2 reader contract; inventory behavior is pending the prepared tests.
 * @param campsiteId Fixed saved campsite identity.
 * @param campsites Half-open inventory source.
 * @param scope Containing feature's coroutine lifetime.
 */
@Suppress("UNUSED_PARAMETER")
internal class BookingCalendarReader(
    campsiteId: String,
    campsites: CampsiteRepository,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(BookingCalendarReadState())
    val state = mutableState.asStateFlow()

    /** Declares requested scopes before inventory behavior is implemented.
     * @param visibleMonth Displayed month, or null while hidden.
     * @param checkIn Inclusive arrival, or null before selection.
     * @param checkOut Exclusive departure, or null before selection is complete.
     */
    fun update(
        visibleMonth: YearMonth?,
        checkIn: LocalDate? = null,
        checkOut: LocalDate? = null,
    ) =
        Unit

    /** Declares the visible-source retry action. */
    fun retryVisible() =
        Unit

    /** Declares the selected-source retry action. */
    fun retrySelection() =
        Unit

    /** Declares temporary release of requested ranges. */
    fun stop() =
        Unit

    /** Declares permanent retirement of the reader. */
    fun close() =
        Unit
}

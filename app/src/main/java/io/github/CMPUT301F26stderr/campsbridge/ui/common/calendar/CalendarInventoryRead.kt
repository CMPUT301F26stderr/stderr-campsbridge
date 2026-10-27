package io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight

/** Confirmed calendar query outcome shared by setup, allocation, saved readers and booking. */
sealed interface CalendarInventoryRead {
    /** The requested range has not loaded, so absence is not known inventory. */
    data object Loading : CalendarInventoryRead

    /** The range failed and can be retried without discarding unrelated input or sources. */
    data object Failure : CalendarInventoryRead

    /** Successfully read range; missing dates are known unoffered nights.
     * @param nights Confirmed records in the caller's requested half-open range.
     */
    data class Content(
        val nights: List<CalendarNight>,
    ) : CalendarInventoryRead
}

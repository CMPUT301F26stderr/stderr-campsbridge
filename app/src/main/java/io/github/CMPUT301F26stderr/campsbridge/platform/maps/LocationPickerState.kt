package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates

/** Local working pin; applying or abandoning it belongs to the calling route.
 * @param initial Initial saved/draft location, or no selected pin.
 */
@Stable
class LocationPickerState(
    initial: Coordinates?,
) {
    var selected: Coordinates? by mutableStateOf(initial)
        private set

    /** Changes only this picker's working value.
     * @param location Finite, geographically valid coordinates.
     */
    fun select(location: Coordinates) {
        selected = location
    }

    /** Saves only coordinate primitives, never an SDK marker or parent draft. */
    companion object {
        val Saver: Saver<LocationPickerState, List<Double>> =
            Saver(
                save = { state ->
                    state.selected?.let { listOf(it.latitude, it.longitude) } ?: emptyList()
                },
                restore = { values ->
                    LocationPickerState(
                        values.takeIf { it.size == 2 }?.let { Coordinates(it[0], it[1]) },
                    )
                },
            )
    }
}

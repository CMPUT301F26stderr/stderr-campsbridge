package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.compose.runtime.saveable.SaverScope
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Working-pin and primitive restoration contracts, without Maps SDK or persistence. */
class LocationPickerStateTest {
    /** Opening a new picker does not select its default camera location. */
    @Test fun newPickerHasNoSelection() {
        assertNull(LocationPickerState(null).selected)
    }

    /** Selecting and replacing the working pin retains the latest chosen coordinates. */
    @Test fun workingSelectionUsesTheLatestPin() {
        val original = Coordinates(53.0, -118.0)
        val state = LocationPickerState(original)
        val selected = Coordinates(51.2, -115.6)
        state.select(selected)
        assertEquals(selected, state.selected)
        state.select(Coordinates(-90.0, 180.0))
        assertEquals(Coordinates(-90.0, 180.0), state.selected)
    }

    /** Recreation retains the latest working pin rather than the original parent value. */
    @Test fun saverRestoresSelectedPrimitives() {
        val state = LocationPickerState(Coordinates(53.0, -118.0))
        state.select(Coordinates(51.2, -115.6))
        val encoded = with(LocationPickerState.Saver) { SaverScope { true }.save(state) }
        assertEquals(listOf(51.2, -115.6), encoded)
        assertEquals(
            Coordinates(51.2, -115.6),
            LocationPickerState.Saver.restore(encoded!!)!!.selected,
        )
    }

    /** An empty primitive representation restores an unselected picker. */
    @Test fun saverRestoresAbsentPin() {
        val encoded =
            with(LocationPickerState.Saver) {
                SaverScope { true }.save(LocationPickerState(null))
            }
        assertEquals(emptyList<Double>(), encoded)
        assertNull(LocationPickerState.Saver.restore(encoded!!)!!.selected)
    }
}

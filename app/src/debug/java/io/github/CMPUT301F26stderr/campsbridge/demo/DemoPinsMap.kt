/*
 * Lists saved map pins offline so the demo and UI tests can choose one without the Maps SDK.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.MapPin
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Offers each pin as a single-choice row with its saved coordinates.
 * @param pins Markers the real map would show.
 * @param selectedId Currently chosen pin.
 * @param onSelect Reports the chosen pin.
 * @param modifier Parent map area.
 */
@Composable
fun DemoPinsMap(
    pins: List<MapPin>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.demo_offline_location))
        for (pin in pins) {
            ChoiceRow(
                pin.title,
                pin.id == selectedId,
                { onSelect(pin.id) },
                supportingText =
                    stringResource(
                        R.string.campsite_coordinates,
                        pin.location.latitude,
                        pin.location.longitude,
                    ),
            )
        }
    }
}

/** Shows two offline pins with the second chosen, under enlarged text. */
@Preview(fontScale = 1.6f)
@Composable
private fun DemoPinsMapPreview() {
    CampsbridgeTheme {
        DemoPinsMap(
            listOf(
                MapPin("river", "River Camp", Coordinates(52.8734, -118.0814)),
                MapPin("lake", "Lake Louise Walk-in", Coordinates(51.4254, -116.1773)),
            ),
            "lake",
            {},
            Modifier,
        )
    }
}

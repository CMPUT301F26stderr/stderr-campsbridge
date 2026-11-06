package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Uses explicit saved coordinates offline; selection edits only the picker's working pin.
 * @param location Saved or working location.
 * @param onSelect Optional picker callback; absent for read-only views.
 * @param modifier Parent map area.
 */
@Composable
fun DemoLocationMap(
    location: Coordinates?,
    onSelect: ((Coordinates) -> Unit)?,
    modifier: Modifier,
) {
    var latitude by rememberSaveable { mutableStateOf(location?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf(location?.longitude?.toString().orEmpty()) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.demo_offline_location))
        location?.let { Text(stringResource(R.string.campsite_coordinates, it.latitude, it.longitude)) }
        if (onSelect != null) {
            Text(stringResource(R.string.demo_offline_location_hint))
            Row {
                TextButton({ onSelect(Coordinates(52.8734, -118.0814)) }) { Text(stringResource(R.string.demo_jasper)) }
                TextButton({ onSelect(Coordinates(51.1784, -115.5708)) }) { Text(stringResource(R.string.demo_banff)) }
            }
            AppTextField(latitude, { latitude = it }, stringResource(R.string.demo_latitude))
            AppTextField(longitude, { longitude = it }, stringResource(R.string.demo_longitude))
            val pin = runCatching { Coordinates(latitude.toDouble(), longitude.toDouble()) }.getOrNull()
            TextButton(
                { pin?.let(onSelect) },
                enabled = pin != null,
            ) { Text(stringResource(R.string.demo_choose_coordinates)) }
        }
    }
}

/** Previews explicit local selection without Maps initialization. */
@Preview
@Composable
private fun DemoLocationPickerPreview() {
    CampsbridgeTheme { DemoLocationMap(null, {}, Modifier) }
}

/** Keeps the saved coordinate readable under enlarged system text. */
@Preview(fontScale = 1.6f)
@Composable
private fun DemoSavedLocationPreview() {
    CampsbridgeTheme { DemoLocationMap(Coordinates(52.8734, -118.0814), null, Modifier) }
}

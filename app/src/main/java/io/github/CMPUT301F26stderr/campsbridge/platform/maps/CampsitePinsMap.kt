/*
 * Read-only map of several saved campsite locations; SDK types stay in this platform adapter.
 */

package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import io.github.CMPUT301F26stderr.campsbridge.BuildConfig
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates

/** One selectable map marker, described with application types only.
 * @param id Stable caller identity reported when the pin is chosen.
 * @param title Marker label.
 * @param location Saved coordinate.
 */
data class MapPin(
    val id: String,
    val title: String,
    val location: Coordinates,
)

/** Host-owned offline multi-pin surface; ordinary hosts use the configured Maps SDK. */
val LocalDemoPinsMap =
    staticCompositionLocalOf<
        (
            @Composable (
                List<MapPin>,
                String?,
                (String) -> Unit,
                Modifier,
            ) -> Unit
        )?,
    > { null }

/** Shows every pin, fits the camera around them and reports the chosen pin.
 * @param pins Markers to display.
 * @param selectedId Currently chosen pin, used by offline hosts to mark the choice.
 * @param onSelect Reports a tapped pin's identity.
 * @param description Accessibility description of the whole map.
 * @param missingKeyText Text shown when this build has no Maps key.
 * @param modifier Caller layout.
 * @param configured Whether this build supplied a Maps key; controllable for unavailable UI tests.
 */
@Composable
fun CampsitePinsMap(
    pins: List<MapPin>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    description: String,
    missingKeyText: String,
    modifier: Modifier = Modifier,
    configured: Boolean = BuildConfig.MAPS_CONFIGURED,
) {
    val local = LocalDemoPinsMap.current
    if (local != null) {
        local(pins, selectedId, onSelect, modifier)
        return
    }
    if (LocalInspectionMode.current) {
        MapPreview(modifier)
        return
    }
    if (!mapAvailable(configured, missingKeyText, modifier)) return
    val select by rememberUpdatedState(onSelect)
    val camera =
        rememberCameraPositionState {
            position =
                CameraPosition.fromLatLngZoom(
                    pins.firstOrNull()?.location?.toLatLng() ?: LatLng(53.5, -116.5),
                    if (pins.isEmpty()) 5f else 10f,
                )
        }
    var loaded by remember { mutableStateOf(false) }
    // Bounds need a laid-out map, so fitting waits until the SDK reports it has loaded.
    LaunchedEffect(loaded, pins) {
        if (loaded && pins.size > 1) {
            val bounds = LatLngBounds.builder()
            pins.forEach { bounds.include(it.location.toLatLng()) }
            camera.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
        }
    }
    Column(modifier) {
        if (!loaded) Text(stringResource(R.string.campsite_map_loading))
        GoogleMap(
            Modifier.weight(1f).fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = description,
            mapColorScheme = ComposeMapColorScheme.LIGHT,
            uiSettings =
                MapUiSettings(
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    zoomControlsEnabled = true,
                ),
            onMapLoaded = { loaded = true },
        ) {
            for (pin in pins) {
                key(pin.id) {
                    Marker(
                        state = rememberUpdatedMarkerState(pin.location.toLatLng()),
                        title = pin.title,
                        onClick = {
                            select(pin.id)
                            false
                        },
                    )
                }
            }
        }
    }
}

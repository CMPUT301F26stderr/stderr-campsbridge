package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import io.github.CMPUT301F26stderr.campsbridge.BuildConfig
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Read-only stored-location map; SDK types and initialization stay in this platform adapter.
 * @param location Confirmed application coordinate to display.
 * @param modifier Caller layout.
 */
@Composable
fun CampsiteMap(
    location: Coordinates,
    modifier: Modifier = Modifier,
) {
    CampsiteMapSurface(location, null, modifier)
}

/** Shares SDK configuration between read-only display and an explicit working-pin picker.
 * @param location Stored/working pin; null never creates an implicit selected coordinate.
 * @param onSelect Optional picker callback; absent means read-only marker behavior.
 * @param modifier Caller layout.
 * @param configured Whether this build supplied a Maps key; controllable for unavailable UI tests.
 */
@Composable
internal fun CampsiteMapSurface(
    location: Coordinates?,
    onSelect: ((Coordinates) -> Unit)?,
    modifier: Modifier = Modifier,
    configured: Boolean = BuildConfig.MAPS_CONFIGURED,
) {
    if (LocalInspectionMode.current) {
        MapPreview(modifier)
        return
    }
    if (!configured) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.campsite_map_configuration_missing))
        }
        return
    }
    val context = LocalContext.current
    val servicesAvailable =
        remember(context) {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                ConnectionResult.SUCCESS
        }
    if (!servicesAvailable) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.campsite_map_services_unavailable))
        }
        return
    }
    val camera =
        rememberCameraPositionState {
            position =
                CameraPosition.fromLatLngZoom(
                    location?.toLatLng() ?: LatLng(53.5, -116.5),
                    if (location == null) 5f else 12f,
                )
        }
    var loaded by remember { mutableStateOf(false) }
    Column(modifier) {
        if (!loaded) Text(stringResource(R.string.campsite_map_loading))
        GoogleMap(
            Modifier.weight(1f).fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = stringResource(R.string.campsite_map_description),
            mapColorScheme = ComposeMapColorScheme.LIGHT,
            uiSettings =
                MapUiSettings(
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    zoomControlsEnabled = false,
                ),
            onMapLoaded = { loaded = true },
            onMapClick = onSelect?.let { select -> { point -> select(point.toCoordinates()) } },
        ) {
            if (location != null) {
                if (onSelect == null) {
                    Marker(
                        state = rememberUpdatedMarkerState(location.toLatLng()),
                        title = stringResource(R.string.campsite_map_pin),
                    )
                } else {
                    WorkingPin(location, onSelect)
                }
            }
        }
    }
}

/** Reconciles external taps and completed SDK marker drags with the same working value.
 * @param location Working application coordinate.
 * @param onSelect Changes only the picker working state after a completed drag.
 */
@Composable
@GoogleMapComposable
private fun WorkingPin(
    location: Coordinates,
    onSelect: (Coordinates) -> Unit,
) {
    val marker = remember { MarkerState(position = location.toLatLng()) }
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(location) {
        if (!marker.isDragging) marker.position = location.toLatLng()
    }
    LaunchedEffect(marker) {
        snapshotFlow { marker.isDragging }
            .drop(1)
            .filter { !it }
            .collect { select(marker.position.toCoordinates()) }
    }
    Marker(
        state = marker,
        draggable = true,
        title = stringResource(R.string.campsite_map_pin),
    )
}

/** Neutral tooling surface; never substitutes a successful runtime map.
 * @param modifier Preview layout.
 */
@Composable
internal fun MapPreview(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.campsite_map_preview))
    }
}

/** Converts an already validated application coordinate only at the SDK boundary. */
private fun Coordinates.toLatLng(): LatLng =
    LatLng(latitude, longitude)

/** Converts SDK tap/drag coordinates through the application's existing validity checks. */
private fun LatLng.toCoordinates(): Coordinates =
    Coordinates(latitude, longitude)

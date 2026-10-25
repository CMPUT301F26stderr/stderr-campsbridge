package io.github.CMPUT301F26stderr.campsbridge.platform.maps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Owns only a saveable working pin; the calling setup graph owns Apply/Cancel effects.
 * @param initialLocation Parent value used only when this child is first opened.
 * @param onApply Applies a valid pin to the actual parent and returns.
 * @param onCancel Returns without modifying that parent.
 * @param mapContent SDK surface, replaceable for deterministic interactions and passive previews.
 */
@Composable
fun LocationPicker(
    initialLocation: Coordinates?,
    onApply: (Coordinates) -> Unit,
    onCancel: () -> Unit,
    mapContent: @Composable (
        Coordinates?,
        (Coordinates) -> Unit,
        Modifier,
    ) -> Unit = { pin, select, modifier ->
        CampsiteMapSurface(pin, select, modifier)
    },
) {
    val state =
        rememberSaveable(saver = LocationPickerState.Saver) {
            LocationPickerState(initialLocation)
        }
    BackHandler(onBack = onCancel)
    LocationPickerScreen(state.selected, state::select, onApply, onCancel, mapContent)
}

/** Previewable picker content with one shared frame and explicit footer actions.
 * @param selected Current working pin, or no selection.
 * @param onSelect Changes the working pin only.
 * @param onApply Commits the current valid pin to the caller.
 * @param onCancel Abandons the working selection.
 * @param mapContent Specialist map surface supplied by the platform host.
 */
@Composable
internal fun LocationPickerScreen(
    selected: Coordinates?,
    onSelect: (Coordinates) -> Unit,
    onApply: (Coordinates) -> Unit,
    onCancel: () -> Unit,
    mapContent: @Composable (Coordinates?, (Coordinates) -> Unit, Modifier) -> Unit,
) {
    AppScreen(
        stringResource(R.string.location_picker_title),
        onBack = onCancel,
        bottomContent = {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.small)) {
                AppActionButton(
                    stringResource(R.string.location_picker_apply),
                    { selected?.let(onApply) },
                    Modifier.fillMaxWidth(),
                    enabled = selected != null,
                )
                AppActionButton(
                    stringResource(R.string.cancel),
                    onCancel,
                    Modifier.fillMaxWidth(),
                    style = ActionStyle.TEXT,
                )
            }
        },
    ) {
        Text(stringResource(R.string.location_picker_hint))
        Text(
            if (selected == null) {
                stringResource(R.string.location_picker_no_pin)
            } else {
                stringResource(R.string.campsite_coordinates, selected.latitude, selected.longitude)
            },
        )
        mapContent(selected, onSelect, Modifier.weight(1f).fillMaxSize())
    }
}

/** Shows ordinary picker chrome without starting an SDK in Android Studio. */
@Preview
@Composable
private fun LocationPickerPreview() {
    CampsbridgeTheme {
        LocationPickerScreen(Coordinates(53.0, -118.0), {}, {}, {}, { _, _, modifier ->
            MapPreview(modifier)
        })
    }
}

/** Shows empty selection and reachable actions with enlarged system text. */
@Preview(fontScale = 1.6f)
@Composable
private fun EmptyLocationPickerPreview() {
    CampsbridgeTheme {
        LocationPickerScreen(null, {}, {}, {}, { _, _, modifier -> MapPreview(modifier) })
    }
}

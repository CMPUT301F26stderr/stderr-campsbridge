package io.github.CMPUT301F26stderr.campsbridge.ui.components.actions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/** Adds standard hover and long-press help without replacing the action's accessible label.
 * @param label Short explanation of the icon action.
 * @param content Control that retains its own click action, label and enabled state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTooltip(
    label: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        hasAction = false,
        content = content,
    )
}

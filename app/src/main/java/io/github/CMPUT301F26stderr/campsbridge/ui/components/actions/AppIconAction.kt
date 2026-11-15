package io.github.CMPUT301F26stderr.campsbridge.ui.components.actions

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Gives a secondary icon action a tonal container, press feedback and a full touch target.
 * @param label Localized action description and tooltip.
 * @param onClick Caller-owned action.
 * @param modifier Caller layout and test semantics.
 * @param enabled Whether the action is permitted.
 * @param icon Decorative icon; the button supplies its description.
 */
@Composable
fun AppIconAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val target = 48.dp * LocalControlScale.current.coerceAtLeast(1f)
    ActionTooltip(label) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.sizeIn(minWidth = target, minHeight = target).semantics { contentDescription = label },
            enabled = enabled,
            content = icon,
        )
    }
}

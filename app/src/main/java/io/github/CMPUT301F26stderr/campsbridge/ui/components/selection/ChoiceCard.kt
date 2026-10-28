package io.github.CMPUT301F26stderr.campsbridge.ui.components.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** One prominent, mutually exclusive choice with a single accessible selection target.
 * @param label Localized choice name.
 * @param selected Whether this option is currently chosen.
 * @param onClick Reports an explicit selection.
 * @param icon Standard icon that helps identify the option.
 * @param modifier Caller layout.
 * @param enabled Whether the choice can be changed.
 * @param supportingText Optional factual context for this choice.
 */
@Composable
fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .selectable(
                    selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) colors.primary else colors.outlineVariant,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (enabled) colors.primary else contentColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(label, color = contentColor, style = MaterialTheme.typography.titleMedium)
                if (supportingText != null) {
                    Text(
                        supportingText,
                        color = if (enabled) colors.onSurfaceVariant else contentColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            RadioButton(selected, onClick = null, enabled = enabled)
        }
    }
}

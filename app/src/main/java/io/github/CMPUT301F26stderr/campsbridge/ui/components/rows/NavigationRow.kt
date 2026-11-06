/*
 * A consistent full-width navigation target for menus and detail sections.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Groups related destinations in one shared Material surface.
 * @param modifier Caller layout.
 * @param content Navigation rows for this section.
 */
@Composable
fun NavigationGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = Spacing.medium), content = content)
    }
}

/**
 * A consistent full-width navigation target for menus and detail sections.
 * @param title Visible destination label.
 * @param onClick Caller-owned navigation callback.
 * @param modifier Caller layout.
 * @param supportingText Optional supporting detail.
 * @param icon Optional supplementary icon.
 * @param enabled Whether the destination is available.
 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    Column(
        modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp * LocalControlScale.current.coerceAtLeast(1f))
                .padding(vertical = Spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = colors.surfaceContainerLow,
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            it,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (enabled) colors.primary else contentColor,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                supportingText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) colors.onSurfaceVariant else contentColor,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = if (enabled) colors.onSurfaceVariant else contentColor,
            )
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    }
}

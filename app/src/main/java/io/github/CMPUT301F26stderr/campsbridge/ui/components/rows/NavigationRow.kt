/*
 * A consistent full-width navigation target for menus and detail sections.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Groups related destinations in one shared Material surface.
 * @param modifier Caller layout.
 * @param content Navigation rows for this section.
 */
@Composable
fun NavigationGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(content = content)
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
        modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(title, color = contentColor) },
            supportingContent = supportingText?.let { supporting -> { Text(supporting) } },
            leadingContent =
                icon?.let { image ->
                    { Icon(image, null, tint = if (enabled) colors.primary else contentColor) }
                },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = if (enabled) colors.onSurfaceVariant else contentColor,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp * LocalControlScale.current.coerceAtLeast(1f))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            colors =
                ListItemDefaults.colors(
                    containerColor = colors.surface,
                    supportingColor = if (enabled) colors.onSurfaceVariant else contentColor,
                ),
        )
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    }
}

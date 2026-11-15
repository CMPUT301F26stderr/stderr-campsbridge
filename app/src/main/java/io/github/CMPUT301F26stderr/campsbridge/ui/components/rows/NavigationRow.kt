/*
 * A consistent full-width navigation target for menus and detail sections.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

internal val LocalNavigationShapes = compositionLocalOf<ListItemShapes?> { null }

/** Groups related destinations with shared spacing and position-aware corners.
 * @param items One navigation row per item, in display order.
 * @param modifier Caller layout.
 */
@Composable
fun NavigationGroup(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, item ->
            CompositionLocalProvider(
                LocalNavigationShapes provides ListItemDefaults.segmentedShapes(index, items.size),
            ) {
                item()
            }
        }
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
 * @param destructive Marks an entry that leads to a destructive confirmation.
 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val emphasis = if (destructive) colors.error else colors.onSurface
    val contentColor = if (enabled) emphasis else colors.onSurface.copy(alpha = 0.38f)
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = LocalNavigationShapes.current ?: ListItemDefaults.shapes(shape = MaterialTheme.shapes.medium),
        content = { Text(title, color = contentColor) },
        supportingContent = supportingText?.let { supporting -> { Text(supporting) } },
        leadingContent =
            icon?.let { image ->
                { Icon(image, null, tint = if (enabled && !destructive) colors.primary else contentColor) }
            },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = if (enabled) colors.onSurfaceVariant else contentColor,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp * LocalControlScale.current.coerceAtLeast(1f))
                .semantics { role = Role.Button },
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = colors.surfaceContainerLow,
                supportingContentColor = if (enabled) colors.onSurfaceVariant else contentColor,
            ),
    )
}

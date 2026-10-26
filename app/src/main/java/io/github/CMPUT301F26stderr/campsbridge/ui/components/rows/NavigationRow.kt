package io.github.CMPUT301F26stderr.campsbridge.ui.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier =
            modifier
                .heightIn(min = 56.dp * LocalControlScale.current.coerceAtLeast(1f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Presentation for a registered destination; the graph owns availability and navigation.
 * @param id Stable destination identity.
 * @param label Localized label.
 * @param icon Material icon.
 * @param badge Optional already-formatted badge content.
 */
data class WorkspaceNavigationItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
)

/** White Material workspace navigation with wrapping labels and caller-owned selection.
 * @param items Destinations supplied by the owning graph.
 * @param selectedId Current selected identity.
 * @param onSelect Navigation request.
 * @param modifier Caller layout.
 */
@Composable
fun WorkspaceNavigation(
    items: List<WorkspaceNavigationItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.map { it.id }.distinct().size == items.size)
    require(items.any { it.id == selectedId })
    NavigationBar(
        modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.id == selectedId,
                onClick = { onSelect(item.id) },
                icon = {
                    BadgedBox(badge = { item.badge?.let { Badge { Text(it) } } }) {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        item.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

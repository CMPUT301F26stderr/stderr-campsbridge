/*
 * Presentation for a registered destination; the graph owns availability and navigation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Presentation for a registered destination; the graph owns availability and navigation.
 * @param id Stable destination identity.
 * @param label Localized label.
 * @param icon Material icon.
 * @param badge Optional already-formatted badge content.
 * @param unselectedIcon Outline variant shown outside the current destination.
 */
data class WorkspaceNavigationItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val unselectedIcon: ImageVector = icon,
)

/** Material workspace navigation with compact labels and caller-owned selection.
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
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val widestLabel =
        items.maxOf { item ->
            with(density) {
                measurer
                    .measure(
                        AnnotatedString(item.label),
                        style =
                            labelStyle.copy(
                                fontSize =
                                    labelStyle.fontSize * 0.9f,
                            ),
                        softWrap = false,
                    ).size.width
                    .toDp()
            }
        }
    val target = 48.dp * LocalControlScale.current.coerceAtLeast(1f)
    val requiredWidth = maxOf(target, widestLabel) * items.size + Spacing.small * (items.size - 1)
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        val floating = requiredWidth + Spacing.medium <= maxWidth
        Surface(
            modifier =
                Modifier.fillMaxWidth().padding(
                    start = if (floating) Spacing.small else 0.dp,
                    end = if (floating) Spacing.small else 0.dp,
                    bottom = if (floating) Spacing.small else 0.dp,
                ),
            shape = if (floating) MaterialTheme.shapes.extraLarge else RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = if (floating) 2.dp else 0.dp,
        ) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                items.forEach { item ->
                    key(item.id) {
                        NavigationBarItem(
                            selected = item.id == selectedId,
                            onClick = { onSelect(item.id) },
                            icon = {
                                BadgedBox(badge = { item.badge?.let { Badge { Text(it) } } }) {
                                    Icon(
                                        if (item.id ==
                                            selectedId
                                        ) {
                                            item.icon
                                        } else {
                                            item.unselectedIcon
                                        },
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    item.label,
                                    maxLines = 1,
                                    autoSize =
                                        TextAutoSize.StepBased(
                                            minFontSize = MaterialTheme.typography.labelMedium.fontSize * 0.9f,
                                            maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        ),
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

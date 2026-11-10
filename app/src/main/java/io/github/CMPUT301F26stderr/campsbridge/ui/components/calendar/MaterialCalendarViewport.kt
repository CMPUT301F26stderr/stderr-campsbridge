package io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Gives Google's fixed-size calendar room on narrow screens and scales its controls together.
 * Material's month rows assume 48 dp targets, so changing their minimum independently overlaps rows.
 * @param content Stock date or range picker; its text retains the caller's font scale.
 */
@Composable
internal fun MaterialCalendarViewport(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = LocalControlScale.current.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(360.dp, maxWidth / scale)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * scale, density.fontScale / scale),
                LocalMinimumInteractiveComponentSize provides 48.dp,
            ) {
                Box(Modifier.width(width)) { content() }
            }
        }
    }
}

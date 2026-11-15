package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Prevents a retiring form step from competing with the currently displayed step for Back. */
internal val LocalScreenInteractionEnabled = compositionLocalOf { true }

/** Gives every screen bounded drag feedback before invoking its existing Back action.
 * @param onBack Completed Back action; null leaves navigation to the parent or Android.
 * @param backEnabled Whether the current operation allows leaving.
 */
@Composable
internal fun screenBackGesture(
    onBack: (() -> Unit)?,
    backEnabled: Boolean,
): Modifier {
    val active by rememberUpdatedState(LocalScreenInteractionEnabled.current)
    val enabled by rememberUpdatedState(backEnabled)
    val action by rememberUpdatedState(onBack)
    val displacement = remember { Animatable(0f) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val distance = with(LocalDensity.current) { 24.dp.toPx() }
    PredictiveBackHandler(enabled = onBack != null && active) { events ->
        resetJob?.cancel()
        var completed = false
        try {
            displacement.stop()
            events.collect { event ->
                val progress = event.progress.coerceIn(0f, 1f)
                val resisted = 1f - (1f - progress) * (1f - progress)
                val direction = if (event.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f
                displacement.snapTo(if (enabled && active) direction * distance * resisted else 0f)
            }
            if (enabled && active) {
                action?.invoke()
                completed = true
            }
        } finally {
            resetJob =
                scope.launch {
                    if (completed) {
                        // Let the route and its effects render the resulting page, step or prompt.
                        withFrameNanos { }
                        withFrameNanos { }
                    }
                    if (!completed || (active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                        displacement.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        )
                    }
                }
        }
    }
    return Modifier.graphicsLayer { translationX = displacement.value }
}

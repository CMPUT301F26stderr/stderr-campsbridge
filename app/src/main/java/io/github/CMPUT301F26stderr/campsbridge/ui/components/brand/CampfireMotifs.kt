/*
 * Draws the campfire-terminal motifs: a blinking ember cursor and the full-screen embers that rise on a lottery win.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.brand

import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Ember
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.EmberGlow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.sin

/** Half of a terminal blink cycle, the classic 530 ms on and 530 ms off. */
private const val BLINK_HALF_MILLIS = 530

/** How long the win embers take to rise and fade. */
private const val EMBER_MILLIS = 2600

/**
 * Reads the system animator scale when a motif enters composition, so motifs stay still when the user turns
 * animations off. A change while a screen is open applies the next time that screen is shown. Previews skip
 * the system setting.
 */
@Composable
private fun rememberMotionEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * A block cursor sized to [style], drawn after a heading. It is decorative, so it adds nothing to semantics,
 * and it holds steady when system animations are off.
 * @param style Text style of the heading it follows.
 * @param modifier Caller layout.
 */
@Composable
fun BlinkingCursor(
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val height = with(LocalDensity.current) { style.fontSize.toDp() }
    val block = modifier.clearAndSetSemantics {}.padding(start = 3.dp).size(width = height * 0.55f, height = height)
    if (rememberMotionEnabled()) {
        val blink = rememberInfiniteTransition(label = "cursor")
        val alpha =
            blink.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = BLINK_HALF_MILLIS * 2
                            1f at 0
                            1f at BLINK_HALF_MILLIS - 1
                            0f at BLINK_HALF_MILLIS
                            0f at BLINK_HALF_MILLIS * 2
                        },
                        RepeatMode.Restart,
                    ),
                label = "cursorAlpha",
            )
        Box(block.graphicsLayer { this.alpha = alpha.value }.background(Ember))
    } else {
        Box(block.background(Ember))
    }
}

/**
 * Plays one burst of embers rising across the whole screen each time it enters composition, such as when a won
 * lottery opens. The embers are drawn in their own window that ignores touches, so the screen underneath stays
 * usable, and the window closes when the burst ends. Nothing is drawn when system animations are off.
 */
@Composable
fun EmberBurst() {
    if (!rememberMotionEnabled()) return
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(EMBER_MILLIS, easing = LinearOutSlowInEasing))
        finished = true
    }
    if (finished) return
    Popup(popupPositionProvider = WindowOrigin, properties = OverlayWindow) {
        EmberField({ progress.value }, Modifier.fillMaxSize())
    }
}

/** Places the ember window at the top-left of the screen, whatever composable started the burst. */
private object WindowOrigin : PopupPositionProvider {
    /**
     * Ignores the anchor and pins the window to the screen origin.
     * @param anchorBounds Bounds of the composable that started the burst.
     * @param windowSize Size of the screen area available to the window.
     * @param layoutDirection Current layout direction.
     * @param popupContentSize Measured size of the ember field.
     * @return The screen origin.
     */
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset.Zero
}

/** A window that never takes focus or touches, so taps and Back go to the screen underneath. */
private val OverlayWindow =
    PopupProperties(
        flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
    )

/**
 * Draws the embers at one point of the burst. Progress is read while drawing, so the animation only redraws.
 * @param progress Burst position from 0 (start) to 1 (finished and invisible).
 * @param modifier Caller layout.
 */
@Composable
private fun EmberField(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val rise = size.height + 56.dp.toPx()
        for (ember in Embers) {
            val t = ((p - ember.delay) / (1f - ember.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val sway = sin(t * PI.toFloat() * 2f * ember.sway) * 16.dp.toPx()
            drawCircle(
                color = if (ember.glow) EmberGlow else Ember,
                radius = ember.radius.dp.toPx() * (1f - t * 0.5f),
                center = Offset(size.width * ember.x + sway, size.height - rise * ember.lift * t),
                alpha = 1f - t,
            )
        }
    }
}

/**
 * One spark in the burst.
 * @param x Start position as a fraction of the width.
 * @param lift Share of the full rise this spark travels.
 * @param delay Share of the burst that passes before it appears.
 * @param radius Starting radius in dp.
 * @param sway Side-to-side cycles while rising.
 * @param glow Whether it uses the hot centre colour.
 */
private data class EmberParticle(
    val x: Float,
    val lift: Float,
    val delay: Float,
    val radius: Float,
    val sway: Float,
    val glow: Boolean,
)

/**
 * Fixed sparks, so every burst and preview looks the same. Position, height and timing each step by a different
 * irrational fraction, so no two sparks line up into a clump.
 */
private val Embers =
    List(56) { index ->
        EmberParticle(
            x = 0.02f + ((index * 0.618034f) % 1f) * 0.96f,
            lift = 0.55f + ((index * 0.754878f) % 1f) * 0.45f,
            delay = ((index * 0.569840f) % 1f) * 0.35f,
            radius = 3f + (index % 4),
            sway = 0.5f + (index % 4) * 0.25f,
            glow = index % 3 == 0,
        )
    }

/** Shows the cursor after a heading in the shared theme. */
@Preview(name = "Cursor", showBackground = true, widthDp = 390)
@Composable
private fun BlinkingCursorPreview() {
    CampsbridgeTheme {
        Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.discovery_title), style = MaterialTheme.typography.titleLarge)
            BlinkingCursor(MaterialTheme.typography.titleLarge)
        }
    }
}

/** Freezes the win burst partway over a phone-sized screen, since previews do not play it. */
@Preview(name = "Win embers", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun EmberFieldPreview() {
    CampsbridgeTheme {
        Box(Modifier.fillMaxSize().padding(Spacing.medium), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.lottery_membership_won),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
        EmberField({ 0.35f }, Modifier.fillMaxSize())
    }
}

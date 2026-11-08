package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

private const val PAGE_DURATION_MILLIS = 240

/** Brings a full page in without fading or resizing it.
 * @param direction Travel direction, using Start or End for layout-aware navigation.
 * @param gestureDriven Whether progress should follow the Back gesture directly.
 */
internal fun <T> AnimatedContentTransitionScope<T>.pageEnter(
    direction: SlideDirection,
    gestureDriven: Boolean = false,
): EnterTransition =
    slideIntoContainer(
        direction,
        tween(PAGE_DURATION_MILLIS, easing = if (gestureDriven) LinearEasing else FastOutSlowInEasing),
    )

/** Moves the previous page out with the same timing as its replacement.
 * @param direction Travel direction shared with the incoming page.
 * @param gestureDriven Whether progress should follow the Back gesture directly.
 */
internal fun <T> AnimatedContentTransitionScope<T>.pageExit(
    direction: SlideDirection,
    gestureDriven: Boolean = false,
): ExitTransition =
    slideOutOfContainer(
        direction,
        tween(PAGE_DURATION_MILLIS, easing = if (gestureDriven) LinearEasing else FastOutSlowInEasing),
    )

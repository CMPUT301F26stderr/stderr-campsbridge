package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.ui.common.navigation.StepNavigation

/** Slides between form steps while keeping ordinary input updates in place.
 * Only the current step accepts input.
 * @param state Current immutable form state.
 * @param step Step identity; equal values never animate.
 * @param modifier Host layout.
 * @param navigation Action that entered the target step; omitted for strictly ordered forms.
 * @param content Renders the supplied step snapshot.
 */
@Composable
fun <T> StepTransition(
    state: T,
    step: (T) -> Int,
    modifier: Modifier = Modifier,
    navigation: ((T) -> StepNavigation)? = null,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentKey = step,
        label = "Form step",
        transitionSpec = {
            if (step(initialState) == step(targetState)) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                val back =
                    navigation?.let { it(targetState) == StepNavigation.BACK }
                        ?: (step(targetState) < step(initialState))
                val direction = if (back) SlideDirection.Right else SlideDirection.Left
                (pageEnter(direction) togetherWith pageExit(direction)).using(null)
            }
        },
    ) { visibleState ->
        val interactions =
            if (step(visibleState) == step(state)) {
                Modifier
            } else {
                Modifier
                    .focusProperties { canFocus = false }
                    .semantics { hideFromAccessibility() }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(interactions)) {
            CompositionLocalProvider(
                LocalScreenInteractionEnabled provides
                    (LocalScreenInteractionEnabled.current && step(visibleState) == step(state)),
            ) {
                content(visibleState)
            }
        }
    }
}

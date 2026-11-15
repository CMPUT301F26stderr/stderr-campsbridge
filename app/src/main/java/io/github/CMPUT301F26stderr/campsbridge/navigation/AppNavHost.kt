package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.pageEnter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.pageExit

/** Hosts app pages with the shared motion policy.
 * @param controller Owns the page back stack.
 * @param startDestination First page or nested graph.
 * @param modifier Host layout.
 * @param immediateTransition Entry IDs identifying a tab or workspace switch.
 * @param builder App destinations.
 */
@Composable
fun AppNavHost(
    controller: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    immediateTransition: State<Pair<String, String>?>? = null,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = controller,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds(),
        enterTransition = {
            if ((initialState.id to targetState.id) == immediateTransition?.value) {
                EnterTransition.None
            } else {
                pageEnter(SlideDirection.Left)
            }
        },
        exitTransition = {
            if ((initialState.id to targetState.id) == immediateTransition?.value) {
                ExitTransition.None
            } else {
                pageExit(SlideDirection.Left)
            }
        },
        popEnterTransition = { pageEnter(SlideDirection.Right) },
        popExitTransition = { pageExit(SlideDirection.Right) },
        predictivePopEnterTransition = { pageEnter(SlideDirection.Right, gestureDriven = true) },
        predictivePopExitTransition = { pageExit(SlideDirection.Right, gestureDriven = true) },
        sizeTransform = null,
        builder = builder,
    )
}

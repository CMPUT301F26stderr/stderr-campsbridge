package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingRoute

/** Scopes the entire wizard to one entry; its ViewModel owns steps and saved input.
 * @param factory Explicit account dependencies and saved-state factory.
 */
@Composable
fun OnboardingNavigation(factory: ViewModelProvider.Factory) {
    val controller = rememberNavController()
    NavHost(controller, AppDestination.ONBOARDING) {
        composable(AppDestination.ONBOARDING) { OnboardingRoute(factory) }
    }
}

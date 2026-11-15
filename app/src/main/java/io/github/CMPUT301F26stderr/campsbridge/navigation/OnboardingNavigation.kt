/*
 * Scopes the entire wizard to one entry; its ViewModel owns steps and saved input.
 */

package io.github.CMPUT301F26stderr.campsbridge.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyReaderScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.policyContent

/** Scopes the entire wizard to one entry; its ViewModel owns steps and saved input.
 * @param factory Explicit account dependencies and saved-state factory.
 */
@Composable
fun OnboardingNavigation(factory: ViewModelProvider.Factory) {
    val controller = rememberNavController()
    AppNavHost(controller, AppDestination.ONBOARDING) {
        composable(AppDestination.ONBOARDING) {
            OnboardingRoute(factory) { controller.navigate("onboarding/guidelines") }
        }
        composable("onboarding/guidelines") {
            PolicyReaderScreen(policyContent(PolicyId.GUIDELINES), onBack = { controller.popBackStack() })
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Connects one navigation-scoped draft owner to repository-free screen content.
 * @param factory Explicit onboarding dependencies and saved-state construction.
 */
@Composable
fun OnboardingRoute(factory: ViewModelProvider.Factory) {
    val model: OnboardingViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state,
        model::next,
        model::back,
        model::chooseIntent,
        model::editName,
        model::editPreferences,
        model::skipInterests,
        model::retryLoad,
    )
}

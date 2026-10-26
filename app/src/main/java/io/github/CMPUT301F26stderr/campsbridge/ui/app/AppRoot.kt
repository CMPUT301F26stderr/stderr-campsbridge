package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppNavigation
import io.github.CMPUT301F26stderr.campsbridge.navigation.OnboardingNavigation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/**
 * Selects one root branch from lifecycle-aware session state without imperative navigation.
 * @param model Root session owner.
 * @param onboardingFactory Factory for one account-scoped onboarding flow.
 * @param profileFactory Factory for the established account; no entity snapshots in routes.
 */
@Composable
fun AppRoot(
    model: AppViewModel,
    onboardingFactory: (String) -> ViewModelProvider.Factory,
    profileFactory: (String) -> ViewModelProvider.Factory,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val settings =
        when (val session = state) {
            is SessionState.Ready -> session.account.settings
            is SessionState.SetupRequired -> session.account?.settings ?: AccountSettings()
            else -> AccountSettings()
        }
    CampsbridgeTheme(settings) {
        when (val session = state) {
            SessionState.Initializing -> {
                StartupContent(false, model::retry)
            }

            SessionState.Failure -> {
                StartupContent(true, model::retry)
            }

            SessionState.Unavailable -> {
                UnavailableAccountScreen()
            }

            is SessionState.SetupRequired -> {
                key(
                    session.accountId,
                ) { OnboardingNavigation(onboardingFactory(session.accountId)) }
            }

            is SessionState.Ready -> {
                key(session.account.id) {
                    AppNavigation(
                        profileFactory(session.account.id),
                        session,
                        model::selectWorkspace,
                    )
                }
            }
        }
    }
}

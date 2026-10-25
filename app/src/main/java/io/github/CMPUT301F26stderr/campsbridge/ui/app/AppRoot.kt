package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppNavigation
import io.github.CMPUT301F26stderr.campsbridge.navigation.OnboardingNavigation
import io.github.CMPUT301F26stderr.campsbridge.navigation.SessionNavigationOwner
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/**
 * Selects one root branch from lifecycle-aware session state without imperative navigation.
 * @param model Root session owner.
 * @param onboardingFactory Factory for one account-scoped onboarding flow.
 * @param profileFactory Factory for the established account; no entity snapshots in routes.
 * @param campsiteListFactory Factory for the owner's primary inventory.
 * @param campsiteDetailFactory Factory for a saved identity/context reader.
 */
@Composable
fun AppRoot(
    model: AppViewModel,
    onboardingFactory: (String) -> ViewModelProvider.Factory,
    profileFactory: (String) -> ViewModelProvider.Factory,
    campsiteListFactory: (String) -> ViewModelProvider.Factory,
    campsiteDetailFactory: (String, String, CampsiteDetailContext) -> ViewModelProvider.Factory,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val navigationOwner: SessionNavigationOwner = viewModel()
    val navigationState = rememberSaveableStateHolder()
    val navigationKey =
        remember(navigationOwner, navigationState, state) {
            navigationOwner.update(state)?.let(navigationState::removeState)
            navigationOwner.scopeKey
        }
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
                navigationState.SaveableStateProvider(checkNotNull(navigationKey)) {
                    key(
                        session.accountId,
                    ) {
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides navigationOwner,
                        ) {
                            OnboardingNavigation(onboardingFactory(session.accountId))
                        }
                    }
                }
            }

            is SessionState.Ready -> {
                navigationState.SaveableStateProvider(checkNotNull(navigationKey)) {
                    key(session.account.id) {
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides navigationOwner,
                        ) {
                            AppNavigation(
                                profileFactory(session.account.id),
                                session,
                                model::selectWorkspace,
                                campsiteListFactory(session.account.id),
                                {
                                    id,
                                    context,
                                    ->
                                    campsiteDetailFactory(session.account.id, id, context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

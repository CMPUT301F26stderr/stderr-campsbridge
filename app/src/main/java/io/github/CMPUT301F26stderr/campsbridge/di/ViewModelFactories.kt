package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseDisplayName

/**
 * Supplies explicit root dependencies; the ViewModel never receives a concrete container.
 * @param services Application-scoped platform/repository dependencies.
 */
fun appViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AppViewModel(services.identity, services.repositories.accounts, services.preferences)
        }
    }

/**
 * Constructs a graph-owned profile model with stable identity and repository interfaces.
 * @param services Application-scoped services.
 * @param accountId Ready session's identity.
 */
fun profileViewModelFactory(
    services: AppServices,
    accountId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ProfileViewModel(
                accountId,
                services.repositories.accounts,
                services.repositories.images,
                createSavedStateHandle(),
                parseName = ::parseDisplayName,
                parseEmail = null,
                parsePhone = null,
            )
            // F02 contacts remain independently pending.
        }
    }

/** Constructs the wizard with navigation-owned saved input and explicit persistence boundaries.
 * @param services Application-scoped services.
 * @param accountId Established identity awaiting setup.
 */
fun onboardingViewModelFactory(
    services: AppServices,
    accountId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            OnboardingViewModel(
                accountId,
                services.repositories.accounts,
                services.preferences,
                createSavedStateHandle(),
                parseName = ::parseDisplayName,
            )
            // F01 handoff uses the same function as the personal-details editor.
        }
    }

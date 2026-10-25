package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel

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
                parseName = null,
                parseEmail = null,
                parsePhone = null,
            )
            // Connect reviewed F01/F02 functions here; keep dependent saves pending until then.
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
                parseName = null,
            )
            // Connect ::parseDisplayName here after the reserved task passes review.
        }
    }

/** Constructs the inventory owner with F11 ordering explicitly pending.
 * @param services Selected host's dependencies.
 * @param ownerId Confirmed owner identity.
 */
fun campsiteListViewModelFactory(
    services: AppServices,
    ownerId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CampsiteListViewModel(
                ownerId,
                services.repositories.campsites,
                services.repositories.images,
                createSavedStateHandle(),
                sortCampsites = null,
            )
        }
    }

/** Constructs a saved reader for one account/record/context parent entry.
 * @param services Selected host's dependencies.
 * @param accountId Ready identity.
 * @param campsiteId Saved listing identity.
 * @param context Caller-validated presentation.
 */
fun campsiteDetailViewModelFactory(
    services: AppServices,
    accountId: String,
    campsiteId: String,
    context: CampsiteDetailContext,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CampsiteDetailViewModel(
                accountId,
                campsiteId,
                context,
                services.repositories.campsites,
                services.repositories.images,
            )
        }
    }

/** Constructs one local setup owner, without introducing persistence actions or helper fallbacks.
 * @param services Selected host's dependencies and clock.
 * @param ownerId Confirmed owner identity.
 * @param campsiteId Actual saved draft identity, or null for a new local form.
 */
fun campsiteSetupViewModelFactory(
    services: AppServices,
    ownerId: String,
    campsiteId: String?,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CampsiteSetupViewModel(
                ownerId,
                campsiteId,
                services.repositories.campsites,
                services.repositories.images,
                services.clock,
                createSavedStateHandle(),
            )
        }
    }

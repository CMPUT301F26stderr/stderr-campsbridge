package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel

/** Constructs the saved/new lottery form; reserved F13 remains explicitly pending.
 * @param services Current host's repository interfaces and clock.
 * @param ownerId Current account identity whose published sites are eligible.
 * @param lotteryId Existing draft target, or null for a new local form.
 */
fun lotterySetupViewModelFactory(
    services: AppServices,
    ownerId: String,
    lotteryId: String? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotterySetupViewModel(
                ownerId,
                services.repositories.campsites,
                services.repositories.lotteries,
                services.clock,
                createSavedStateHandle(),
                validateWindows = null,
                lotteryId = lotteryId,
            )
        }
    }

/** Constructs the actual saved owner reader independently of setup's local state.
 * @param services Current host's repository dependencies.
 * @param ownerId Current owner identity.
 * @param lotteryId Actual saved target carried by navigation.
 */
fun lotteryDetailViewModelFactory(
    services: AppServices,
    ownerId: String,
    lotteryId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotteryDetailViewModel(
                ownerId,
                lotteryId,
                services.repositories.lotteries,
                services.repositories.campsites,
            )
        }
    }

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
                services.repositories.accounts,
                services.repositories.reviews,
                createSavedStateHandle(),
                services.clock,
            )
        }
    }

/** Constructs one setup owner with explicit repository operations and stable draft-ID allocation.
 * @param services Selected host's dependencies and clock.
 * @param ownerId Confirmed owner identity.
 * @param campsiteId Actual saved draft identity, or null for a new local form.
 * @param remainingAvailabilityEdits Reserved F08 integration; null keeps requested dates pending.
 */
fun campsiteSetupViewModelFactory(
    services: AppServices,
    ownerId: String,
    campsiteId: String?,
    remainingAvailabilityEdits: AvailabilityDifference? = null,
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
                remainingAvailabilityEdits = remainingAvailabilityEdits,
            )
        }
    }

/** Constructs the independent saved-listing editor with the same F08 boundary as setup.
 * @param services Selected host's repository and clock.
 * @param ownerId Confirmed owner identity.
 * @param campsiteId Actual saved listing identity.
 * @param remainingAvailabilityEdits Reserved F08 integration; null keeps requested dates pending.
 */
fun availabilityViewModelFactory(
    services: AppServices,
    ownerId: String,
    campsiteId: String,
    remainingAvailabilityEdits: AvailabilityDifference? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AvailabilityViewModel(
                ownerId,
                campsiteId,
                services.repositories.campsites,
                services.clock,
                createSavedStateHandle(),
                remainingAvailabilityEdits,
            )
        }
    }

/** Constructs the discovery owner with reserved matching/ordering dependencies explicitly pending.
 * @param services Selected host's published listing, attachment and rating sources.
 */
fun exploreViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ExploreViewModel(
                services.repositories.campsites,
                services.repositories.images,
                services.repositories.reviews,
                createSavedStateHandle(),
                matchesText = null,
                matchesCapacity = null,
                sortCampsites = null,
                clock = services.clock,
            )
        }
    }

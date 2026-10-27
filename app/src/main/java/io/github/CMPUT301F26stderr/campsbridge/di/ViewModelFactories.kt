package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.sortCampsitesByName
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.validateLotteryWindows
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.parseDisplayName
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel

/** Constructs the actual winner wizard; F15 remains absent and explicitly pending by default.
 * @param services Current host's source/clock dependencies.
 * @param entrantId Ready account whose own win is required.
 * @param lotteryId Saved lottery identity carried by navigation.
 * @param campsiteId Saved selected allocation identity.
 */
fun bookingViewModelFactory(
    services: AppServices,
    entrantId: String,
    lotteryId: String,
    campsiteId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            BookingViewModel(
                entrantId,
                lotteryId,
                campsiteId,
                services.repositories.lotteries,
                services.repositories.campsites,
                services.repositories.bookings,
                services.repositories.images,
                services.clock,
                createSavedStateHandle(),
                availableNights = null,
            )
        }
    }

/** Constructs an explicitly scoped list and optional owner-management allocation filter.
 * @param services Current host's scoped sources and clock.
 * @param accountId Ready account identity.
 * @param context Requested owner or entrant presentation.
 * @param campsiteId Optional saved listing association.
 */
fun lotteryListViewModelFactory(
    services: AppServices,
    accountId: String,
    context: LotteryContext,
    campsiteId: String? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotteryListViewModel(
                accountId,
                context,
                services.repositories.lotteries,
                services.repositories.bookings,
                services.repositories.campsites,
                services.clock,
                createSavedStateHandle(),
                campsiteId,
            )
        }
    }

/** Constructs the saved/new lottery form with the assigned F13 schedule validator.
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
                validateWindows = ::validateLotteryWindows,
                lotteryId = lotteryId,
            )
        }
    }

/** Constructs the saved context-qualified reader independently of setup's local state.
 * @param services Current host's repository dependencies.
 * @param accountId Ready account identity.
 * @param lotteryId Actual saved target carried by navigation.
 * @param context Explicit owner management or entrant presentation.
 */
fun lotteryDetailViewModelFactory(
    services: AppServices,
    accountId: String,
    lotteryId: String,
    context: LotteryContext,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotteryDetailViewModel(
                accountId,
                lotteryId,
                services.repositories.lotteries,
                services.repositories.campsites,
                services.clock,
                services.repositories.bookings,
                context,
                services.repositories.accounts,
                createSavedStateHandle(),
                filterParticipants = null,
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

/** Constructs the inventory owner with the assigned F11 ordering function.
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
                sortCampsites = ::sortCampsitesByName,
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
                services.repositories.lotteries,
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

/** Constructs the discovery owner with reserved matching dependencies explicitly pending.
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
                sortCampsites = ::sortCampsitesByName,
                clock = services.clock,
            )
        }
    }

/** Constructs one saved aggregate reader shared by overview, payment and exact accepted terms.
 * @param services Current host's repository interfaces.
 * @param accountId Ready actor identity.
 * @param reservationId Actual saved target.
 * @param context Explicit caller presentation.
 */
fun reservationDetailViewModelFactory(
    services: AppServices,
    accountId: String,
    reservationId: String,
    context: ReservationContext,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ReservationDetailViewModel(
                accountId,
                reservationId,
                context,
                services.repositories.bookings,
            )
        }
    }

/** Constructs the account-root receipt owner; reserved ordering/count remain explicitly pending.
 * @param services Current host's repository interfaces.
 * @param accountId Ready recipient identity, independent of workspace selection.
 */
fun inboxViewModelFactory(
    services: AppServices,
    accountId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val repositories = services.repositories
            InboxViewModel(
                accountId,
                repositories.notifications,
                NotificationContentLoader(
                    repositories.notifications,
                    repositories.lotteries,
                    repositories.bookings,
                ),
                sortItems = null,
                countUnread = null,
            )
        }
    }

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryAllocationsEditViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailsEditViewModel

/** Constructs a focused saved organizer editor using the same clock and repository.
 * @param services Selected host's shared dependencies.
 * @param organizerId Confirmed acting account.
 * @param lotteryId Existing saved lottery identity.
 */
fun lotteryDetailsEditViewModelFactory(
    services: AppServices,
    organizerId: String,
    lotteryId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotteryDetailsEditViewModel(
                lotteryId,
                organizerId,
                services.repositories.lotteries,
                services.clock,
                createSavedStateHandle(),
            )
        }
    }

/** Supplies the saved participant editor without the organizer's creation/detail coordinator.
 * @param services Current repository interfaces and shared clock.
 * @param ownerId Acting participating owner.
 * @param lotteryId Existing saved lottery identity.
 */
fun lotteryAllocationsEditViewModelFactory(
    services: AppServices,
    ownerId: String,
    lotteryId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LotteryAllocationsEditViewModel(
                lotteryId,
                ownerId,
                services.repositories.campsites,
                services.repositories.lotteries,
                services.clock,
                createSavedStateHandle(),
            )
        }
    }

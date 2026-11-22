package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeViewModel

/** Binds explicit owner composition to the host's existing persistence contracts.
 * @param services Shared Firebase or local-demo dependencies.
 * @param ownerId Original participating actor.
 * @param lotteryId Saved audience source.
 */
fun announcementComposeViewModelFactory(
    services: AppServices,
    ownerId: String,
    lotteryId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AnnouncementComposeViewModel(
                ownerId,
                lotteryId,
                services.repositories.lotteries,
                services.repositories.accounts,
                services.repositories.notifications,
                createSavedStateHandle(),
            )
        }
    }

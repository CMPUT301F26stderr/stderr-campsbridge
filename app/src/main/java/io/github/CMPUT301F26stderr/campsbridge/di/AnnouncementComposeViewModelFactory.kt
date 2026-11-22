/*
 * Supplies ViewModel dependencies for announcement composition.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.AnnouncementComposeViewModel

/** Supplies the announcement composer using the same host repositories.
 * @param services Current host's repository interfaces and shared clock.
 * @param actorId Acting participating owner.
 * @param lotteryId Existing saved lottery identity.
 */
fun announcementComposeViewModelFactory(
    services: AppServices,
    actorId: String,
    lotteryId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AnnouncementComposeViewModel(
                actorId,
                lotteryId,
                services.repositories.lotteries,
                services.repositories.accounts,
                services.repositories.notifications,
                createSavedStateHandle(),
            )
        }
    }

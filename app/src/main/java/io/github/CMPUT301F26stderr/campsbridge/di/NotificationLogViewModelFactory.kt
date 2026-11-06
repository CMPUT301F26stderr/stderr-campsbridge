/*
 * Supplies the read-only log with the host's existing event and context readers.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.NotificationLogViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader

/** Supplies the read-only log with the host's existing event and context readers.
 * @param services Current notification, lottery and saved reservation repositories.
 */
fun notificationLogViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val repositories = services.repositories
            NotificationLogViewModel(
                repositories.notifications,
                NotificationContentLoader(repositories.notifications, repositories.lotteries, repositories.bookings),
                createSavedStateHandle(),
            )
        }
    }

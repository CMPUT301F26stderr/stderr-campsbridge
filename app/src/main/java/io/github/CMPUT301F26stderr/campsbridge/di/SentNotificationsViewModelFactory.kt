/*
 * Supplies read-only sender history and actual relationship resolution from the host's repositories.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.SentNotificationsViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver

/** Supplies read-only sender history and actual relationship resolution from the host's repositories.
 * @param services Shared Firebase or offline-demo composition.
 * @param accountId Original actor whose sent events are observed.
 */
fun sentNotificationsViewModelFactory(
    services: AppServices,
    accountId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val repositories = services.repositories
            SentNotificationsViewModel(
                accountId,
                repositories.notifications,
                NotificationContentLoader(
                    repositories.notifications,
                    repositories.lotteries,
                    repositories.bookings,
                    repositories.messages,
                ),
                ConversationContextResolver(repositories.campsites),
                createSavedStateHandle(),
            )
        }
    }

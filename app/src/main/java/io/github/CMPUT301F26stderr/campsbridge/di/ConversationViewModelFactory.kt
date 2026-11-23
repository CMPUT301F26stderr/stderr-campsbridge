package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationViewModel

/** Binds one saved participant conversation to the host's existing repository contracts.
 * @param services Shared Firebase or local-demo dependency composition.
 * @param accountId Actual participant actor.
 * @param reservationId Original saved conversation identity.
 */
fun conversationViewModelFactory(
    services: AppServices,
    accountId: String,
    reservationId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ConversationViewModel(
                accountId,
                reservationId,
                services.repositories.bookings,
                ConversationContextResolver(services.repositories.campsites),
                services.repositories.messages,
                services.repositories.accounts,
                createSavedStateHandle(),
            )
        }
    }

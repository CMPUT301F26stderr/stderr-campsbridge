/*
 * Supplies the participant conversation list from the host's booking and message repositories.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationContextResolver
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.ConversationListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Supplies the participant conversation list from the host's booking and message repositories.
 * @param services Shared Firebase or offline-demo composition.
 * @param accountId Account viewing its own conversations.
 * @param context Entrant reservations or reservations at the owner's campsites.
 */
fun conversationListViewModelFactory(
    services: AppServices,
    accountId: String,
    context: ReservationContext,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ConversationListViewModel(
                accountId,
                context,
                services.repositories.bookings,
                services.repositories.messages,
                ConversationContextResolver(services.repositories.campsites),
                services.repositories.accounts,
            )
        }
    }

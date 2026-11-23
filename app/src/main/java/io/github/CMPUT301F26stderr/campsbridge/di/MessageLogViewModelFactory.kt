/*
 * Supplies existing message and descriptive context sources without participant operations.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.MessageLogViewModel

/** Supplies existing message and descriptive context sources without participant operations.
 * @param services Current Firebase or shared local-demo repositories.
 */
fun messageLogViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            MessageLogViewModel(
                services.repositories.messages,
                services.repositories.bookings,
                services.repositories.campsites,
                services.repositories.accounts,
                createSavedStateHandle(),
            )
        }
    }

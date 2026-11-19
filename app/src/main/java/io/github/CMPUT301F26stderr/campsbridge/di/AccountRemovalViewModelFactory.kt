package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AccountRemovalViewModel

/** Binds root cleanup to the original device identity and existing moderation operation.
 * @param services Shared Firebase or local-demo dependencies.
 */
fun accountRemovalViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AccountRemovalViewModel(
                services.identity,
                services.repositories.accounts,
                services.repositories.moderation,
                createSavedStateHandle(),
            )
        }
    }

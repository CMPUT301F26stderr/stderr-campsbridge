/*
 * Binds shared repositories and one actual account-hosted admin identity.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.AdminRemovalViewModel

/** Binds shared repositories and one actual account-hosted admin identity. */
fun adminRemovalViewModelFactory(
    services: AppServices,
    adminId: String,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            AdminRemovalViewModel(
                adminId,
                services.repositories.accounts,
                services.repositories.campsites,
                services.repositories.images,
                services.repositories.moderation,
                createSavedStateHandle(),
            )
        }
    }

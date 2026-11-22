package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.CampsiteReviewsViewModel

/** Binds feedback to the same application repositories in Firebase and the local demo.
 * @param services Current host's shared repository dependencies.
 * @param accountId Actual signed-in actor.
 * @param campsiteId Saved listing identity.
 * @param context Requested workspace, separate from actual account capabilities.
 */
fun campsiteReviewsViewModelFactory(
    services: AppServices,
    accountId: String,
    campsiteId: String,
    context: Role,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CampsiteReviewsViewModel(
                accountId,
                campsiteId,
                context,
                services.repositories.campsites,
                services.repositories.reviews,
                services.repositories.accounts,
                createSavedStateHandle(),
                services.repositories.moderation,
            )
        }
    }

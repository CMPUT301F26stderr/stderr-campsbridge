package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory

/** Keeps this factory contract available until the feature is implemented.
 * @param services Current host's existing notification and context repositories.
 */
fun notificationLogViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {}

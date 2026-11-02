package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.AdminBrowseViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.filterProfilesByRole
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.matchesProfileText

/** Binds current account matching and repository sources to the parent graph owner. */
fun adminBrowseViewModelFactory(services: AppServices): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        AdminBrowseViewModel(services.repositories.campsites, services.repositories.accounts,
            services.repositories.images, createSavedStateHandle(), ::matchesCampsiteText, ::filterProfilesByRole, ::matchesProfileText)
    }
}

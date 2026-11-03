package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.AdminBrowseViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.*

/** Uses the same graph owner with controllable read-only dependencies. */
internal fun TestAppHost.adminBrowseFactory(): () -> ViewModelProvider.Factory = {
    viewModelFactory {
        initializer {
            adminBrowseCreations++
            AdminBrowseViewModel(adminAccounts, adminCampsites, adminImages, createSavedStateHandle(),
                { account, role -> filterProfiles?.invoke(listOf(account), role)?.contains(account) ?: matchesProfileRole(account, role) },
                { account, query -> matchesProfile?.invoke(account, query) ?: matchesProfileText(account, query) })
        }
    }
}

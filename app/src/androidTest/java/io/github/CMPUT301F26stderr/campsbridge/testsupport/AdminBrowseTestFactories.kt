package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.AdminBrowseViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.filterProfilesByRole
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.matchesProfileText
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText

/** Uses the same graph owner with controllable read-only dependencies. */
internal fun TestAppHost.adminBrowseFactory(): () -> ViewModelProvider.Factory =
    {
        viewModelFactory {
            initializer {
                adminBrowseCreations++
                AdminBrowseViewModel(
                    adminCampsites,
                    adminAccounts,
                    adminImages,
                    createSavedStateHandle(),
                    ::matchesCampsiteText,
                    filterProfiles ?: ::filterProfilesByRole,
                    matchesProfile ?: ::matchesProfileText,
                )
            }
        }
    }

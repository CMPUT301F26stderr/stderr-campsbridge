package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel

/** Exposes fixed sample results through the real controller; never implements text/guest matching.
 * Search and guest input can be inspected, but these fixture predicates intentionally keep the
 * supplied rows unchanged. The demo banner and explanation identify this presentation mode.
 * @param services Local fixture sources and fixed time.
 */
fun demoExploreViewModelFactory(services: AppServices): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ExploreViewModel(
                services.repositories.campsites,
                services.repositories.images,
                services.repositories.reviews,
                createSavedStateHandle(),
                matchesText = { _, _ -> true },
                matchesCapacity = { _, _ -> true },
                sortCampsites = { it },
                clock = services.clock,
            )
        }
    }

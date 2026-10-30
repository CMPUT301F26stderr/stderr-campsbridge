package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory

/** Keeps this factory contract available until the feature is implemented.
 * @param services Current host's source/clock dependencies.
 * @param entrantId Ready account whose own win is required.
 * @param lotteryId Saved lottery identity carried by navigation.
 * @param campsiteId Saved selected allocation identity.
 */
fun bookingViewModelFactory(
    services: AppServices,
    entrantId: String,
    lotteryId: String,
    campsiteId: String,
): ViewModelProvider.Factory =
    viewModelFactory {}

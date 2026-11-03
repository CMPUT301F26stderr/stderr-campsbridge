package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.availableBookingNights
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingViewModel

/** Creates one saved-state booking parent with the confirmed availability rule.
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
    viewModelFactory {
        initializer {
            BookingViewModel(
                entrantId,
                lotteryId,
                campsiteId,
                services.repositories.lotteries,
                services.repositories.campsites,
                services.repositories.bookings,
                services.repositories.images,
                services.clock,
                createSavedStateHandle(),
                ::availableBookingNights,
            )
        }
    }

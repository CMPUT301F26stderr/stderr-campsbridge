/*
 * Creates the real booking controller for the account-root navigation tests.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.availableBookingNights
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingViewModel

/** Creates the real booking controller for the account-root navigation tests. */
internal fun TestAppHost.bookingFactory(): (
    String,
    String,
    String,
) -> ViewModelProvider.Factory =
    { accountId, lotteryId, campsiteId ->
        viewModelFactory {
            initializer {
                BookingViewModel(
                    accountId,
                    lotteryId,
                    campsiteId,
                    lotteries,
                    campsites,
                    bookings,
                    images,
                    clock,
                    createSavedStateHandle(),
                    ::availableBookingNights,
                )
            }
        }
    }

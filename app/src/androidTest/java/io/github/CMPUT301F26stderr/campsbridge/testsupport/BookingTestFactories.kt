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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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
                    Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                    createSavedStateHandle(),
                    ::availableBookingNights,
                )
            }
        }
    }

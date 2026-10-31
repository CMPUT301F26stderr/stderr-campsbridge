package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel

/** Supplies the real saved reader and records its target and lifetime for route tests. */
internal fun TestAppHost.reservationDetailFactory(): (
    String,
    String,
    ReservationContext,
) -> ViewModelProvider.Factory =
    { accountId, id, context ->
        viewModelFactory {
            initializer {
                reservationDetailCreations++
                reservationDetailRequests.add(id to context)
                ReservationDetailViewModel(accountId, id, context, bookings)
            }
        }
    }

/** Keeps this factory contract available until the feature is implemented. */
internal fun TestAppHost.reservationListFactory(): (
    String,
    ReservationContext,
    String?,
) -> ViewModelProvider.Factory =
    { accountId, context, campsiteId ->
        viewModelFactory {}
    }

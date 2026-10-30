package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Keeps this factory contract available until the feature is implemented. */
internal fun TestAppHost.reservationDetailFactory(): (
    String,
    String,
    ReservationContext,
) -> ViewModelProvider.Factory =
    { accountId, id, context ->
        viewModelFactory {}
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

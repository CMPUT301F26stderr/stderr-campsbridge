package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Keeps the local reservation factory contract while its list screen is pending.
 * @param services Local record sources.
 * @param accountId Actor whose sample records are requested.
 * @param context Owner or entrant presentation.
 * @param campsiteId Optional management-entry scope.
 * @param categories Fixed classifications keyed by sample reservation identity.
 */
fun demoReservationListViewModelFactory(
    services: AppServices,
    accountId: String,
    context: ReservationContext,
    campsiteId: String?,
    categories: Map<String, ReservationCategory>,
): ViewModelProvider.Factory =
    viewModelFactory {}

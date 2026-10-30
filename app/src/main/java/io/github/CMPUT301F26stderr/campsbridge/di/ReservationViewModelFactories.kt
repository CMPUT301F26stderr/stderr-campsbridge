package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext

/** Keeps this factory contract available until the feature is implemented.
 * @param services Current host's repository interfaces.
 * @param accountId Ready actor identity.
 * @param reservationId Actual saved target.
 * @param context Explicit caller presentation.
 */
fun reservationDetailViewModelFactory(
    services: AppServices,
    accountId: String,
    reservationId: String,
    context: ReservationContext,
): ViewModelProvider.Factory =
    viewModelFactory {}

/** Keeps this factory contract available until the feature is implemented.
 * @param services Current repository interfaces and application clock.
 * @param accountId Current ready actor.
 * @param context Actual entrant or owner workspace.
 * @param campsiteId Optional management-entry filter.
 */
fun reservationListViewModelFactory(
    services: AppServices,
    accountId: String,
    context: ReservationContext,
    campsiteId: String?,
): ViewModelProvider.Factory =
    viewModelFactory {}

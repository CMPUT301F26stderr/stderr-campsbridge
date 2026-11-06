/*
 * Binds saved-reservation and active-list ViewModels to the current actor and shared repositories.
 */

package io.github.CMPUT301F26stderr.campsbridge.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.classifyReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic.sortReservationsByCheckIn

/** Supplies the single saved reader with the current host's booking source.
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
    viewModelFactory {
        initializer {
            ReservationDetailViewModel(
                accountId,
                reservationId,
                context,
                services.repositories.bookings,
                services.clock,
                createSavedStateHandle(),
            )
        }
    }

/** Binds the actor-scoped list to shared repositories and the completed date/order rules.
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
    viewModelFactory {
        initializer {
            ReservationListViewModel(
                accountId,
                context,
                services.repositories.bookings,
                services.repositories.accounts,
                services.repositories.images,
                services.clock,
                createSavedStateHandle(),
                campsiteId,
                classify = ::classifyReservation,
                order = ::sortReservationsByCheckIn,
            )
        }
    }

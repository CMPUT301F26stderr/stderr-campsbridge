/*
 * Supplies the real saved reader and records its target and lifetime for route tests.
 */

package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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
                ReservationDetailViewModel(
                    accountId,
                    id,
                    context,
                    bookings,
                    Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                    createSavedStateHandle(),
                )
            }
        }
    }

/** Supplies this feature's real controller with the host's controlled dependencies. */
internal fun TestAppHost.reservationListFactory(): (
    String,
    ReservationContext,
    String?,
) -> ViewModelProvider.Factory =
    { accountId, context, campsiteId ->
        viewModelFactory {
            initializer {
                reservationListCreations++
                reservationListRequests.add(context to campsiteId)
                ReservationListViewModel(
                    accountId,
                    context,
                    bookings,
                    reservationAccounts,
                    images,
                    Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                    createSavedStateHandle(),
                    campsiteId,
                    classifyReservation,
                    orderReservations,
                )
            }
        }
    }

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel

/** Reuses the task session's saved reader for one selected sample identity.
 * @param id Actual saved reservation in the task's local repository.
 */
internal fun TaskExampleSession.reservation(id: String): ReservationDetailViewModel =
    model(ReservationDetailViewModel::class.java, id) {
        ReservationDetailViewModel(
            TaskExampleData.ACCOUNT_ID,
            id,
            ReservationContext.ENTRANT,
            bookings,
            TaskExampleData.clock,
        )
    }

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationContext
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationListViewModel

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

/** Exercises the selected helper against fixed outcomes for its peer dependency. */
internal fun TaskExampleSession.reservations(): ReservationListViewModel =
    model(ReservationListViewModel::class.java) {
        ReservationListViewModel(
            TaskExampleData.ACCOUNT_ID,
            ReservationContext.ENTRANT,
            bookings,
            accounts,
            images,
            TaskExampleData.clock,
            classify =
                if (example == TaskExample.RESERVATION_CATEGORY) {
                    bindings.classifyReservation
                } else {
                    { record, _ -> TaskExampleData.categories.getValue(record.id) }
                },
            order =
                if (example ==
                    TaskExample.RESERVATION_ORDER
                ) {
                    bindings.orderReservations
                } else {
                    { rows -> rows }
                },
        )
    }

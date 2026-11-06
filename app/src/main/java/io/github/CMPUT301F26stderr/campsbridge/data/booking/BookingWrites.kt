package io.github.CMPUT301F26stderr.campsbridge.data.booking

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.CallableFunctions
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import java.time.Clock

/** Delegates coordinated writes to authenticated server transactions.
 * @param functions Callable client bound to the same Firebase project as the readers.
 * @param clock Controlled time supplied only to the emulator; cloud functions use server time.
 */
internal class BookingWrites(
    private val functions: CallableFunctions,
    private val clock: Clock,
) {
    /** Revalidates an unused win, all inventory, quote and terms in one trusted transaction.
     * @param request Selected stay and acknowledged quote.
     * @return Confirmed reservation identity.
     */
    suspend fun reserve(request: BookingRequest): String {
        val result =
            call(
                "reserve",
                mapOf(
                    "lotteryId" to request.lotteryId,
                    "entrantId" to request.entrantId,
                    "campsiteId" to request.campsiteId,
                    "checkInDate" to request.checkInDate.toString(),
                    "checkOutDate" to request.checkOutDate.toString(),
                    "guestCount" to request.guestCount.toString(),
                    "expectedTotalCents" to request.expectedTotalCents.toString(),
                    "acceptedRules" to request.acceptedRules,
                    "policyVersion" to request.policyVersion,
                ),
            )
        return result.getString("reservationId")
    }

    /** Cancels/refunds/releases atomically, repairing incomplete previously cancelled records.
     * @param request Actor, selected role and optional reason.
     * @param forRemoval Whether recorded removal authority replaces an active participant check.
     */
    suspend fun cancel(
        request: CancellationRequest,
        forRemoval: Boolean = false,
    ) {
        call(
            "cancelReservation",
            mapOf(
                "reservationId" to request.reservationId,
                "actorId" to request.actorId,
                "initiatedAs" to request.initiatedAs.name,
                "reason" to request.reason,
                "forRemoval" to forRemoval,
            ),
        )
    }

    /** Preserves typed application failures returned by the trusted operation.
     * @param name Deployed callable name.
     * @param values Explicit operation inputs; no stored record snapshots are accepted.
     */
    private suspend fun call(
        name: String,
        values: Map<String, Any?>,
    ): org.json.JSONObject {
        val now = clock.instant()
        return functions.call(
            name,
            values + (
                "emulatorTime" to
                    mapOf(
                        "seconds" to now.epochSecond,
                        "nanos" to now.nano,
                    )
            ),
        )
    }
}

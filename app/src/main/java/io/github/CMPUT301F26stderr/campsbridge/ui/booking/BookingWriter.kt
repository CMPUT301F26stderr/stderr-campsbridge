package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Acknowledged history, known rejection or read-only resolution of an uncertain attempt. */
sealed interface BookingWriteResult {
    /** Actual saved identity; cancelled history still consumes the same winning opportunity.
     * @param id Nonblank repository-confirmed reservation identity.
     */
    data class Saved(
        val id: String,
    ) : BookingWriteResult

    /** A transaction was rejected with a known domain reason; the parent decides correction.
     * @param reason Exact expected repository failure, excluding ALREADY_USED recovery.
     */
    data class Rejected(
        val reason: PersistenceFailure.Reason,
    ) : BookingWriteResult

    /** Server confirmed no consuming reservation; only the parent may allow an explicit retry. */
    data object Absent : BookingWriteResult

    /** Server confirmation failed; neither success nor absence is established. */
    data object Unknown : BookingWriteResult
}

/** Resolves one frozen request; the parent owns guards, saved uncertainty and explicit retries.
 * @param bookings Existing transaction owner and server-only consumption lookup.
 */
class BookingWriter(
    private val bookings: BookingRepository,
) {
    /** Declares one explicit submission for the prepared contract tests.
     * @param request Frozen stay to submit.
     * @param onRecovering Signals read-only resolution.
     */
    suspend fun submit(
        request: BookingRequest,
        onRecovering: () -> Unit = {},
    ): BookingWriteResult =
        BookingWriteResult.Unknown

    /** Declares the server-only opportunity lookup.
     * @param lotteryId Winning draw identity.
     * @param entrantId Winning account identity.
     */
    suspend fun recover(
        lotteryId: String,
        entrantId: String,
    ): BookingWriteResult =
        BookingWriteResult.Unknown
}

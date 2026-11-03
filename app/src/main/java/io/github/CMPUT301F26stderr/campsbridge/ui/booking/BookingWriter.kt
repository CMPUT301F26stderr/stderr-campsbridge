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
    /** Reserves once; an uncertain response triggers only a read of consuming history.
     * Cancellation propagates and cannot become a rejected/absent/saved result.
     * @param request Immutable currently reviewed input frozen by the parent.
     * @param onRecovering Synchronous phase signal before the recovery read starts.
     */
    suspend fun submit(
        request: BookingRequest,
        onRecovering: () -> Unit = {},
    ): BookingWriteResult {
        currentCoroutineContext().ensureActive()
        try {
            val id = bookings.reserve(request)
            currentCoroutineContext().ensureActive()
            if (id.isNotBlank()) return BookingWriteResult.Saved(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PersistenceFailure) {
            currentCoroutineContext().ensureActive()
            if (failure.reason != PersistenceFailure.Reason.ALREADY_USED) {
                return BookingWriteResult.Rejected(failure.reason)
            }
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
        }
        onRecovering()
        return recover(request.lotteryId, request.entrantId)
    }

    /** Resolves uncertain consumption by identity without rebuilding or resubmitting input.
     * Does not depend on current lottery/entry status; cancelled history remains a consumed win.
     * @param lotteryId Actual winning lottery identity.
     * @param entrantId Account whose opportunity may already be consumed.
     */
    suspend fun recover(
        lotteryId: String,
        entrantId: String,
    ): BookingWriteResult {
        currentCoroutineContext().ensureActive()
        return try {
            val record = bookings.getForOpportunity(lotteryId, entrantId)
            currentCoroutineContext().ensureActive()
            when {
                record == null -> BookingWriteResult.Absent

                record.id.isBlank() || record.lotteryId != lotteryId ||
                    record.entrantId != entrantId -> BookingWriteResult.Unknown

                else -> BookingWriteResult.Saved(record.id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            BookingWriteResult.Unknown
        }
    }
}

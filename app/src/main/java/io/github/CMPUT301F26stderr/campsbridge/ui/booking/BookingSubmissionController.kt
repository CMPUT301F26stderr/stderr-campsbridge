package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns one guarded save, its uncertainty marker and the actual saved-page handoff.
 * @param lotteryId Draw whose winning opportunity is consumed.
 * @param entrantId Account that owns the win.
 * @param bookings Atomic write and server-confirmed history source.
 * @param savedState Primitive uncertainty and completion values for this wizard.
 * @param scope Wizard-owned coroutine lifetime.
 * @param onChanged Reprojects content when command progress changes.
 * @param onResolved Applies known absence or rejection to the retained draft and reads.
 */
internal class BookingSubmissionController(
    private val lotteryId: String,
    private val entrantId: String,
    bookings: BookingRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onResolved: (BookingWriteResult) -> Unit,
) {
    var reservationId = savedState.get<String>("bookingReservationId")?.takeIf { it.isNotBlank() }
        private set
    var recoveryRequired = savedState.get<Boolean>("bookingRecoveryRequired") == true
        private set
    var snapshot: BookingUiState.Content? = null
        private set
    private val writer = BookingWriter(bookings)
    private var job: Job? = null
    private var version = 0L
    private var closed = false
    private val mutableState =
        MutableStateFlow<BookingSubmission>(
            if (recoveryRequired &&
                reservationId == null
            ) {
                BookingSubmission.Recovering
            } else {
                BookingSubmission.Idle
            },
        )
    val state = mutableState.asStateFlow()
    private val mutableCompletion =
        MutableStateFlow(
            savedState.get<String>("bookingCompletion")?.takeIf { it == reservationId },
        )
    val completion = mutableCompletion.asStateFlow()

    init {
        if (reservationId != null) setRecoveryRequired(false)
    }

    /** Input stays locked while a save might have consumed this win. */
    val blocksEditing: Boolean
        get() =
            when (state.value) {
                BookingSubmission.Submitting,
                BookingSubmission.Recovering,
                BookingSubmission.Unknown,
                -> true

                else -> false
            }

    /** Only an active save or recovery read temporarily holds navigation. */
    val blocksExit: Boolean
        get() =
            state.value == BookingSubmission.Submitting ||
                state.value == BookingSubmission.Recovering

    /** Installs one command before suspension; recovery never repeats the reservation write.
     * @param request Frozen reviewed stay, or null for read-only recovery.
     * @param content Content retained while the command temporarily blocks input.
     */
    fun start(
        request: BookingRequest?,
        content: BookingUiState.Content? = snapshot,
    ) {
        if (closed || reservationId != null || job?.isActive == true) return
        val currentVersion = ++version
        snapshot = content
        mutableState.value =
            if (request == null) BookingSubmission.Recovering else BookingSubmission.Submitting
        setRecoveryRequired(true)
        onChanged()
        val pending =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result =
                        if (request == null) {
                            writer.recover(lotteryId, entrantId)
                        } else {
                            writer.submit(request) {
                                if (!closed && version == currentVersion) {
                                    mutableState.value = BookingSubmission.Recovering
                                }
                            }
                        }
                    currentCoroutineContext().ensureActive()
                    if (!closed && version == currentVersion) finish(result)
                } catch (cancelled: CancellationException) {
                    if (!closed && version == currentVersion && recoveryRequired) {
                        mutableState.value = BookingSubmission.Unknown
                    }
                    throw cancelled
                }
            }
        job = pending
        pending.start()
    }

    /** Retries only a failed history read, with no second reserve call. */
    fun retryRecovery() {
        if (state.value == BookingSubmission.Unknown && recoveryRequired) start(null)
    }

    /** Drops feedback for an older draft after a user changes its values or approvals. */
    fun clearFeedback() {
        if (state.value is BookingSubmission.Rejected || state.value == BookingSubmission.Absent) {
            mutableState.value = BookingSubmission.Idle
        }
    }

    /** Shares confirmed consuming history between ordinary entry and command recovery.
     * @param id Actual nonblank saved reservation identity.
     */
    fun rememberReservation(id: String) {
        if (closed) return
        reservationId = id
        savedState["bookingReservationId"] = id
        setRecoveryRequired(false)
        snapshot = null
        mutableState.value = BookingSubmission.Idle
        onChanged()
        requestOpen()
    }

    /** Retains an explicit saved-target handoff until the active route navigates. */
    fun requestOpen() {
        if (closed) return
        val id = reservationId ?: return
        savedState["bookingCompletion"] = id
        mutableCompletion.value = id
    }

    /** Clears a completion only after the active route has delivered it. */
    fun acknowledgeCompletion() {
        if (closed) return
        savedState.remove<String>("bookingCompletion")
        mutableCompletion.value = null
    }

    /** Applies confirmed results without replaying an uncertain mutation.
     * @param result Acknowledged save, known rejection, absence or unresolved result.
     */
    private fun finish(result: BookingWriteResult) {
        when (result) {
            is BookingWriteResult.Saved -> {
                rememberReservation(result.id)
            }

            BookingWriteResult.Unknown -> {
                mutableState.value = BookingSubmission.Unknown
                onChanged()
            }

            BookingWriteResult.Absent, is BookingWriteResult.Rejected -> {
                setRecoveryRequired(false)
                snapshot = null
                mutableState.value =
                    when (result) {
                        BookingWriteResult.Absent -> BookingSubmission.Absent
                        is BookingWriteResult.Rejected -> BookingSubmission.Rejected(result.reason)
                    }
                onResolved(result)
            }
        }
    }

    /** Clears uncertainty only for an acknowledged or server-confirmed outcome.
     * @param required Whether the previous write still needs confirmation.
     */
    private fun setRecoveryRequired(required: Boolean) {
        recoveryRequired = required
        if (required) {
            savedState["bookingRecoveryRequired"] = true
        } else {
            savedState.remove<Boolean>("bookingRecoveryRequired")
        }
    }

    /** Retires callbacks before cancelling this wizard's current command. */
    fun close() {
        closed = true
        version++
        job?.cancel()
    }
}

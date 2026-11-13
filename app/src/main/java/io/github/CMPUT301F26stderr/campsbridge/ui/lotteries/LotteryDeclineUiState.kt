package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

/** Independent command feedback while the saved reader continues observing the entry.
 * @param canDecline Confirmed unused win is within the booking window.
 * @param isConfirming An explicit, unsubmitted confirmation is open.
 * @param isSubmitting The repository call is awaiting its result.
 * @param isAwaitingEntry A successful operation is waiting for the entry observation.
 * @param failed The last attempt failed and needs explicit recovery.
 */
data class LotteryDeclineUiState(
    val canDecline: Boolean = false,
    val isConfirming: Boolean = false,
    val isSubmitting: Boolean = false,
    val isAwaitingEntry: Boolean = false,
    val failed: Boolean = false,
)

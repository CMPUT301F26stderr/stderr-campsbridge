package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure

/** Tracks a command separately from the saved record; uncertainty blocks another write. */
enum class CancellationPhase { IDLE, CANCELLING, RECOVERING, UNKNOWN }

/** Editable confirmation input.
 * @param reason Optional explanation, preserved until a confirmed result or dismissal.
 */
data class CancellationPrompt(
    val reason: String,
)

/** Projects explicit actions without duplicating the saved cancellation or payment.
 * @param canRequest Whether a new confirmation is currently allowed.
 * @param prompt Input retained through submission and recovery.
 * @param phase Current command or reconciliation phase.
 * @param failure Known rejection which did not commit.
 * @param notCancelled Server confirmed that the stay is still active.
 */
data class CancellationUiState(
    val canRequest: Boolean = false,
    val prompt: CancellationPrompt? = null,
    val phase: CancellationPhase = CancellationPhase.IDLE,
    val failure: PersistenceFailure.Reason? = null,
    val notCancelled: Boolean = false,
)

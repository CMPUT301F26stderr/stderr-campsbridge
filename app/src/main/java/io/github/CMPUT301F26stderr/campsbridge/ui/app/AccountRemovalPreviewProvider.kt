/*
 * Passive root confirmation, progress and recovery states; no operation is simulated.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Passive root confirmation, progress and recovery states; no operation is simulated. */
class AccountRemovalPreviewProvider : PreviewParameterProvider<AccountRemovalUiState> {
    override val values =
        sequenceOf(
            AccountRemovalPhase.CONFIRMING,
            AccountRemovalPhase.REMOVING,
            AccountRemovalPhase.RESTORING,
            AccountRemovalPhase.RETRY,
            AccountRemovalPhase.NOT_STARTED,
            AccountRemovalPhase.RECOVERY_FAILED,
            AccountRemovalPhase.UNAVAILABLE,
        ).map {
            AccountRemovalUiState(it, AccountRemovalRequest("demo-account", "demo-account"))
        }
}

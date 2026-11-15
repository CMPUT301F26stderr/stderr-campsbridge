/*
 * Supplies passive confirmation, progress and failure states without repository operations.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Supplies passive confirmation, progress and failure states without repository operations. */
class CancellationPreviewProvider : PreviewParameterProvider<CancellationUiState> {
    override val values =
        sequenceOf(
            CancellationUiState(canRequest = true),
            CancellationUiState(canRequest = true, prompt = CancellationPrompt("")),
            CancellationUiState(
                prompt = CancellationPrompt("Road closure near the campsite; we cannot safely reach the entrance."),
                phase = CancellationPhase.CANCELLING,
            ),
            CancellationUiState(
                canRequest = true,
                prompt = CancellationPrompt("Our travel dates have changed."),
                failure = PersistenceFailure.Reason.NOT_ALLOWED,
            ),
            CancellationUiState(phase = CancellationPhase.RECOVERING),
            CancellationUiState(phase = CancellationPhase.UNKNOWN),
            CancellationUiState(canRequest = true, prompt = CancellationPrompt("Plans changed"), notCancelled = true),
        )
}

/** Previews entrant confirmation at ordinary and enlarged text sizes.
 * @param state Passive command or input state.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun EntrantCancellationPreview(
    @PreviewParameter(CancellationPreviewProvider::class) state: CancellationUiState,
) {
    CampsbridgeTheme {
        ReservationDetailScreen(
            ReservationDetailUiState.Content(previewReservation()),
            {},
            {},
            {},
            {},
            context = ReservationContext.ENTRANT,
            cancellation = state,
        )
    }
}

/** Checks owner attribution and long saved names without simulating a successful write.
 * @param state Passive confirmation or recovery state.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun OwnerRejectionPreview(
    @PreviewParameter(CancellationPreviewProvider::class) state: CancellationUiState,
) {
    val record = previewReservation()
    CampsbridgeTheme {
        ReservationDetailScreen(
            ReservationDetailUiState.Content(
                record.copy(bookedCampsite = record.bookedCampsite.copy(name = "North Saskatchewan River Valley Camp")),
            ),
            {},
            {},
            {},
            {},
            context = ReservationContext.OWNER,
            cancellation = state,
        )
    }
}

/** Keeps independent read recovery visible when the ordinary record observation fails. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun CancellationUnknownSourcePreview() {
    CampsbridgeTheme {
        ReservationDetailScreen(
            ReservationDetailUiState.Failure,
            {},
            {},
            {},
            {},
            cancellation = CancellationUiState(phase = CancellationPhase.UNKNOWN),
        )
    }
}

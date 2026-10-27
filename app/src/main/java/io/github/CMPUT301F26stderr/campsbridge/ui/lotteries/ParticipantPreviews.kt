package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import java.time.Instant

/** Passive states cover unavailable names, partial results and explicit pending filtering. */
class ParticipantPreviewProvider : PreviewParameterProvider<ParticipantListUiState> {
    override val values =
        sequenceOf(
            ParticipantListUiState.Loading,
            ParticipantListUiState.Failure,
            ParticipantListUiState.Content(emptyList(), listOf(EntryState.WAITING)),
            ParticipantListUiState.Content(
                emptyList(),
                listOf(EntryState.WAITING),
                filterPending = true,
            ),
            ParticipantListUiState.Content(
                listOf(
                    ParticipantRow(
                        "entrant-A",
                        ParticipantName.Available("Alexandra River Camper"),
                        LotteryMembershipStatus.RESULTS_PENDING,
                        Instant.parse("2027-05-05T20:00:00Z"),
                    ),
                    ParticipantRow(
                        "entrant-B",
                        ParticipantName.Unavailable,
                        LotteryMembershipStatus.RESULTS_PENDING,
                        Instant.parse("2027-05-05T20:30:00Z"),
                    ),
                    ParticipantRow(
                        "entrant-C",
                        ParticipantName.Failure,
                        LotteryMembershipStatus.WITHDRAWN,
                        Instant.parse("2027-05-05T20:45:00Z"),
                    ),
                ),
                listOf(EntryState.WAITING, EntryState.WITHDRAWN),
            ),
            ParticipantListUiState.Unavailable,
        )
}

/** Supplies actual parent read outcomes separately from an independently loaded child. */
class ParticipantParentPreviewProvider : PreviewParameterProvider<LotteryDetailUiState> {
    override val values: Sequence<LotteryDetailUiState> =
        sequenceOf(
            LotteryDetailUiState.Loading,
            LotteryDetailUiState.Failure,
            LotteryDetailUiState.Unavailable,
        )
}

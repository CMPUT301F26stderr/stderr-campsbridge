package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import java.time.Instant

/** Supplies saved editor input and required-source outcomes without repository operations. */
class LotteryDetailsEditPreviewProvider : PreviewParameterProvider<LotteryDetailsEditUiState> {
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val record =
        Lottery(
            "lottery-a",
            "owner-a",
            "May riverside camping for families and friends",
            "Quiet stays beside the river.\nPlease arrive before sunset.",
            LotteryConfiguration(
                anchor.plusSeconds(3600),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(172800),
                5,
                50,
            ),
            0,
            anchor,
            anchor,
        )
    private val content = LotteryDetailsEditUiState.Content(record, lotteryDetailsDraft(record), true, isDirty = true)
    private val frozen =
        record.copy(
            configuration = record.configuration.copy(registrationOpensAt = anchor.minusSeconds(3600)),
        )
    override val values: Sequence<LotteryDetailsEditUiState> =
        sequenceOf(
            content,
            content.copy(
                lottery = frozen,
                draft = lotteryDetailsDraft(frozen),
                configurationEditable = false,
                isDirty = false,
            ),
            content.copy(isSaving = true),
            content.copy(saveFailed = true),
            content.copy(contextChanged = true),
            LotteryDetailsEditUiState.Loading,
            LotteryDetailsEditUiState.Failure,
            LotteryDetailsEditUiState.Unavailable,
        )
}

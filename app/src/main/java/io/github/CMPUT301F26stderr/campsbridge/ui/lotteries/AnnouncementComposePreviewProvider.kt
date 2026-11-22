package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import java.time.Instant

/** Supplies passive original-owner announcement and recovery states without repository writes. */
class AnnouncementComposePreviewProvider : PreviewParameterProvider<AnnouncementComposeUiState> {
    override val values: Sequence<AnnouncementComposeUiState> =
        run {
            val now = Instant.parse("2027-05-10T18:00:00Z")
            val lottery =
                Lottery(
                    "draw",
                    "owner",
                    "Long weekend by the river and northern meadow",
                    "Quiet stays",
                    LotteryConfiguration(
                        now,
                        now.plusSeconds(3600),
                        now.plusSeconds(3600),
                        now.plusSeconds(7200),
                        2,
                        null,
                    ),
                    3,
                    now,
                    now,
                )
            val ready =
                AnnouncementComposeUiState(
                    read = AnnouncementRead.READY,
                    lottery = lottery,
                    canSend = true,
                    title = "Trail access for the long weekend",
                    body =
                        "The riverside trail is open again.\n" +
                            "Please use the east gate when you arrive and keep food secured.",
                )
            val attempt = AnnouncementAttempt("event", lottery.id, lottery.ownerId, ready.title, ready.body)
            sequenceOf(
                ready,
                ready.copy(title = "", body = ""),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.SENDING),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.CHECKING),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.RETRY),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.ABSENT),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.CONFLICT),
                ready.copy(title = "", body = "", titleInvalid = true, bodyInvalid = true),
                ready.copy(confirmingDiscard = true),
                ready.copy(read = AnnouncementRead.FAILURE, canSend = false),
                ready.copy(read = AnnouncementRead.UNAVAILABLE, canSend = false),
                AnnouncementComposeUiState(),
                ready.copy(attempt = attempt, phase = AnnouncementPhase.RETRY, confirmingDiscard = true),
            )
        }
}

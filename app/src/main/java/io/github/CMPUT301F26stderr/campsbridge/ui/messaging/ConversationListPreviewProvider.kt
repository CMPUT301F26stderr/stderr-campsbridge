/*
 * Supplies realistic conversation-list outcomes for previews.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.messaging

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.logic.ConversationSummary
import java.time.Instant

/** Realistic list outcomes, including a long campsite name and a long message. */
class ConversationListPreviewProvider : PreviewParameterProvider<ConversationListUiState> {
    private val river = previewReservation()
    private val lake =
        river.copy(
            id = "reservation-lake",
            bookedCampsite =
                river.bookedCampsite.copy(
                    name = "Lake Louise walk-in sites beside the long portage trail",
                ),
        )
    private val at = Instant.parse("2027-04-25T18:30:00Z")
    private val rows =
        listOf(
            ConversationSummary(
                lake,
                Message(
                    "m-lake",
                    lake.id,
                    "owner",
                    "The gate code changed this week. It's 4821, and quiet hours start at ten, so please arrive before then.",
                    at,
                ),
                unread = 2,
            ),
            ConversationSummary(
                river,
                Message("m-river", river.id, "entrant", "Thanks, see you Friday!", at.minusSeconds(86400), at),
                unread = 0,
                latestIsMine = true,
            ),
        )

    override val values =
        sequenceOf(
            ConversationListUiState.Content(rows, names = mapOf(lake.id to "Alex Morgan", river.id to "Sarah Jenkins")),
            ConversationListUiState.Content(rows.drop(1), failedReservationIds = setOf(lake.id)),
            ConversationListUiState.Content(emptyList()),
            ConversationListUiState.Content(emptyList(), loading = true),
            ConversationListUiState.Loading,
            ConversationListUiState.Failure,
        )
}

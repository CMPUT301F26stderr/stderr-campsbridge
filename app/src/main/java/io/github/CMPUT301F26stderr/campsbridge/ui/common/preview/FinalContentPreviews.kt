package io.github.CMPUT301F26stderr.campsbridge.ui.common.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.CheckboxRow
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.AllocationSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessagePresentation
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessageRow
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.RatingInput
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.ReviewRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate

/** Shows known and unavailable campsite context without implying free booking inventory. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large allocation", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun AllocationContentPreview() {
    val campsite =
        previewCampsite().copy(
            details = previewCampsite().details.copy(name = "Riverbend family campsite and riverside pitches"),
        )
    val offer = LotteryOffer(campsite.id, listOf(18, 19, 22).map { LocalDate.of(2027, 5, it) })
    FinalContentPreview {
        AllocationSummary(offer, campsite, ownerLabel = "Alex Morgan and family")
        AllocationSummary(offer, null)
    }
}

/** Keeps empty, selected and pending choices readable as the touch target scale increases. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large rating controls", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun RatingContentPreview() {
    FinalContentPreview {
        RatingInput(null, {})
        RatingInput(3, {})
        RatingInput(3, {}, enabled = false)
    }
}

/** Keeps organizer locks readable beside a long owner name at both approved scales. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large owner selection", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun OwnerSelectionPreview() {
    FinalContentPreview {
        CheckboxRow(
            "Alex Morgan and the Riverbend campsite team",
            false,
            {},
            supportingText = "owner-riverbend",
        )
        CheckboxRow("Sam", true, {}, enabled = false, supportingText = "The organizer stays selected")
    }
}

/** Includes independently missing feedback and author context, with a multiline body. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large feedback", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun ReviewContentPreview() {
    val anchor = Instant.parse("2027-05-10T18:00:00Z")
    val review =
        Review(
            "site-a",
            "author-a",
            4,
            "Quiet evenings beside the river.\nThe campsite has room for the whole family.",
            anchor,
            anchor,
        )
    FinalContentPreview {
        ReviewRow(review, "Alex Morgan and family")
        ReviewRow(review.copy(rating = null), null)
        ReviewRow(review.copy(body = null), "Sam")
    }
}

/** Shows participant direction and read-only admin content using the same saved message. */
@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Preview(name = "Large messages", showBackground = true, widthDp = 360, heightDp = 1200, fontScale = 2f)
@Composable
private fun MessageContentPreview() {
    val message =
        Message(
            "message-a",
            "reservation-a",
            "owner-a",
            "The gate opens at noon.\nPlease let us know if your group will arrive later in the evening.",
            Instant.parse("2027-05-10T18:00:00Z"),
        )
    FinalContentPreview {
        MessageRow(message, "Alex Morgan", MessagePresentation.RECEIVED)
        MessageRow(message, "Sam", MessagePresentation.SENT)
        MessageRow(message, "Alex Morgan", MessagePresentation.ADMIN, recipientLabel = "Sam")
    }
}

/** Hosts component examples inside the real screen insets, with scroll for enlarged content.
 * @param content Supplied facts and passive controls for visual inspection.
 */
@Composable
private fun FinalContentPreview(content: @Composable ColumnScope.() -> Unit) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        AppScreen(title = null) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                content = content,
            )
        }
    }
}

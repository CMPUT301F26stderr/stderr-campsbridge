package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.NightRange
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.formatNightRange
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.components.AllocationSummary
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessagePresentation
import io.github.CMPUT301F26stderr.campsbridge.ui.messaging.components.MessageRow
import io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components.ReviewRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Fixed content and callback contracts for the Final shared components. */
class FinalSharedContentTest {
    @get:Rule val compose = createComposeRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val locale =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.configuration.locales[0]

    /** SC-01: gaps stay visible, missing context keeps identity, and only supplied actions run. */
    @Test fun allocationKeepsOfferedRangesAndCallerActions() {
        val site = previewCampsite()
        val context = mutableStateOf(site)
        val missing = mutableStateOf(false)
        val showActions = mutableStateOf(true)
        val dates = listOf(18, 19, 22).map { LocalDate.of(2027, 5, it) }
        val offer = LotteryOffer(site.id, dates)
        val actions = mutableListOf<Pair<String, String>>()
        compose.setContent {
            CampsbridgeTheme {
                AllocationSummary(
                    offer,
                    if (missing.value) null else context.value,
                    ownerLabel = "Alex",
                    actions =
                        if (showActions.value) {
                            { id ->
                                for (label in listOf("Edit", "Remove", "Choose")) {
                                    TextButton(onClick = { actions += label to id }) { Text(label) }
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
        compose.onNodeWithText(site.details.name).assertIsDisplayed()
        compose.onNodeWithText(site.details.locationLabel).assertIsDisplayed()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("3 offered nights").assertIsDisplayed()
        compose.onNodeWithText(formatNightRange(NightRange(dates[0], dates[1]), locale)).assertIsDisplayed()
        compose.onNodeWithText(formatNightRange(NightRange(dates[2], dates[2]), locale)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Pair<String, String>>(), actions) }
        for (label in listOf("Edit", "Remove", "Choose")) compose.onNodeWithText(label).performClick()
        compose.runOnIdle {
            assertEquals(listOf("Edit", "Remove", "Choose").map { it to site.id }, actions)
            missing.value = true
            showActions.value = false
        }
        compose.onNodeWithText("Unavailable campsite (${site.id})").assertIsDisplayed()
        compose.onNodeWithText("3 offered nights").assertIsDisplayed()
        for (label in listOf("Edit", "Remove", "Choose")) compose.onNodeWithText(label).assertDoesNotExist()
    }

    /** SC-04: absent stars/text stay absent and moderation is supplied by the caller. */
    @Test fun feedbackKeepsIndependentComponentsAndOptionalActions() {
        val rating = Review("site-a", "rating-author", 4, null, anchor, anchor)
        val written = rating.copy(authorId = "text-author", rating = null, body = "Quiet nights.\nClean campsite.")
        val combined = rating.copy(authorId = "missing-author", body = "Close to the river.")
        var removed: Review? = null
        compose.setContent {
            CampsbridgeTheme {
                Column {
                    ReviewRow(rating, "Alex", Modifier.testTag("rating-only"))
                    ReviewRow(written, "Sam", Modifier.testTag("text-only"))
                    ReviewRow(combined, null, actions = { review ->
                        TextButton(onClick = { removed = review }) { Text("Remove rating") }
                    })
                }
            }
        }
        compose.onNode(hasText("Rating: 4 out of 5") and hasAnyAncestor(hasTestTag("rating-only"))).assertIsDisplayed()
        compose
            .onAllNodes(
                hasText("Rating:", substring = true) and hasAnyAncestor(hasTestTag("text-only")),
            ).assertCountEquals(0)
        compose.onNodeWithText(written.body!!).assertIsDisplayed()
        compose.onNodeWithText(combined.body!!).assertIsDisplayed()
        compose.onNodeWithText("Unavailable profile (missing-author)").assertIsDisplayed()
        compose.onAllNodes(hasText("Remove rating")).assertCountEquals(1)
        compose.onNodeWithText("Remove rating").performClick()
        compose.runOnIdle { assertEquals(combined, removed) }
    }

    /** SC-05: each presentation keeps the saved body, participant label and event time. */
    @Test fun messagesExplainDirectionAndPreserveSavedContent() {
        val message = Message("message-a", "reservation-a", "owner-a", "The gate opens at noon.\nSee you then!", anchor)
        compose.setContent {
            CampsbridgeTheme {
                Column {
                    MessageRow(message, "Alex", MessagePresentation.SENT)
                    MessageRow(message.copy(id = "message-b"), "Alex", MessagePresentation.RECEIVED)
                    MessageRow(
                        message.copy(id = "message-c"),
                        "Alex",
                        MessagePresentation.ADMIN,
                        recipientLabel = "Sam",
                    )
                }
            }
        }
        compose.onAllNodes(hasText(message.body)).assertCountEquals(3)
        compose.onAllNodes(hasText("Alex")).assertCountEquals(3)
        compose.onAllNodes(hasText(formatDateTime(anchor, locale))).assertCountEquals(3)
        compose.onNodeWithText("Sent").assertIsDisplayed()
        compose.onNodeWithText("Received").assertIsDisplayed()
        compose.onNodeWithText("To: Sam").assertIsDisplayed()
    }
}

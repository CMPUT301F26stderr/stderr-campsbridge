/*
 * Shows independent component consequences and recovery actions at both configured scales.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Shows independent component consequences and recovery actions at both configured scales.
 * @param state Original target and passive action outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large moderation", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ReviewModerationPreview(
    @PreviewParameter(ReviewModerationPreviewProvider::class) state: ReviewModerationState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        ReviewModerationHost(state, {}, {}, {}, {})
    }
}

/** Shows rating-only, text-only and combined rows with independent admin actions. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large admin feedback", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun AdminFeedbackActionsPreview() {
    val reader =
        CampsiteReviewsPreviewProvider().values.first().copy(
            isAuthor = false,
            canEdit = false,
            canModerate = true,
        )
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CampsiteReviewsScreen(reader, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, reviewActions = { review ->
            ReviewModerationActions(review, enabled = true) {}
        })
    }
}

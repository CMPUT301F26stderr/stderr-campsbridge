package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Both independent components use the same retained target and confirmation host. */
@Composable
fun ReviewModerationActions(
    controller: ReviewModerationController,
    review: Review,
    campsiteName: String,
    authorName: String?,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    for (component in ReviewModerationComponent.entries) {
        if (component == ReviewModerationComponent.RATING && review.rating == null) continue
        if (component == ReviewModerationComponent.TEXT && review.body == null) continue
        TextButton(
            onClick = { controller.request(review, component, campsiteName, authorName) },
            enabled = state.phase == ReviewModerationPhase.IDLE,
        ) {
            Text(
                stringResource(
                    if (component ==
                        ReviewModerationComponent.RATING
                    ) {
                        R.string.moderation_remove_rating
                    } else {
                        R.string.moderation_remove_text
                    },
                ),
            )
        }
    }
}

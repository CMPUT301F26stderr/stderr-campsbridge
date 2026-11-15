package io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Presents the independently saved stars and text with optional caller-owned actions.
 * @param review Authoritative saved feedback.
 * @param authorLabel Resolved display name, or null when the profile is unavailable.
 * @param modifier Caller layout.
 * @param actions Optional moderation content receiving the original review identity.
 */
@Composable
fun ReviewRow(
    review: Review,
    authorLabel: String?,
    modifier: Modifier = Modifier,
    actions: @Composable ((Review) -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                authorLabel ?: stringResource(R.string.feedback_author_unavailable, review.authorId),
                style = MaterialTheme.typography.titleSmall,
            )
            review.rating?.let {
                Text(
                    stringResource(R.string.feedback_rating_value, it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        review.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            formatDateTime(review.updatedAt, LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions?.invoke(review)
    }
}

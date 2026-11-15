/*
 * Renders shared inline notices with visible text that explains their meaning alongside color and icons.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shared emphasis for notices; the visible title must communicate the meaning too. */
enum class NoticeTone { INFORMATION, ERROR }

/** Inline feedback preserving the surrounding screen and draft.
 * @param title Short explicit status.
 * @param message Useful explanation.
 * @param modifier Caller layout.
 * @param tone Shared semantic color treatment.
 * @param action Optional caller-owned recovery action.
 */
@Composable
fun InlineNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFORMATION,
    action: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            if (tone == NoticeTone.ERROR) error(message)
        },
        shape = MaterialTheme.shapes.medium,
        color = if (tone == NoticeTone.ERROR) colors.errorContainer else colors.surfaceContainerLow,
        contentColor =
            if (tone ==
                NoticeTone.ERROR
            ) {
                colors.onErrorContainer
            } else {
                colors.onSurface
            },
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

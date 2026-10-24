package io.github.CMPUT301F26stderr.campsbridge.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Presents a shared form action and blocks taps while its operation is pending.
 * @param label Localized action text.
 * @param onClick Explicit action supplied by the owner.
 * @param modifier Layout and semantics supplied by the caller.
 * @param enabled Whether the action is permitted.
 * @param isSubmitting Whether the operation is pending.
 * @param progressDescription Localized progress announcement.
 */
@Composable
fun AppActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSubmitting: Boolean = false,
    progressDescription: String = label,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isSubmitting,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(
                            20.dp,
                        ).semantics { contentDescription = progressDescription },
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(label)
    }
}

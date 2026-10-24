package io.github.CMPUT301F26stderr.campsbridge.ui.components.actions

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Consistent Material hierarchy for actions sharing submission behavior. */
enum class ActionStyle { PRIMARY, SECONDARY, TEXT, DESTRUCTIVE }

/**
 * Presents a shared form action and blocks taps while its operation is pending.
 * @param label Localized action text.
 * @param onClick Explicit action supplied by the owner.
 * @param modifier Layout and semantics supplied by the caller.
 * @param enabled Whether the action is permitted.
 * @param isSubmitting Whether the operation is pending.
 * @param progressDescription Localized progress announcement.
 * @param style Shared visual emphasis.
 */
@Composable
fun AppActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSubmitting: Boolean = false,
    progressDescription: String = label,
    style: ActionStyle = ActionStyle.PRIMARY,
) {
    val content: @Composable RowScope.() -> Unit = {
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
    val size = modifier.heightIn(min = 48.dp * LocalControlScale.current.coerceAtLeast(1f))
    val active = enabled && !isSubmitting
    when (style) {
        ActionStyle.PRIMARY -> {
            Button(onClick, size, enabled = active, content = content)
        }

        ActionStyle.SECONDARY -> {
            OutlinedButton(onClick, size, enabled = active, content = content)
        }

        ActionStyle.TEXT -> {
            TextButton(onClick, size, enabled = active, content = content)
        }

        ActionStyle.DESTRUCTIVE -> {
            Button(
                onClick,
                size,
                enabled = active,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                content = content,
            )
        }
    }
}

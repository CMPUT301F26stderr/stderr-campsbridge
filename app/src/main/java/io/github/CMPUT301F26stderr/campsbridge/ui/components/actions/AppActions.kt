/*
 * Defines shared Material action buttons and submission feedback for forms.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.components.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Consistent Material hierarchy for actions sharing submission behavior. */
enum class ActionStyle { PRIMARY, SECONDARY, TEXT, DESTRUCTIVE, DESTRUCTIVE_OUTLINED }

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
        Box(Modifier.widthIn(min = 20.dp), contentAlignment = Alignment.Center) {
            // Keep the label's measured size while the progress indicator replaces its ink.
            Text(label, Modifier.alpha(if (isSubmitting) 0f else 1f))
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).semantics { contentDescription = progressDescription },
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
    val size = modifier.heightIn(min = 48.dp * LocalControlScale.current.coerceAtLeast(1f))
    val active = enabled && !isSubmitting
    when (style) {
        ActionStyle.PRIMARY -> {
            Button(onClick, shapes = ButtonDefaults.shapes(), modifier = size, enabled = active, content = content)
        }

        ActionStyle.SECONDARY -> {
            OutlinedButton(
                onClick,
                shapes = ButtonDefaults.shapes(),
                modifier = size,
                enabled = active,
                content = content,
            )
        }

        ActionStyle.TEXT -> {
            TextButton(onClick, shapes = ButtonDefaults.shapes(), modifier = size, enabled = active, content = content)
        }

        ActionStyle.DESTRUCTIVE -> {
            Button(
                onClick,
                shapes = ButtonDefaults.shapes(),
                modifier = size,
                enabled = active,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                content = content,
            )
        }

        ActionStyle.DESTRUCTIVE_OUTLINED -> {
            OutlinedButton(
                onClick,
                shapes = ButtonDefaults.shapes(),
                modifier = size,
                enabled = active,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border =
                    BorderStroke(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    ),
                content = content,
            )
        }
    }
}

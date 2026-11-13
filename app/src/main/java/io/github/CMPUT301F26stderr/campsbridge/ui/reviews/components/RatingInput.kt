package io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.LocalControlScale

/** Reports explicit numeric star choices without owning or saving the selection.
 * @param value Current saved or tentative rating; null leaves every choice unselected.
 * @param onValueChange Requested rating from 1 to 5.
 * @param modifier Caller layout.
 * @param enabled Whether another explicit choice is permitted.
 */
@Composable
fun RatingInput(
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(modifier.selectableGroup()) {
        for (star in 1L..5L) {
            val label = stringResource(R.string.feedback_rate_value, star)
            val filled = value != null && star <= value
            Box(
                Modifier
                    .clip(CircleShape)
                    .selectable(
                        value == star,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onValueChange(star) },
                    ).semantics { contentDescription = label }
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        if (filled) {
                            rememberVectorPainter(
                                Icons.Default.Star,
                            )
                        } else {
                            painterResource(R.drawable.ic_star_border)
                        },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp * LocalControlScale.current),
                    tint =
                        when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            filled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/**
 * Pairs text with an icon so status never depends on color alone.
 * @param text Localized status.
 * @param icon Supplementary visual cue; adjacent text supplies its meaning.
 */
@Composable
fun StatusLabel(
    text: String,
    icon: ImageVector,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Icon(icon, contentDescription = null)
        Text(text)
    }
}

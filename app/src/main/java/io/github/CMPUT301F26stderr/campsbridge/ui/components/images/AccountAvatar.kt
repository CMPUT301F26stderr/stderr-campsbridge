package io.github.CMPUT301F26stderr.campsbridge.ui.components.images

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Circular account imagery using the existing loader and a native person fallback.
 * @param reference Transient photo reference, or null.
 * @param description Accessible account identity.
 * @param modifier Caller layout.
 * @param size Avatar diameter.
 */
@Composable
fun AccountAvatar(
    reference: String?,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (reference != null) {
        RemoteImage(
            reference,
            description,
            modifier.size(size).clip(CircleShape),
            compactFailure = true,
        )
    } else {
        Surface(
            modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, description) }
        }
    }
}

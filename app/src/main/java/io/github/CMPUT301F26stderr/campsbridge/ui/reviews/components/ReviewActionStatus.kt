/*
 * Announces action feedback without claiming it is an observed saved list row.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Announces action feedback without claiming it is an observed saved list row.
 * @param text Localized progress, failure or confirmed operation response.
 */
@Composable
fun ReviewActionStatus(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}

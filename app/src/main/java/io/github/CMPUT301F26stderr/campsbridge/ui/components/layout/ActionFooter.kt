package io.github.CMPUT301F26stderr.campsbridge.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Reachable white actions in AppScreen.bottomContent, with bounded overflow for large text.
 * The caller supplies bounded screen constraints; at most half is used by footer content.
 * @param modifier Caller layout.
 * @param content Current actions and feedback, without persistence or local success behavior.
 */
@Composable
fun ActionFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maximum = maxHeight / 2
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maximum)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                content = content,
            )
        }
    }
}

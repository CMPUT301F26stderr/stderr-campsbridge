/*
 * Checks list outcomes and long saved text at ordinary and enlarged configured scales.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Checks list outcomes and long saved text at ordinary and enlarged configured scales.
 * @param state Passive saved rows and independently resolved facts.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large message log", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun MessageLogPreview(
    @PreviewParameter(MessageLogPreviewProvider::class) state: MessageLogUiState,
) {
    CampsbridgeTheme(
        AccountSettings(
            controlScale =
                if (LocalDensity.current.fontScale >=
                    2f
                ) {
                    2.0
                } else {
                    1.0
                },
        ),
    ) { AppScreen(stringResource(R.string.admin_logs)) { MessageLogScreen(state, {}, {}, {}, {}) } }
}

/** Checks complete saved detail content and retained unavailable labels at both scales. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large message detail", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun MessageLogDetailPreview() {
    val row =
        MessageLogPreviewProvider()
            .values
            .first()
            .rows
            .first()
    CampsbridgeTheme(
        AccountSettings(
            controlScale =
                if (LocalDensity.current.fontScale >=
                    2f
                ) {
                    2.0
                } else {
                    1.0
                },
        ),
    ) { MessageLogDetailScreen(row, MessageLogRead.READY, row.message.id, {}, {}, {}) }
}

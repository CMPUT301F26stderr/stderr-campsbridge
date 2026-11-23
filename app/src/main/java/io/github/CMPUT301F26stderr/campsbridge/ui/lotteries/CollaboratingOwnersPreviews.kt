/*
 * Shows full-page owner selection and operation/source feedback at both agreed scales.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Shows full-page owner selection and operation/source feedback at both agreed scales.
 * @param state Current identities, local selection and explicit recovery feedback.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large owner picker", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun CollaboratingOwnersPreview(
    @PreviewParameter(CollaboratingOwnersPreviewProvider::class) state: CollaboratingOwnersUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        CollaboratingOwnersScreen(state, {}, {}, { _, _ -> }, {}, {})
    }
}

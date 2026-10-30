package io.github.CMPUT301F26stderr.campsbridge.ui.policies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.rows.NavigationRow
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Lists the two actual packaged readers; no help or unimplemented actions.
 * @param onOpen Opens the chosen document.
 * @param onBack Returns to the originating settings page.
 * @param modifier Caller layout.
 */
@Composable
fun PoliciesScreen(
    onOpen: (PolicyId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    AppScreen(stringResource(R.string.policies_title), modifier, onBack) {
        NavigationRow(
            stringResource(R.string.onboarding_guidelines),
            { onOpen(PolicyId.GUIDELINES) },
        )
        NavigationRow(stringResource(R.string.refund_policy_title), { onOpen(PolicyId.REFUND) })
    }
}

/** Reads supplied packaged content offline; unknown versions never show a newer substitute.
 * @param content Exact resolved version, or null for an unavailable version.
 * @param onBack Returns to the actual menu/wizard without saving or advancing it.
 * @param modifier Caller layout.
 */
@Composable
fun PolicyReaderScreen(
    content: PolicyContent?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    AppScreen(
        stringResource(content?.title ?: R.string.policy_unavailable_title),
        modifier,
        onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            PolicyDocumentContent(content)
        }
    }
}

/** Renders an exact packaged document inside its caller's existing scroll and screen host.
 * @param content Resolved version, or null to show explicit unavailable-history feedback.
 */
@Composable
fun PolicyDocumentContent(content: PolicyContent?) {
    if (content == null) {
        Text(stringResource(R.string.policy_unavailable_body))
    } else {
        for (section in content.sections) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(section.body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** Previews the policy menu using ordinary shared navigation rows. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PoliciesPreview() {
    CampsbridgeTheme { PoliciesScreen({}, {}) }
}

/** Previews the same full guidelines used by onboarding and settings. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun GuidelinesPreview() {
    CampsbridgeTheme { PolicyReaderScreen(policyContent(PolicyId.GUIDELINES), {}) }
}

/** Verifies the refund reader remains scrollable at enlarged system text size. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun LargeRefundPolicyPreview() {
    CampsbridgeTheme { PolicyReaderScreen(policyContent(PolicyId.REFUND), {}) }
}

/** Previews explicit unavailable-history feedback with a working caller-supplied Back action. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun UnavailablePolicyPreview() {
    CampsbridgeTheme { PolicyReaderScreen(null, {}) }
}

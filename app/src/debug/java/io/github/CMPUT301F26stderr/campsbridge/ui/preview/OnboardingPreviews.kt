package io.github.CMPUT301F26stderr.campsbridge.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Renders supplied fixture state with passive callbacks; never simulates a successful save.
 * @param step Step to inspect.
 * @param failed Whether to show an unsuccessful operation.
 * @param submitting Whether the operation is pending.
 * @param pending Whether name validation has not been integrated.
 * @param large Whether to apply enlarged account controls and text.
 */
@Composable
fun OnboardingPreviewContent(
    step: OnboardingStep,
    failed: Boolean = false,
    submitting: Boolean = false,
    pending: Boolean = false,
    large: Boolean = false,
) {
    CampsbridgeTheme(
        if (large) AccountSettings(fontScale = 1.25, controlScale = 1.5) else AccountSettings(),
    ) {
        OnboardingScreen(
            OnboardingUiState(
                step,
                OnboardingDraft(
                    "Alex Chen",
                    if (step ==
                        OnboardingStep.OWNER
                    ) {
                        Role.OWNER
                    } else {
                        Role.ENTRANT
                    },
                ),
                isLoading = false,
                isSubmitting = submitting,
                failed = failed,
                nameValidationPending = pending,
            ),
            {},
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

/** Approved welcome composition without live dependencies. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun WelcomePreview() {
    OnboardingPreviewContent(OnboardingStep.WELCOME)
}

/** Public intent choices and shared segmented progress. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun IntentPreview() {
    OnboardingPreviewContent(OnboardingStep.INTENT)
}

/** Name input with the explicit reserved implementation boundary. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PendingNamePreview() {
    OnboardingPreviewContent(OnboardingStep.NAME, pending = true)
}

/** Retained name input after a recoverable save failure. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FailedNamePreview() {
    OnboardingPreviewContent(OnboardingStep.NAME, failed = true)
}

/** Pending submission blocks repeated actions. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmittingNamePreview() {
    OnboardingPreviewContent(OnboardingStep.NAME, submitting = true)
}

/** Optional interests fit the same short-step frame. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InterestsPreview() {
    OnboardingPreviewContent(OnboardingStep.INTERESTS)
}

/** Owner introduction and the future campsite-creation boundary. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OwnerPreview() {
    OnboardingPreviewContent(OnboardingStep.OWNER)
}

/** Large system and account fonts retain scrollable content and reachable actions. */
@Preview(widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LargeInterestsPreview() {
    OnboardingPreviewContent(OnboardingStep.INTERESTS, large = true)
}

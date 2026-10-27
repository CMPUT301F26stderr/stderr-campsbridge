package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.OwnerIntroductionStep
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Existing-account hosting introduction, reusing onboarding's informational content.
 * @param state Observed profile and explicit activation outcome.
 * @param workspaceRequested Whether activation already completed and only selection remains.
 * @param selectingWorkspace Whether the root is confirming local workspace selection.
 * @param selectionFailed Whether the requested owner workspace selection failed.
 * @param onContinue Activates hosting or retries only the confirmed-role workspace selection.
 * @param onRetry Retries a failed account observation.
 * @param onBack Returns without revoking any already granted capability.
 * @param modifier Caller layout.
 * @param ownerWorkspaceAvailable Whether the root observer has also confirmed owner capability.
 */
@Composable
fun OwnerIntroductionScreen(
    state: ProfileUiState,
    workspaceRequested: Boolean,
    selectingWorkspace: Boolean,
    selectionFailed: Boolean,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    ownerWorkspaceAvailable: Boolean = true,
) {
    val content = state as? ProfileUiState.Content
    val activating = content?.operation == ProfileOperation.ENABLING_OWNER
    val busy = activating || selectingWorkspace
    BackHandler { if (!busy) onBack() }
    AppScreen(
        stringResource(R.string.onboarding_host_label),
        modifier,
        onBack = onBack,
        backEnabled = !busy,
        bottomContent = {
            if (content != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(
                            when {
                                workspaceRequested && selectionFailed -> {
                                    R.string.profile_retry_owner_workspace
                                }

                                content.ownerActivationFailed -> {
                                    R.string.profile_retry_hosting
                                }

                                else -> {
                                    R.string.onboarding_host_label
                                }
                            },
                        ),
                        onContinue,
                        Modifier.fillMaxWidth(),
                        enabled = !workspaceRequested || ownerWorkspaceAvailable,
                        isSubmitting = busy,
                        progressDescription =
                            stringResource(
                                if (selectingWorkspace) {
                                    R.string.profile_switching_owner
                                } else {
                                    R.string.profile_enabling_owner
                                },
                            ),
                    )
                }
            }
        },
    ) {
        ProfileStateContent(state, onRetry) { profile ->
            OwnerIntroductionStep()
            if (profile.ownerActivationFailed) {
                Text(
                    stringResource(R.string.profile_hosting_failure),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (workspaceRequested && selectionFailed) {
                Text(
                    stringResource(R.string.profile_owner_workspace_failure),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Shows the simple existing-account introduction before any write. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun OwnerIntroductionPreview() {
    CampsbridgeTheme {
        OwnerIntroductionScreen(
            ProfileUiState.Content(
                profilePreviewAccount(),
            ),
            false,
            false,
            false,
            {
            },
            {},
            {},
        )
    }
}

/** Shows a failed workspace selection without claiming activation must run again. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OwnerWorkspaceFailurePreview() {
    CampsbridgeTheme {
        OwnerIntroductionScreen(
            ProfileUiState.Content(
                profilePreviewAccount(),
            ),
            true,
            false,
            true,
            {
            },
            {},
            {},
        )
    }
}

/** Shows the guarded activation action using supplied state only. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OwnerActivationPreview() {
    CampsbridgeTheme {
        OwnerIntroductionScreen(
            ProfileUiState.Content(
                profilePreviewAccount(),
                operation = ProfileOperation.ENABLING_OWNER,
            ),
            false,
            false,
            false,
            {
            },
            {},
            {},
        )
    }
}

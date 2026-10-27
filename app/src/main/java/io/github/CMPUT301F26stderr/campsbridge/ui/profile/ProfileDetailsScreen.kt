package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.ImageSlot
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Personal editor with separate photo actions and explicit name saving.
 * @param state Account/editor state supplied by the owner.
 * @param onNameChange Unsaved text edit.
 * @param onSave Explicit name submission.
 * @param onBack Cancel/discard request supplied by the route.
 * @param onRetry Failed account read retry.
 * @param modifier Parent layout.
 * @param photoBusy Whether image mutation or reconciliation prevents leaving/saving.
 * @param photoContent Shared photo UI connected by the route.
 */
@Composable
fun ProfileDetailsScreen(
    state: ProfileUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    photoBusy: Boolean = false,
    photoContent: @Composable () -> Unit = {},
) {
    val content = state as? ProfileUiState.Content
    val saving = content?.operation == ProfileOperation.SAVING_NAME
    val canExit =
        (
            content == null ||
                (content.operation == ProfileOperation.IDLE && content.completedEditor == null)
        ) &&
            !photoBusy
    BackHandler { if (canExit) onBack() }
    AppScreen(
        stringResource(R.string.personal_details),
        modifier,
        onBack,
        backEnabled = canExit,
        bottomContent = {
            if (content?.personalDraft != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.save_name),
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled =
                            content.operation == ProfileOperation.IDLE &&
                                !content.nameValidationPending &&
                                !photoBusy,
                        isSubmitting = saving,
                        progressDescription = stringResource(R.string.profile_saving),
                    )
                }
            }
        },
    ) {
        ProfileStateContent(state, onRetry) { current ->
            photoContent()
            current.personalDraft?.let { draft ->
                AppTextField(
                    draft.nameText,
                    onNameChange,
                    stringResource(R.string.onboarding_name),
                    enabled = current.operation == ProfileOperation.IDLE,
                    errorMessage =
                        if (current.nameError) {
                            stringResource(
                                R.string.onboarding_name_required,
                            )
                        } else {
                            null
                        },
                )
            }
            if (current.nameValidationPending) {
                Text(
                    stringResource(R.string.name_validation_pending),
                )
            }
            if (current.saveFailed) Text(stringResource(R.string.onboarding_save_failure))
        }
    }
}

/** Previews the ordinary editor using real theme/components and no persistence. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PersonalEditorPreview() {
    PersonalEditorPreviewContent(
        ProfileUiState.Content(
            profilePreviewAccount(),
            personalDraft = PersonalDetailsDraft("Alex Chen"),
        ),
    )
}

/** Previews retained input and validation feedback at enlarged system text size. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun FailedPersonalEditorPreview() {
    PersonalEditorPreviewContent(
        ProfileUiState.Content(
            profilePreviewAccount(),
            personalDraft = PersonalDetailsDraft("Alexandra Chen Montgomery"),
            saveFailed = true,
        ),
    )
}

/** Previews a confirmed write in progress, including disabled Back and submit. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SavingPersonalEditorPreview() {
    PersonalEditorPreviewContent(
        ProfileUiState.Content(
            profilePreviewAccount(),
            personalDraft = PersonalDetailsDraft("Alex Chen"),
            operation = ProfileOperation.SAVING_NAME,
        ),
    )
}

/** Uses the real shared image slot in static editor previews without IO or fake persistence.
 * @param state Supplied editor scenario.
 */
@Composable
private fun PersonalEditorPreviewContent(state: ProfileUiState.Content) {
    CampsbridgeTheme {
        ProfileDetailsScreen(state, {}, {}, {}, {}) {
            ImageSlot(
                ImageSlotState(loadState = ImageLoadState.READY),
                {},
                {},
                {},
                {},
                {},
                stringResource(R.string.profile_photo),
                Icons.Default.AccountCircle,
                imageShape = CircleShape,
                enabled = state.operation == ProfileOperation.IDLE,
            )
        }
    }
}

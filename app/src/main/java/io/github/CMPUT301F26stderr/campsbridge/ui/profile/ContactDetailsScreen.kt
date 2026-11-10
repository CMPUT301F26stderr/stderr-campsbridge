/*
 * Optional contacts editor; content does not infer login or contact ownership.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.LocalValidationAttempt
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Optional contacts editor; content does not infer login or contact ownership.
 * @param state Account/editor read outcome.
 * @param onEmailChange Unsaved email edit.
 * @param onPhoneChange Unsaved phone edit.
 * @param onSave Explicit contact submission.
 * @param onBack Cancel/discard request.
 * @param onRetry Account observation retry.
 * @param modifier Parent layout.
 */
@Composable
fun ContactDetailsScreen(
    state: ProfileUiState,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var validationAttempt by remember { mutableIntStateOf(0) }
    val content = state as? ProfileUiState.Content
    val saving = content?.operation == ProfileOperation.SAVING_CONTACTS
    val canExit =
        content == null ||
            (content.operation == ProfileOperation.IDLE && content.completedEditor == null)
    AppScreen(
        stringResource(R.string.contact_details),
        modifier,
        onBack,
        backEnabled = canExit,
        bottomContent = {
            if (content?.contactDraft != null) {
                ActionFooter {
                    AppActionButton(
                        stringResource(R.string.save_contacts),
                        {
                            validationAttempt++
                            focusManager.clearFocus()
                            onSave()
                        },
                        Modifier.fillMaxWidth(),
                        enabled =
                            content.operation == ProfileOperation.IDLE &&
                                !content.contactValidationPending,
                        isSubmitting = saving,
                        progressDescription = stringResource(R.string.profile_saving),
                    )
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalValidationAttempt provides validationAttempt) {
            ProfileStateContent(state, onRetry) { current ->
                current.contactDraft?.let { draft ->
                    AppTextField(
                        draft.emailText,
                        onEmailChange,
                        stringResource(R.string.email),
                        enabled = current.operation == ProfileOperation.IDLE,
                        supportingText = stringResource(R.string.contact_optional),
                        errorMessage =
                            if (current.emailError) {
                                stringResource(
                                    R.string.email_invalid,
                                )
                            } else {
                                null
                            },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        revealError = current.emailError,
                    )
                    AppTextField(
                        draft.phoneText,
                        onPhoneChange,
                        stringResource(R.string.phone),
                        enabled = current.operation == ProfileOperation.IDLE,
                        supportingText = stringResource(R.string.contact_optional),
                        errorMessage =
                            if (current.phoneError) {
                                stringResource(
                                    R.string.phone_invalid,
                                )
                            } else {
                                null
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        revealError = current.phoneError && !current.emailError,
                    )
                }
                if (current.contactValidationPending) {
                    Text(
                        stringResource(R.string.contact_validation_pending),
                    )
                }
                if (current.saveFailed) Text(stringResource(R.string.onboarding_save_failure))
            }
        }
    }
}

/** Previews valid optional fields without an injected parser or repository. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ContactEditorPreview() {
    CampsbridgeTheme {
        ContactDetailsScreen(
            ProfileUiState.Content(
                profilePreviewAccount(),
                contactDraft = ContactDetailsDraft("alex@example.com", ""),
            ),
            {
            },
            {},
            {},
            {},
            {},
        )
    }
}

/** Previews independent field errors and retained long input. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2f)
@Composable
private fun InvalidContactEditorPreview() {
    CampsbridgeTheme {
        ContactDetailsScreen(
            ProfileUiState.Content(
                profilePreviewAccount(),
                contactDraft =
                    ContactDetailsDraft(
                        "invalid email example",
                        "invalid phone example",
                    ),
                emailError = true,
                phoneError = true,
            ),
            {
            },
            {},
            {},
            {},
            {},
        )
    }
}

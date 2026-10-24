package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.app.UnavailableAccountScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.InlineNotice
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.StepScaffold
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.IntentStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.InterestsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.NameStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.OwnerIntroductionStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.steps.WelcomeStep

/** Arranges short onboarding steps from supplied state, without persistence or navigation lookup.
 * @param state Current draft and operation state.
 * @param onNext Continue or retry the visible save.
 * @param onBack Return within the flow.
 * @param onIntent Select a public starting workspace.
 * @param onName Edit display-name input.
 * @param onPreferences Replace optional draft interests.
 * @param onSkip Finish without saving optional interests.
 * @param onRetryLoad Retry a failed initial read.
 * @param modifier Caller layout.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onIntent: (Role) -> Unit,
    onName: (String) -> Unit,
    onPreferences: (Preferences) -> Unit,
    onSkip: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var guidelines by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME && !state.unavailable) {
        if (!state.isSubmitting) onBack()
    }
    when {
        state.unavailable -> {
            UnavailableAccountScreen()
        }

        state.isLoading || state.completed -> {
            AppScreen(stringResource(R.string.app_name), modifier) {
                LoadingContent(
                    stringResource(
                        if (state.completed) {
                            R.string.onboarding_finishing
                        } else {
                            R.string.onboarding_load
                        },
                    ),
                )
            }
        }

        state.failed && state.step == OnboardingStep.WELCOME -> {
            AppScreen(stringResource(R.string.app_name), modifier) {
                FailureContent(stringResource(R.string.onboarding_load_failure), onRetryLoad)
            }
        }

        state.step == OnboardingStep.WELCOME -> {
            WelcomeStep(onNext, { guidelines = true }, modifier)
        }

        else -> {
            val position =
                when (state.step) {
                    OnboardingStep.INTENT -> 1
                    OnboardingStep.NAME -> 2
                    else -> 3
                }
            val label =
                when (state.step) {
                    OnboardingStep.INTENT -> R.string.onboarding_intent_label
                    OnboardingStep.NAME -> R.string.onboarding_name_label
                    OnboardingStep.INTERESTS -> R.string.onboarding_interests_label
                    else -> R.string.onboarding_host_label
                }
            val title =
                when (state.step) {
                    OnboardingStep.INTENT -> R.string.onboarding_intent_title
                    OnboardingStep.NAME -> R.string.onboarding_name_title
                    OnboardingStep.INTERESTS -> R.string.onboarding_interests_title
                    else -> R.string.onboarding_host_title
                }
            val nextLabel =
                when (state.step) {
                    OnboardingStep.INTENT -> R.string.next
                    OnboardingStep.NAME -> R.string.onboarding_continue
                    OnboardingStep.INTERESTS -> R.string.onboarding_finish
                    else -> R.string.onboarding_setup_later
                }
            val nextEnabled =
                when (state.step) {
                    OnboardingStep.INTENT -> state.draft.intent != null
                    OnboardingStep.NAME -> !state.nameValidationPending
                    else -> true
                }
            StepScaffold(
                title = stringResource(R.string.app_name),
                progress = stringResource(R.string.onboarding_step, position, 3),
                onBack = onBack,
                onNext = onNext,
                modifier = modifier,
                nextEnabled = nextEnabled,
                isSubmitting = state.isSubmitting,
                nextLabel = stringResource(nextLabel),
                steps = StepProgress(position, 3),
                onSkip = if (state.step == OnboardingStep.INTERESTS) onSkip else null,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
                when (state.step) {
                    OnboardingStep.INTENT -> {
                        IntentStep(state.draft.intent, onIntent, !state.isSubmitting)
                    }

                    OnboardingStep.NAME -> {
                        NameStep(
                            state.draft.name,
                            onName,
                            state.nameRequired,
                            !state.isSubmitting,
                            { if (nextEnabled && !state.isSubmitting) onNext() },
                        )
                    }

                    OnboardingStep.INTERESTS -> {
                        InterestsStep(state.draft.preferences, onPreferences, !state.isSubmitting)
                    }

                    OnboardingStep.OWNER -> {
                        OwnerIntroductionStep()
                    }

                    OnboardingStep.WELCOME -> {
                        Unit
                    }
                }
                if (state.step == OnboardingStep.NAME && state.nameValidationPending) {
                    InlineNotice(
                        stringResource(R.string.setup_title),
                        stringResource(R.string.onboarding_name_pending),
                    )
                }
                if (state.failed) {
                    Text(
                        stringResource(R.string.onboarding_save_failure),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (guidelines) {
        AlertDialog(
            onDismissRequest = { guidelines = false },
            title = { Text(stringResource(R.string.onboarding_guidelines)) },
            text = {
                Text(
                    stringResource(R.string.onboarding_guidelines_body),
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    { guidelines = false },
                ) { Text(stringResource(R.string.onboarding_close)) }
            },
        )
    }
}

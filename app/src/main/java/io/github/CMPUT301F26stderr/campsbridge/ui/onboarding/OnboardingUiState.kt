package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role

/** Steps belonging to one onboarding flow, rather than separate workspace destinations. */
enum class OnboardingStep { WELCOME, INTENT, NAME, INTERESTS, OWNER }

/** Editable input retained across steps and saved-state restoration.
 * @param name Unsubmitted display-name input.
 * @param intent Starting workspace choice; never grants a capability.
 * @param preferences Optional camping interests.
 */
data class OnboardingDraft(
    val name: String = "",
    val intent: Role? = null,
    val preferences: Preferences = Preferences(),
)

/** Presentation of the onboarding flow and its explicit operation outcomes.
 * @param step Current short task.
 * @param draft Retained input.
 * @param isLoading Whether existing setup is being restored.
 * @param isSubmitting Whether an action is awaiting persistence.
 * @param nameRequired Whether the name failed required-field validation.
 * @param failed Whether the last load or save failed and can be retried.
 * @param unavailable Whether the account is removed.
 * @param completed Whether required setup writes have succeeded.
 * @param nameValidationPending Whether the reserved parser has not been integrated yet.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val draft: OnboardingDraft = OnboardingDraft(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val nameRequired: Boolean = false,
    val failed: Boolean = false,
    val unavailable: Boolean = false,
    val completed: Boolean = false,
    val nameValidationPending: Boolean = false,
)

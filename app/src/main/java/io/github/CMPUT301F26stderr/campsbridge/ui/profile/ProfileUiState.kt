package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account

/** Existing-profile presentation outcomes, independent of navigation and SDK clients. */
sealed interface ProfileUiState {
    /** Account observation is pending. */
    data object Loading : ProfileUiState

    /**
     * Active profile data.
     * @param account Current shared account.
     * @param personalDraft Unsaved name, if its editor has started.
     * @param contactDraft Unsaved optional contacts, if their editor has started.
     * @param operation Pending profile-field write.
     * @param nameError Whether the submitted name was empty.
     * @param emailError Whether submitted email was invalid.
     * @param phoneError Whether submitted phone was invalid.
     * @param saveFailed Whether an explicit field save failed without losing input.
     * @param nameValidationPending Whether the reserved name parser is unavailable.
     * @param contactValidationPending Whether either reserved contact parser is unavailable.
     * @param completedEditor Confirmed save awaiting acknowledgement by its active route.
     */
    data class Content(
        val account: Account,
        val personalDraft: PersonalDetailsDraft? = null,
        val contactDraft: ContactDetailsDraft? = null,
        val operation: ProfileOperation = ProfileOperation.IDLE,
        val nameError: Boolean = false,
        val emailError: Boolean = false,
        val phoneError: Boolean = false,
        val saveFailed: Boolean = false,
        val nameValidationPending: Boolean = false,
        val contactValidationPending: Boolean = false,
        val completedEditor: ProfileEditor? = null,
    ) : ProfileUiState

    /** Account is absent or removed. */
    data object Unavailable : ProfileUiState

    /** Observation failed and can be retried. */
    data object Failure : ProfileUiState
}

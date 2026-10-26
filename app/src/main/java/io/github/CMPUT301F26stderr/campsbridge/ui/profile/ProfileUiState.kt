package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account

/** Existing-profile presentation outcomes, independent of navigation and SDK clients. */
sealed interface ProfileUiState {
    /** Account observation is pending. */
    data object Loading : ProfileUiState

    /**
     * Active profile data.
     * @param account Current shared account.
     */
    data class Content(
        val account: Account,
    ) : ProfileUiState

    /** Account is absent or removed. */
    data object Unavailable : ProfileUiState

    /** Observation failed and can be retried. */
    data object Failure : ProfileUiState
}

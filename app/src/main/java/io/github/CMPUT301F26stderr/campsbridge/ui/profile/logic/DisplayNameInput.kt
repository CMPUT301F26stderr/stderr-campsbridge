package io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic

/** Result contract for the reserved display-name parsing task. */
sealed interface DisplayNameInput {
    /** Empty input after trimming must show a required-field error. */
    data object Required : DisplayNameInput

    /** A nonblank trimmed name, retaining internal whitespace, spelling and case.
     * @param name Normalized display name to persist.
     */
    data class Valid(
        val name: String,
    ) : DisplayNameInput
}

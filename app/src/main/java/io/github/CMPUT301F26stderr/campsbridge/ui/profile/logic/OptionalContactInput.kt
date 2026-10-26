package io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic

/** Result contract for the reserved optional-email and optional-phone parsers. */
sealed interface OptionalContactInput {
    /** Accepted contact spelling, or a deliberate cleared optional field.
     * @param value Trimmed contact, or null for blank input.
     */
    data class Valid(
        val value: String?,
    ) : OptionalContactInput

    /** Nonblank input violates the field's lightweight format contract. */
    data object Invalid : OptionalContactInput
}

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

/** Reserved F02 email parser; this unfinished handoff body currently rejects all input.
 * @param text Raw optional email; the task contract defines trimming and accepted input.
 * @return Task implementation must distinguish a cleared, accepted or invalid field.
 */
fun parseOptionalEmail(text: String): OptionalContactInput =
    OptionalContactInput.Invalid

/** Reserved F02 phone parser; this unfinished handoff body currently rejects all input.
 * @param text Raw optional phone; the task contract defines trimming and accepted characters.
 * @return Task implementation must distinguish a cleared, accepted or invalid field.
 */
fun parseOptionalPhone(text: String): OptionalContactInput =
    OptionalContactInput.Invalid

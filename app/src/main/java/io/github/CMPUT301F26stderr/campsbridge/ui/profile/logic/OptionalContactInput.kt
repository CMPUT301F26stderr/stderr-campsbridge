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

/** Parses an optional email, preserving accepted spelling after trimming surrounding whitespace.
 * @param text Raw optional email input.
 * @return Valid(null) for blank input, Valid with a single whitespace-free address, or Invalid.
 */
fun parseOptionalEmail(text: String): OptionalContactInput {
    val value = text.trim()
    if (value.isEmpty()) return OptionalContactInput.Valid(null)
    val separator = value.indexOf('@')
    val invalid =
        separator <= 0 ||
            separator == value.lastIndex ||
            separator != value.lastIndexOf('@') ||
            value.any { it.isWhitespace() }
    return if (!invalid) {
        OptionalContactInput.Valid(value)
    } else {
        OptionalContactInput.Invalid
    }
}

/** Parses an optional phone without changing accepted digits, spaces or punctuation.
 * @param text Raw optional phone input.
 * @return Valid(null) for blank input, Valid for permitted text containing an ASCII digit, or Invalid.
 */
fun parseOptionalPhone(text: String): OptionalContactInput {
    val value = text.trim()
    if (value.isEmpty()) return OptionalContactInput.Valid(null)
    return if (Regex("""\+?[0-9 ()-]*[0-9][0-9 ()-]*""").matches(value)) {
        OptionalContactInput.Valid(value)
    } else {
        OptionalContactInput.Invalid
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.common.input

/** Parsed whole-count value or field feedback. */
sealed interface PositiveCountInput {
    /** Accepted count.
     * @param count Positive count within the configured limit. */
    data class Value(
        val count: Long,
    ) : PositiveCountInput

    /** User-correctable reasons a whole count was not accepted. */
    enum class Error : PositiveCountInput { REQUIRED, INVALID_WHOLE_NUMBER, EXCEEDS_MAXIMUM }
}

/**
 * Parses trimmed ASCII digits into a count from one through the configured maximum.
 * @param text Editable field text.
 * @param maximum Positive inclusive upper bound; a nonpositive bound is a programming error.
 */
fun parsePositiveCount(
    text: String,
    maximum: Long,
): PositiveCountInput {
    require(maximum > 0)
    val input = text.trim()
    if (input.isEmpty()) return PositiveCountInput.Error.REQUIRED
    if (input.any { it !in '0'..'9' }) return PositiveCountInput.Error.INVALID_WHOLE_NUMBER
    val count = input.toLongOrNull() ?: return PositiveCountInput.Error.EXCEEDS_MAXIMUM
    if (count == 0L) return PositiveCountInput.Error.INVALID_WHOLE_NUMBER
    if (count > maximum) return PositiveCountInput.Error.EXCEEDS_MAXIMUM
    return PositiveCountInput.Value(count)
}

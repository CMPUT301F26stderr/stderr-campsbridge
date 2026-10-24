package io.github.CMPUT301F26stderr.campsbridge.ui.common.input

import java.math.BigDecimal

private val priceFormat = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

/** Exact CAD cents or field feedback. */
sealed interface PriceInput {
    /** Accepted amount.
     * @param cents Nonnegative CAD cents. */
    data class Value(
        val cents: Long,
    ) : PriceInput

    /** User-correctable reasons an amount was not accepted. */
    enum class Error : PriceInput { REQUIRED, INVALID_FORMAT, TOO_LARGE }
}

/**
 * Parses a nonnegative whole amount with an optional one/two-digit fraction into exact cents.
 * @param text Editable field text, with optional surrounding whitespace.
 */
fun parsePriceCents(text: String): PriceInput {
    val input = text.trim()
    if (input.isEmpty()) return PriceInput.Error.REQUIRED
    if (!priceFormat.matches(input)) return PriceInput.Error.INVALID_FORMAT
    return try {
        PriceInput.Value(BigDecimal(input).movePointRight(2).longValueExact())
    } catch (_: ArithmeticException) {
        PriceInput.Error.TOO_LARGE
    }
}

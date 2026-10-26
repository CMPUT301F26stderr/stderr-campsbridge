package io.github.CMPUT301F26stderr.campsbridge.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Test

/** Exact CAD parsing contracts without floating-point conversions. */
class PriceInputTest {
    /** Optional fractional digits, zeros and exact Long-cent bounds are preserved. */
    @Test fun acceptsExactAmounts() {
        mapOf(
            "0" to 0L,
            "0.00" to 0L,
            "12" to 1200L,
            "12.3" to 1230L,
            "12.34" to 1234L,
            "  0012.03  " to 1203L,
            "92233720368547758.07" to Long.MAX_VALUE,
        ).forEach { (text, cents) ->
            assertEquals(text, PriceInput.Value(cents), parsePriceCents(text))
        }
    }

    /** Malformed and overflowing input produces feedback rather than exceptions. */
    @Test fun classifiesInvalidAmounts() {
        listOf("", " \n ").forEach { assertEquals(PriceInput.Error.REQUIRED, parsePriceCents(it)) }
        listOf("-1", "+1", ".5", "1.", "1.234", "1,000", "1e2", "١٢", "1 2", "NaN").forEach {
            assertEquals(it, PriceInput.Error.INVALID_FORMAT, parsePriceCents(it))
        }
        listOf("92233720368547758.08", "999999999999999999999999999999999").forEach {
            assertEquals(it, PriceInput.Error.TOO_LARGE, parsePriceCents(it))
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Whole-count input contracts independent of a form or repository. */
class PositiveCountInputTest {
    /** Valid counts are normalized and retain both inclusive boundaries. */
    @Test fun acceptsWholeCounts() {
        listOf("1" to 1L, "8" to 8L, "  0002  " to 2L).forEach { (text, count) ->
            assertEquals(text, PositiveCountInput.Value(count), parsePositiveCount(text, 8))
        }
        assertEquals(
            PositiveCountInput.Value(Long.MAX_VALUE),
            parsePositiveCount(Long.MAX_VALUE.toString(), Long.MAX_VALUE),
        )
    }

    /** User errors have a stable classification and never throw. */
    @Test fun classifiesInvalidCounts() {
        val errors =
            mapOf(
                "" to PositiveCountInput.Error.REQUIRED,
                " \n " to PositiveCountInput.Error.REQUIRED,
                "0" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "000" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "-1" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "+1" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "1.0" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "1 0" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "a" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "١" to PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                "9" to PositiveCountInput.Error.EXCEEDS_MAXIMUM,
                "99999999999999999999999999999" to PositiveCountInput.Error.EXCEEDS_MAXIMUM,
            )
        errors.forEach { (text, error) -> assertEquals(text, error, parsePositiveCount(text, 8)) }
    }

    /** An invalid configured limit is a programming error even when input is empty. */
    @Test fun rejectsInvalidMaximumBeforeReadingInput() {
        listOf(0L, -1L).forEach { maximum ->
            listOf("", "1").forEach { text ->
                assertThrows(
                    IllegalArgumentException::class.java,
                ) { parsePositiveCount(text, maximum) }
            }
        }
    }
}

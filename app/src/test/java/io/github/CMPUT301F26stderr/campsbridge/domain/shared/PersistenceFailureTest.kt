package io.github.CMPUT301F26stderr.campsbridge.domain.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Covers the existing typed invariant guard used by repository operations. */
class PersistenceFailureTest {
    /** A satisfied invariant does not produce a failure. */
    @Test fun satisfiedInvariantReturnsNormally() {
        requireOperation(true, PersistenceFailure.Reason.CONFLICT, "Already booked")
    }

    /** A rejected invariant preserves its public reason and diagnostic detail. */
    @Test fun failedInvariantRetainsReasonAndMessage() {
        val failure =
            assertThrows(PersistenceFailure::class.java) {
                requireOperation(false, PersistenceFailure.Reason.CONFLICT, "Already booked")
            }
        assertEquals(PersistenceFailure.Reason.CONFLICT, failure.reason)
        assertEquals("Already booked", failure.message)
    }
}

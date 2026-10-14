package io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Asserts a domain rejection while allowing unexpected infrastructure failures to escape unchanged.
 *
 * @param reason Expected business failure category.
 * @param action Repository operation under test.
 */
suspend fun assertPersistenceFailure(
    reason: PersistenceFailure.Reason,
    action: suspend () -> Unit,
) {
    try {
        action()
        fail("Expected persistence failure: $reason")
    } catch (failure: PersistenceFailure) {
        assertEquals(reason, failure.reason)
    }
}

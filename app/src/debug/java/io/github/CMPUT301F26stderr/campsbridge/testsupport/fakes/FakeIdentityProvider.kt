package io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes

import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import kotlinx.coroutines.CompletableDeferred

/** Controlled identity dependency for isolated debug hosts and tests. */
class FakeIdentityProvider : IdentityProvider {
    var calls = 0
    var failure: Exception? = null
    var pending: CompletableDeferred<String>? = null

    /** Returns the controlled UID or failure without starting an SDK operation. */
    override suspend fun establishIdentity(): String {
        calls++
        failure?.let { throw it }
        return pending?.await() ?: "account-1"
    }
}

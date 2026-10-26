package io.github.CMPUT301F26stderr.campsbridge.platform.identity

/** Establishes one device-linked Firebase identity without creating a profile. */
interface IdentityProvider {
    /** Returns the restored or newly established account ID; propagates failure/cancellation. */
    suspend fun establishIdentity(): String
}

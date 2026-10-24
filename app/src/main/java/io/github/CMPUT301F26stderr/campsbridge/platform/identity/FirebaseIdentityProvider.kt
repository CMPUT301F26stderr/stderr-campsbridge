package io.github.CMPUT301F26stderr.campsbridge.platform.identity

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Restores or establishes one anonymous SDK session; never creates an account document.
 * @param auth Auth client configured for the same project/environment as the repositories.
 */
class FirebaseIdentityProvider(
    private val auth: FirebaseAuth,
) : IdentityProvider {
    private val mutex = Mutex()
    private var pending: Task<AuthResult>? = null

    /**
     * Shares sign-in between callers. Cancelling a waiter does not cancel the SDK task.
     * Failed tasks can be retried; no placeholder UID is returned.
     */
    override suspend fun establishIdentity(): String {
        val task =
            mutex.withLock {
                val current = auth.currentUser
                if (current != null) return current.uid
                val existing = pending
                if (existing != null && !existing.isComplete) {
                    existing
                } else {
                    auth.signInAnonymously().also { pending = it }
                }
            }
        return checkNotNull(task.await().user) { "Identity result did not contain a user" }.uid
    }
}

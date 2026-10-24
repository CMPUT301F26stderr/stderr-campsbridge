package io.github.CMPUT301F26stderr.campsbridge.platform.identity

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** SDK-boundary identity contracts, without connecting to Firebase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseIdentityProviderTest {
    /** Existing identity is reused without starting another sign-in. */
    @Test fun cachedIdentityIsReused() =
        runTest {
            val auth = mock(FirebaseAuth::class.java)
            val user = mock(FirebaseUser::class.java)
            `when`(user.uid).thenReturn("existing")
            `when`(auth.currentUser).thenReturn(user)
            assertEquals("existing", FirebaseIdentityProvider(auth).establishIdentity())
            verify(auth, never()).signInAnonymously()
        }

    /** Concurrent callers and a cancelled waiter share the SDK operation. */
    @Test fun cancellationDoesNotCreateCompetingIdentity() =
        runTest {
            val auth = mock(FirebaseAuth::class.java)
            val task = TaskCompletionSource<AuthResult>()
            `when`(auth.signInAnonymously()).thenReturn(task.task)
            val provider = FirebaseIdentityProvider(auth)
            val first = async { provider.establishIdentity() }
            val second = async { provider.establishIdentity() }
            runCurrent()
            first.cancelAndJoin()
            val third = async { provider.establishIdentity() }
            runCurrent()
            val user = mock(FirebaseUser::class.java)
            val result = mock(AuthResult::class.java)
            `when`(user.uid).thenReturn("actual-uid")
            `when`(result.user).thenReturn(user)
            task.setResult(result)
            runCurrent()
            assertEquals("actual-uid", second.await())
            assertEquals("actual-uid", third.await())
            verify(auth, times(1)).signInAnonymously()
        }

    /** A failed operation propagates its failure and a later call can retry. */
    @Test fun failureDoesNotInventIdentity() =
        runTest {
            val auth = mock(FirebaseAuth::class.java)
            val failed =
                TaskCompletionSource<AuthResult>().apply {
                    setException(
                        IOException("offline"),
                    )
                }
            val success = TaskCompletionSource<AuthResult>()
            `when`(auth.signInAnonymously()).thenReturn(failed.task, success.task)
            val provider = FirebaseIdentityProvider(auth)
            try {
                provider.establishIdentity()
                fail("Expected identity failure")
            } catch (expected: IOException) {
                assertEquals("offline", expected.message)
            }
            val retry = async { provider.establishIdentity() }
            runCurrent()
            val user = mock(FirebaseUser::class.java)
            val result = mock(AuthResult::class.java)
            `when`(user.uid).thenReturn("retried")
            `when`(result.user).thenReturn(user)
            success.setResult(result)
            runCurrent()
            assertEquals("retried", retry.await())
            verify(auth, times(2)).signInAnonymously()
        }
}

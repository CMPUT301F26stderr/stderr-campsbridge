package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.firestore

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentDecodingException
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Owns shared missing-data, observed-decoding and transaction-failure behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class FirestoreAccessTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Absence is represented as null or an empty collection, without inventing a failure. */
    @Test(timeout = 60000)
    fun absenceIsDifferentFromFailure() =
        runBlocking {
            val accounts = firebase.actor().container.accounts
            assertNull(accounts.get("missing"))
            withTimeout(10000) {
                assertNull(accounts.observe("missing").first())
                assertTrue(accounts.observeAll().first().isEmpty())
            }
        }

    /** Malformed incoming data terminates an established observation with decoding context. */
    @Test(timeout = 60000)
    fun malformedSnapshotTerminatesObservation() =
        runBlocking {
            val actor = firebase.actor()
            val account =
                actor.container.accounts.create(
                    actor.id,
                    ProfileUpdate("Camper", null, null),
                )
            val initial = CompletableDeferred<Unit>()
            val observation =
                async {
                    runCatching {
                        actor.container.accounts
                            .observe(actor.id)
                            .collect { initial.complete(Unit) }
                    }
                }
            withTimeout(10000) { initial.await() }
            firebase.seed(
                "accounts/${actor.id}",
                AccountDocument.encode(account) + ("settings" to "invalid"),
            )
            val failure = withTimeout(10000) { observation.await().exceptionOrNull() }
            assertTrue(failure is DocumentDecodingException)
            assertEquals(
                "accounts/${actor.id}",
                (failure as DocumentDecodingException).documentPath,
            )
        }

    /** Firebase transaction wrappers preserve the domain's typed failure category. */
    @Test(timeout = 60000)
    fun transactionFailureRetainsDomainReason() =
        runBlocking {
            val actor = firebase.actor()
            val failure =
                runCatching {
                    actor.container.accounts.updateProfile(
                        actor.id,
                        ProfileUpdate("Camper", null, null),
                    )
                }.exceptionOrNull()
            assertTrue(failure is PersistenceFailure)
            assertEquals(
                PersistenceFailure.Reason.NOT_FOUND,
                (failure as PersistenceFailure).reason,
            )
        }
}

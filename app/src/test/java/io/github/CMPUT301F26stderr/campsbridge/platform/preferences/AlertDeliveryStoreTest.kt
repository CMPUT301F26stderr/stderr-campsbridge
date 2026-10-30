package io.github.CMPUT301F26stderr.campsbridge.platform.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Four fixed contracts using real temporary Preferences files and isolated store lifetimes. */
class AlertDeliveryStoreTest {
    @get:Rule val files = TemporaryFolder()

    /** Fresh accounts are empty; markers and returned snapshots stay isolated by account. */
    @Test fun freshHistoryAndMarkedAccountRemainIndependent() =
        runBlocking {
            withStore { data ->
                val store = DataStoreAlertDeliveryStore(data)
                val before = store.handledIds("one")
                assertEquals(emptySet<String>(), before)
                store.markHandled("one", "event/α")
                assertEquals(setOf("event/α"), store.handledIds("one"))
                assertEquals(emptySet<String>(), store.handledIds("two"))
                assertEquals(emptySet<String>(), before)
            }
        }

    /** Repeated marks are idempotent and survive reopening with separate account histories. */
    @Test fun repeatedMarksSurviveReopeningPerAccount() =
        runBlocking {
            withStore { data ->
                val store = DataStoreAlertDeliveryStore(data)
                store.markHandled("one", "first")
                store.markHandled("one", "second")
                store.markHandled("one", "first")
                store.markHandled("two", "first")
            }
            withStore { data ->
                val store = DataStoreAlertDeliveryStore(data)
                assertEquals(setOf("first", "second"), store.handledIds("one"))
                assertEquals(setOf("first"), store.handledIds("two"))
                store.markHandled("two", "third")
                assertEquals(setOf("first", "third"), store.handledIds("two"))
                assertEquals(setOf("first", "second"), store.handledIds("one"))
            }
        }

    /** Concurrent additions merge without overwriting ordinary setup/workspace preferences. */
    @Test fun concurrentMarksPreserveAllEventsAndUnrelatedPreferences() =
        runBlocking {
            withStore { data ->
                val progress = DataStoreAppPreferences(data)
                progress.setSetupProgress("one", SetupProgress.COMPLETED)
                progress.setWorkspace("one", Role.OWNER)
                val store = DataStoreAlertDeliveryStore(data)
                coroutineScope {
                    (1..20)
                        .map { index ->
                            launch { store.markHandled("one", "event-$index") }
                        }.joinAll()
                }
                assertEquals((1..20).map { "event-$it" }.toSet(), store.handledIds("one"))
                assertEquals(
                    LocalAccountProgress(SetupProgress.COMPLETED, Role.OWNER),
                    progress.observe("one").first(),
                )
            }
        }

    /** Corrupt storage fails both operations instead of inventing empty or acknowledged state. */
    @Test fun corruptFileFailsReadsAndMarks() =
        runBlocking {
            File(files.root, "broken.preferences_pb").writeBytes(byteArrayOf(0, 1, 2, 3))
            withStore("broken.preferences_pb") { data ->
                val store = DataStoreAlertDeliveryStore(data)
                assertTrue(runCatching { store.handledIds("one") }.exceptionOrNull() is IOException)
                assertTrue(
                    runCatching {
                        store.markHandled(
                            "one",
                            "first",
                        )
                    }.exceptionOrNull() is IOException,
                )
            }
        }

    /** Closes the sole active store scope before any subsequent reopen of its file.
     * @param T Scenario result.
     * @param filename Temporary Preferences file owned by this scenario.
     * @param block Scenario using the single live DataStore instance.
     */
    private suspend fun <T> withStore(
        filename: String = "alerts.preferences_pb",
        block: suspend (DataStore<Preferences>) -> T,
    ): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(files.root, filename) }
        return try {
            block(store)
        } finally {
            checkNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }
}

package io.github.CMPUT301F26stderr.campsbridge.platform.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Verifies durable account-keyed local state using real temporary DataStore files. */
class DataStoreAppPreferencesTest {
    @get:Rule val files = TemporaryFolder()

    /** Defaults do not create profiles; changes survive reopening without crossing accounts. */
    @Test fun progressAndWorkspaceAreIndependentAndDurable() =
        runBlocking {
            val path = File(files.root, "progress.preferences_pb")
            var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var repository =
                DataStoreAppPreferences(PreferenceDataStoreFactory.create(scope = scope) { path })
            try {
                assertEquals(LocalAccountProgress(), repository.observe("one").first())
                repository.setSetupProgress("one", SetupProgress.IN_PROGRESS)
                repository.setWorkspace("one", Role.OWNER)
                repository.setSetupProgress("two", SetupProgress.COMPLETED)
                assertEquals(
                    LocalAccountProgress(SetupProgress.IN_PROGRESS, Role.OWNER),
                    repository.observe("one").first(),
                )
                assertEquals(
                    LocalAccountProgress(SetupProgress.COMPLETED),
                    repository.observe("two").first(),
                )
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository =
                DataStoreAppPreferences(PreferenceDataStoreFactory.create(scope = scope) { path })
            try {
                assertEquals(
                    LocalAccountProgress(SetupProgress.IN_PROGRESS, Role.OWNER),
                    repository.observe("one").first(),
                )
                repository.setSetupProgress("one", SetupProgress.COMPLETED)
                assertEquals(Role.OWNER, repository.observe("one").first().workspace)
                assertEquals(
                    LocalAccountProgress(SetupProgress.COMPLETED),
                    repository.observe("two").first(),
                )
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            }
        }

    /** Corrupted bytes remain a recoverable failure instead of a guessed default. */
    @Test fun corruptStorageDoesNotBecomeSuccessfulRead() =
        runBlocking {
            val path =
                File(
                    files.root,
                    "broken.preferences_pb",
                ).apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val repository =
                DataStoreAppPreferences(PreferenceDataStoreFactory.create(scope = scope) { path })
            try {
                try {
                    repository.observe("one").first()
                    fail("Expected a failed read")
                } catch (expected: java.io.IOException) {
                    assertNotNull(expected)
                }
                try {
                    repository.setWorkspace("one", Role.OWNER)
                    fail("Expected a failed write")
                } catch (expected: java.io.IOException) {
                    assertNotNull(expected)
                }
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            }
        }
}

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI

/** Exercises observable local-demo behavior without Firebase or Android services. */
class DemoDataTest {
    @get:Rule val files = TemporaryFolder()

    /** Profile writes preserve other fields, role activation and stable retry identity. */
    @Test fun localAccountsRemainIndependentAndCreationIsIdempotent() =
        runTest {
            val accounts = DemoAccountRepository(emptyList())
            val original = accounts.create("one", ProfileUpdate("Alex", "a@example.com", null))
            val other = accounts.create("two", ProfileUpdate("Taylor", null, null))
            accounts.enableOwner("one")
            val preferences = Preferences(setOf("lakeside"))
            val settings = AccountSettings(fontScale = 1.25)
            accounts.updatePreferences("one", preferences)
            accounts.updateSettings("one", settings)
            accounts.updateProfile("one", ProfileUpdate("Alex Chen", "b@example.com", "123"))
            val expected =
                original.copy(
                    name = "Alex Chen",
                    email = "b@example.com",
                    phone = "123",
                    roles = setOf(Role.ENTRANT, Role.OWNER),
                    preferences = preferences,
                    settings = settings,
                )
            assertEquals(expected, accounts.observe("one").first())
            assertEquals(expected, accounts.create("one", ProfileUpdate("Ignored", null, null)))
            assertEquals(other, accounts.get("two"))
            assertNull(accounts.get("missing"))
            assertEquals(setOf(expected, other), accounts.observeAll().first().toSet())
        }

    /** Progress and workspace remain independent across accounts and never create a profile. */
    @Test fun localPreferencesAreScopedToEachIdentity() =
        runTest {
            val local = DemoAppPreferences(emptyMap())
            local.setSetupProgress("one", SetupProgress.IN_PROGRESS)
            local.setWorkspace("one", Role.OWNER)
            local.setSetupProgress("two", SetupProgress.COMPLETED)
            assertEquals(
                LocalAccountProgress(SetupProgress.IN_PROGRESS, Role.OWNER),
                local.observe("one").first(),
            )
            assertEquals(
                LocalAccountProgress(SetupProgress.COMPLETED),
                local.observe("two").first(),
            )
            assertEquals(LocalAccountProgress(), local.observe("missing").first())
        }

    /** Saved photo bytes are local, streams close, replacement and removal update observations. */
    @Test fun photosCopyLocallyAndCanBeReplacedAndRemoved() =
        runTest {
            val repository = DemoImageRepository(files.newFolder())
            val target = ImageTarget(ImageTargetType.ACCOUNT, "one")
            val source = TrackedStream(byteArrayOf(1, 2, 3))
            val first = repository.replace(target, ImageUpload("image/png") { source }).attachment!!
            assertTrue(source.closed)
            val firstFile = File(URI(repository.downloadUrl(first.blobId)))
            assertArrayEquals(byteArrayOf(1, 2, 3), firstFile.readBytes())
            val second =
                repository
                    .replace(
                        target,
                        ImageUpload("image/png") {
                            ByteArrayInputStream(byteArrayOf(4, 5))
                        },
                    ).attachment!!
            assertNotEquals(first.blobId, second.blobId)
            assertFalse(firstFile.exists())
            assertEquals(second, repository.observe(target).first())
            assertEquals(listOf(second), repository.observeAll().first())
            val secondFile = File(URI(repository.downloadUrl(second.blobId)))
            assertNull(repository.delete(target).attachment)
            assertNull(repository.delete(target).attachment)
            assertNull(repository.get(target))
            assertFalse(secondFile.exists())
        }

    /** An unreadable replacement preserves the last saved photo and removes partial bytes. */
    @Test fun failedPhotoReadRetainsTheSavedAttachment() =
        runTest {
            val directory = files.newFolder()
            val repository = DemoImageRepository(directory)
            val target = ImageTarget(ImageTargetType.ACCOUNT, "one")
            val saved =
                repository
                    .replace(
                        target,
                        ImageUpload("image/png") {
                            ByteArrayInputStream(byteArrayOf(1))
                        },
                    ).attachment
            val failure =
                runCatching {
                    repository.replace(
                        target,
                        ImageUpload("image/png") { throw IOException("unavailable") },
                    )
                }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals(saved, repository.get(target))
            assertEquals(1, directory.listFiles()!!.size)
        }

    /** Cleanup cannot delete the photo still referenced by a demo slot. */
    @Test fun cleanupPreservesActivePhotos() =
        runTest {
            val repository = DemoImageRepository(files.newFolder())
            val target = ImageTarget(ImageTargetType.ACCOUNT, "one")
            val saved =
                repository
                    .replace(
                        target,
                        ImageUpload("image/png") {
                            ByteArrayInputStream(byteArrayOf(1))
                        },
                    ).attachment!!
            assertTrue(runCatching { repository.retryCleanup(saved.blobId) }.isFailure)
            assertTrue(File(URI(repository.downloadUrl(saved.blobId))).exists())
            repository.retryCleanup("absent")
        }
}

/** Records ownership of uploaded streams.
 * @param bytes Image fixture bytes.
 */
private class TrackedStream(
    bytes: ByteArray,
) : ByteArrayInputStream(bytes) {
    var closed = false

    /** Records repository closure before releasing the ordinary stream. */
    override fun close() {
        closed = true
        super.close()
    }
}

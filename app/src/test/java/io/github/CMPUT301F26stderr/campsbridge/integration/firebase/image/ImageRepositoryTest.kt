package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.image

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.stored
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.assertPersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.documentExists
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.seed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owns Storage bytes, publication and deferred deletion, independently of removal cascades. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ImageRepositoryTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    /** Stream access stays off the caller thread, including a UI caller's dispatcher. */
    @Test(timeout = 60000)
    fun uploadOpensAndClosesContentOffCallerThread() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val caller = Thread.currentThread()
            val openedOn = AtomicReference<Thread>()
            val closedOn = AtomicReference<Thread>()
            val upload =
                ImageUpload("image/png") {
                    openedOn.set(Thread.currentThread())
                    object : ByteArrayInputStream("selected-photo".toByteArray()) {
                        /** Records the thread responsible for releasing selected content. */
                        override fun close() {
                            closedOn.set(Thread.currentThread())
                            super.close()
                        }
                    }
                }
            val result = owner.container.images.replace(target, upload)
            assertNotNull(result.attachment)
            assertNotNull(openedOn.get())
            assertNotNull(closedOn.get())
            assertNotSame(caller, openedOn.get())
            assertNotSame(caller, closedOn.get())
        }

    /** Replacing an attachment closes the stream and preserves new bytes during old-blob cleanup. */
    @Test(timeout = 60000)
    fun replacementPublishesBytesAndRecoveryDeletesOnlyObsoleteBlob() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            var closed = false
            val stream =
                object : ByteArrayInputStream("first-image".toByteArray()) {
                    /** Records ownership of the supplied stream's lifetime. */
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val first = owner.container.images.replace(target, ImageUpload("image/png") { stream })
            assertTrue(closed)
            val old = checkNotNull(first.attachment)
            val next = owner.container.images.replace(target, image("second-image"))
            val current = checkNotNull(next.attachment)
            assertNotEquals(old.blobId, current.blobId)
            assertEquals(current, owner.container.images.get(target))
            assertEquals(old.blobId, next.obsoleteBlobId)
            assertTrue(firebase.documentExists("_storageCleanup/${old.blobId}"))
            assertArrayEquals(
                "second-image".toByteArray(),
                owner.clients.storage.reference
                    .child(DocumentKeys.blob(current.blobId))
                    .getBytes(1024)
                    .await(),
            )
            assertTrue(
                owner.container.images
                    .downloadUrl(current.blobId)
                    .contains(firebase.storageEndpoint.rawAuthority),
            )
            admin.container.maintenance.resume(admin.id)
            assertFalse(blobExists(old.blobId))
            assertTrue(blobExists(current.blobId))
            assertFalse(firebase.documentExists("_storageCleanup/${old.blobId}"))
            assertFalse(firebase.documentExists("_imageUploads/${current.blobId}"))
        }

    /** Attachment deletion remains committed when cleanup is deferred and is safe to repeat. */
    @Test(timeout = 60000)
    fun deletionCanResumeThroughTheCompositionRoot() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val attachment =
                checkNotNull(
                    owner.container.images
                        .replace(target, image("delete-me"))
                        .attachment,
                )
            val result = owner.container.images.delete(target)
            assertNull(result.attachment)
            assertEquals(attachment.blobId, result.obsoleteBlobId)
            assertNull(owner.container.images.get(target))
            assertTrue(blobExists(attachment.blobId))
            admin.container.maintenance.resume(admin.id)
            admin.container.maintenance.resume(admin.id)
            assertFalse(blobExists(attachment.blobId))
            assertNull(
                owner.container.images
                    .delete(target)
                    .obsoleteBlobId,
            )
        }

    /** Maintenance protects active/attached blobs and retains expired intent until late bytes vanish. */
    @Test(timeout = 60000)
    fun cleanupProtectsLiveDataAndCatchesLateUploads() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val attached =
                checkNotNull(
                    owner.container.images
                        .replace(target, image("attached"))
                        .attachment,
                )
            assertPersistenceFailure(
                CONFLICT,
            ) { admin.container.images.retryCleanup(attached.blobId) }
            intent("active", target, expired = false)
            assertPersistenceFailure(CONFLICT) { admin.container.images.retryCleanup("active") }
            assertTrue(firebase.documentExists("_imageUploads/active"))
            intent("late", target, expired = true)
            admin.container.images.retryCleanup("late")
            assertTrue(firebase.documentExists("_imageUploads/late"))
            // Arrange bytes that an already-started transport delivered after abandonment.
            firebase.request(
                "${firebase.storageEndpoint}/upload/storage/v1/b/" +
                    "${firebase.projectId}.appspot.com/o" +
                    "?uploadType=media&name=images%2Flate%2Foriginal",
                "POST",
                "late-image",
                contentType = "image/png",
            )
            assertTrue(blobExists("late"))
            admin.container.maintenance.resume(admin.id)
            assertFalse(blobExists("late"))
            assertFalse(firebase.documentExists("_imageUploads/late"))
            assertTrue(blobExists(attached.blobId))
            assertTrue(firebase.documentExists("_imageUploads/active"))
        }

    /** A failed publication preserves the preceding attachment and identifies retained uploaded bytes. */
    @Test(timeout = 60000)
    fun publicationFailureIsReconciliableWithoutDeletingTheOldImage() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val old =
                checkNotNull(
                    owner.container.images
                        .replace(target, image("original"))
                        .attachment,
                )
            val profile = checkNotNull(owner.container.accounts.get(owner.id))
            val blocked =
                object : ByteArrayInputStream("unpublished".toByteArray()) {
                    /** Changes eligibility after the transfer to isolate publication from upload failure. */
                    override fun close() {
                        firebase.seed(
                            "accounts/${owner.id}",
                            AccountDocument.encode(
                                profile.copy(removal = Removal(firebase.clock.instant(), admin.id)),
                            ),
                        )
                        super.close()
                    }
                }
            val failure =
                try {
                    owner.container.images.replace(target, ImageUpload("image/png") { blocked })
                    error("Removed account must not publish an attachment")
                } catch (failure: ImagePublicationException) {
                    failure
                }
            assertEquals(target, failure.target)
            assertEquals(old, admin.container.images.get(target))
            assertTrue(blobExists(failure.uploadedBlobId))
            assertTrue(firebase.documentExists("_imageUploads/${failure.uploadedBlobId}"))
            intent(failure.uploadedBlobId, target, expired = true, finished = true)
            admin.container.maintenance.resume(admin.id)
            assertFalse(blobExists(failure.uploadedBlobId))
            assertTrue(blobExists(old.blobId))
        }

    /** Opening failures leave durable intent and never replace the published attachment. */
    @Test(timeout = 60000)
    fun streamOpeningFailurePreservesTheSlot() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val old =
                checkNotNull(
                    owner.container.images
                        .replace(target, image("original"))
                        .attachment,
                )
            val failure = IOException("Input unavailable")
            try {
                owner.container.images.replace(target, ImageUpload("image/png") { throw failure })
                error("Expected input failure")
            } catch (actual: IOException) {
                assertSame(failure, actual)
            }
            assertEquals(old, owner.container.images.get(target))
            val admin = firebase.registeredActor(Role.ADMIN)
            val intents =
                admin.clients.firestore
                    .collection("_imageUploads")
                    .get()
                    .await()
                    .documents
            assertEquals(1, intents.size)
            assertEquals(false, intents.single().getBoolean("uploadFinished"))
        }

    /** Cancelling a transfer closes its stream without replacing the slot or acknowledging completion. */
    @Test(timeout = 60000)
    fun cancellingUploadClosesInputAndPreservesPublishedImage() =
        runBlocking {
            val owner = firebase.registeredActor(Role.ENTRANT)
            val admin = firebase.registeredActor(Role.ADMIN)
            val target = ImageTarget(ImageTargetType.ACCOUNT, owner.id)
            val old =
                checkNotNull(
                    owner.container.images
                        .replace(target, image("original"))
                        .attachment,
                )
            val reading = CompletableDeferred<Unit>()
            val release = Semaphore(0)
            val closed = AtomicBoolean(false)
            val stream =
                object : ByteArrayInputStream("cancelled-upload".toByteArray()) {
                    /**
                     * Holds a real transfer at its input boundary until cancellation closes the stream.
                     * @param bytes Destination supplied by the SDK.
                     * @param offset First destination index.
                     * @param length Maximum requested bytes.
                     */
                    override fun read(
                        bytes: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        reading.complete(Unit)
                        release.acquire()
                        return if (closed.get()) -1 else super.read(bytes, offset, length)
                    }

                    /** Unblocks the transfer worker and records closure by the repository. */
                    override fun close() {
                        closed.set(true)
                        release.release()
                        super.close()
                    }
                }
            val transfer =
                launch {
                    owner.container.images.replace(
                        target,
                        ImageUpload("image/png") { stream },
                    )
                }
            reading.await()
            transfer.cancelAndJoin()
            assertTrue(closed.get())
            assertEquals(old, owner.container.images.get(target))
            val pending =
                admin.clients.firestore
                    .collection(
                        "_imageUploads",
                    ).get()
                    .await()
                    .documents
                    .single()
            assertEquals(false, pending.getBoolean("uploadFinished"))
            assertTrue(blobExists(old.blobId))
        }

    /**
     * Supplies deterministic image bytes without depending on a decoder.
     * @param body Content transferred unchanged by the Storage repository.
     */
    private fun image(body: String) =
        ImageUpload("image/png") { body.byteInputStream() }

    /**
     * Checks independently whether an object remains in the disposable bucket.
     * @param blobId Immutable object identity.
     */
    private fun blobExists(blobId: String): Boolean =
        firebase.request(
            "${firebase.storageEndpoint}/storage/v1/b/${firebase.projectId}.appspot.com/o/images%2F$blobId%2Foriginal",
        ) != null

    /**
     * Arranges a durable upload at a maintenance boundary without opening a transport.
     * @param blobId Immutable object identity.
     * @param target Attachment slot.
     * @param expired Whether the server-visible deadline has elapsed.
     * @param finished Whether completion was acknowledged.
     */
    private fun intent(
        blobId: String,
        target: ImageTarget,
        expired: Boolean,
        finished: Boolean = false,
    ) {
        firebase.seed(
            "_imageUploads/$blobId",
            mapOf(
                "attachmentId" to DocumentKeys.attachment(target),
                "expiresAt" to
                    firebase.clock
                        .instant()
                        .plusSeconds(if (expired) -60 else 900)
                        .stored(),
                "abandoned" to false,
                "uploadFinished" to finished,
            ),
        )
    }
}

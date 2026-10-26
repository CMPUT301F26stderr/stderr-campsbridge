package io.github.CMPUT301F26stderr.campsbridge.ui.common.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpdateResult
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant

/** Behavioral contracts for a real image controller with controlled persistence dependencies. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageSlotControllerTest {
    private val target = ImageTarget(ImageTargetType.ACCOUNT, "account-1")

    /** Creates known attachment facts without making time part of the assertion.
     * @param blob Immutable test blob ID.
     */
    private fun attachment(blob: String): ImageAttachment =
        ImageAttachment(target, blob, Instant.EPOCH)

    /** Provides a fresh stream only when the repository elects to consume it.
     * @param name Local selection identity.
     */
    private fun photo(name: String = "selected"): SelectedPhoto =
        SelectedPhoto(
            "content://fixtures/$name",
            ImageUpload("image/png") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
        )

    /** Unresolved and failed reads are never interpreted as a confirmed empty slot. */
    @Test fun loadingEmptyAndReadFailureAreDistinct() =
        runTest {
            val images = FakeImageRepository().apply { observationGate = CompletableDeferred() }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            assertEquals(ImageLoadState.LOADING, model.state.value.loadState)
            model.select(photo())
            model.save()
            runCurrent()
            assertEquals(0, images.replaceCalls)
            images.observationGate!!.complete(Unit)
            runCurrent()
            assertEquals(ImageLoadState.READY, model.state.value.loadState)
            assertNull(model.state.value.attachment)
            images.streamFailure.value = IOException("read failed")
            runCurrent()
            assertEquals(ImageLoadState.FAILED, model.state.value.loadState)
            assertEquals(
                photo().previewReference,
                model.state.value.candidate
                    ?.previewReference,
            )
            images.streamFailure.value = null
            model.retry()
            runCurrent()
            assertEquals(ImageLoadState.READY, model.state.value.loadState)
            assertEquals(1, images.activeObservers)
        }

    /** Selecting, picker cancellation, and discard never persist an image. */
    @Test fun selectionAndDiscardAreLocalOnly() =
        runTest {
            val images = FakeImageRepository().apply { attachment.value = attachment("old") }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            assertEquals("file:///missing-fixture/old", model.state.value.displayUrl)
            val chosen = photo()
            model.select(chosen)
            model.select(null)
            runCurrent()
            assertEquals(chosen, model.state.value.candidate)
            model.selectionFailed()
            assertEquals(chosen, model.state.value.candidate)
            assertEquals(ImageFailure.SELECTION, model.state.value.failure)
            model.discard()
            runCurrent()
            assertNull(model.state.value.candidate)
            assertEquals(attachment("old"), model.state.value.attachment)
            assertEquals(0, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
        }

    /** Repeated actions cannot compete with one pending save; success uses its returned fact. */
    @Test fun saveIsExplicitSerializedAndConfirmed() =
        runTest {
            val images = FakeImageRepository().apply { uploadGate = CompletableDeferred() }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            model.save()
            runCurrent()
            assertEquals(0, images.replaceCalls)
            val selected = photo()
            model.select(selected)
            model.save()
            model.save()
            model.remove()
            model.select(photo("other"))
            model.discard()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
            assertEquals(ImageOperation.SAVING, model.state.value.operation)
            assertEquals(selected, model.state.value.candidate)
            assertNull(model.state.value.attachment)
            images.uploadGate!!.complete(Unit)
            runCurrent()
            assertEquals(ImageOperation.IDLE, model.state.value.operation)
            assertEquals(attachment("blob-1"), model.state.value.attachment)
            assertNull(model.state.value.candidate)
            assertEquals("file:///missing-fixture/blob-1", model.state.value.displayUrl)
            assertEquals(listOf(target), images.observedTargets)
            assertEquals(listOf(target), images.replacementTargets)
        }

    /** Deferred cleanup is publication success and never becomes a product save failure. */
    @Test fun pendingCleanupStillConfirmsTheImage() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    replaceResult = ImageUpdateResult(attachment("published"), "old")
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            model.select(photo())
            model.save()
            runCurrent()
            assertEquals(attachment("published"), model.state.value.attachment)
            assertNull(model.state.value.failure)
            assertNull(model.state.value.candidate)
        }

    /** Ordinary save failure retains both selection and prior image for one explicit retry. */
    @Test fun failedSaveRetainsInputAndCanRetry() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    attachment.value = attachment("old")
                    replaceFailure = IOException("offline")
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            val selected = photo()
            model.select(selected)
            model.save()
            runCurrent()
            assertEquals(ImageFailure.SAVE, model.state.value.failure)
            assertEquals(selected, model.state.value.candidate)
            assertEquals(attachment("old"), model.state.value.attachment)
            assertTrue(model.state.value.canSave)
            assertEquals(1, images.replaceCalls)
            images.replaceFailure = null
            model.retry()
            runCurrent()
            assertEquals(2, images.replaceCalls)
            assertNull(model.state.value.candidate)
            assertNull(model.state.value.failure)
        }

    /** Removal preserves known state on failure and becomes empty only after confirmation. */
    @Test fun removeFailureAndRetryHaveConfirmedOutcomes() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    attachment.value = attachment("old")
                    deleteFailure = IOException("offline")
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            model.remove()
            runCurrent()
            assertEquals(ImageFailure.REMOVE, model.state.value.failure)
            assertEquals(attachment("old"), model.state.value.attachment)
            images.deleteFailure = null
            images.deleteGate = CompletableDeferred()
            model.retry()
            model.remove()
            runCurrent()
            assertEquals(2, images.deleteCalls)
            assertEquals(ImageOperation.REMOVING, model.state.value.operation)
            images.deleteGate!!.complete(Unit)
            runCurrent()
            assertNull(model.state.value.attachment)
            assertNull(model.state.value.displayUrl)
            assertNull(model.state.value.failure)
            assertEquals(listOf(target, target), images.removalTargets)
        }

    /** Display failure retains the attachment and retries the URL without another upload. */
    @Test fun urlFailureDoesNotBecomeAnUploadRetry() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    attachment.value = attachment("old")
                    urlFailures["old"] = IOException("URL failed")
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            assertTrue(model.state.value.displayFailed)
            assertEquals(attachment("old"), model.state.value.attachment)
            images.urlFailures.clear()
            model.retry()
            runCurrent()
            assertFalse(model.state.value.displayFailed)
            assertEquals("file:///missing-fixture/old", model.state.value.displayUrl)
            assertEquals(0, images.replaceCalls)
            assertEquals(listOf("old", "old"), images.downloads)
        }

    /** Late URL completions cannot replace a newer attachment or local preview. */
    @Test fun supersededUrlResultsCannotOverwriteCurrentDisplay() =
        runTest {
            val slow = CompletableDeferred<String>()
            val images =
                FakeImageRepository().apply {
                    attachment.value = attachment("old")
                    urlGates["old"] = slow
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            assertTrue(model.state.value.isResolvingUrl)
            images.attachment.value = attachment("new")
            runCurrent()
            slow.complete("obsolete URL")
            runCurrent()
            assertEquals("file:///missing-fixture/new", model.state.value.displayUrl)
            val next = CompletableDeferred<String>()
            images.urlGates["next"] = next
            images.attachment.value = attachment("next")
            runCurrent()
            val selected = photo()
            model.select(selected)
            next.complete("late URL")
            runCurrent()
            assertEquals(selected, model.state.value.candidate)
            assertFalse(model.state.value.isResolvingUrl)
            model.discard()
            runCurrent()
            assertEquals("late URL", model.state.value.displayUrl)
        }

    /** Uncertain publication blocks another write until a matching server read confirms it. */
    @Test fun ambiguousPublicationReconcilesWithoutReuploadOrCleanup() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    attachment.value = attachment("old")
                    replaceFailure =
                        ImagePublicationException(target, "uploaded", IOException("ack lost"))
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            val selected = photo()
            model.select(selected)
            model.save()
            runCurrent()
            assertEquals("uploaded", model.state.value.unconfirmedBlobId)
            assertEquals(selected, model.state.value.candidate)
            assertFalse(model.state.value.canSave)
            model.save()
            model.remove()
            model.select(photo("other"))
            model.discard()
            runCurrent()
            assertEquals(selected, model.state.value.candidate)
            assertEquals(1, images.replaceCalls)
            assertEquals(0, images.deleteCalls)
            images.readGate = CompletableDeferred()
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(ImageOperation.RECONCILING, model.state.value.operation)
            assertEquals(1, images.getCalls)
            images.readGate!!.complete(Unit)
            runCurrent()
            assertEquals("uploaded", model.state.value.unconfirmedBlobId)
            images.readFailure = IOException("server unavailable")
            model.retry()
            runCurrent()
            assertEquals("uploaded", model.state.value.unconfirmedBlobId)
            images.readFailure = null
            images.attachment.value = attachment("uploaded")
            runCurrent()
            assertEquals("uploaded", model.state.value.unconfirmedBlobId)
            model.retry()
            runCurrent()
            assertNull(model.state.value.unconfirmedBlobId)
            assertNull(model.state.value.candidate)
            assertEquals(attachment("uploaded"), model.state.value.attachment)
            assertEquals("file:///missing-fixture/uploaded", model.state.value.displayUrl)
            assertEquals(1, images.replaceCalls)
        }

    /** Parent cancellation releases observation and prevents late save state or generic errors. */
    @Test fun parentCancellationStopsWorkWithoutReportingFailure() =
        runTest {
            val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val images = FakeImageRepository().apply { uploadGate = CompletableDeferred() }
            val model = ImageSlotController(images, target, owner)
            try {
                runCurrent()
                assertEquals(1, images.activeObservers)
                model.select(photo())
                model.save()
                runCurrent()
                assertEquals(1, images.replaceCalls)
                val before = model.state.value
                owner.cancel()
                runCurrent()
                images.uploadGate!!.complete(Unit)
                runCurrent()
                assertEquals(before, model.state.value)
                assertEquals(0, images.activeObservers)
                assertNull(model.state.value.failure)
            } finally {
                owner.cancel()
                model.close()
                runCurrent()
            }
        }

    /** A cancelled dependency (including the repository upload timeout) must not trap an active slot. */
    @Test fun dependencyCancellationAllowsAnotherExplicitSave() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    replaceFailure =
                        CancellationException("upload timeout")
                }
            val model = ImageSlotController(images, target, backgroundScope)
            runCurrent()
            val selected = photo()
            model.select(selected)
            model.save()
            runCurrent()
            assertEquals(1, images.replaceCalls)
            assertEquals(ImageOperation.IDLE, model.state.value.operation)
            assertEquals(selected, model.state.value.candidate)
            assertNull(model.state.value.failure)
            assertEquals(1, images.activeObservers)
            assertTrue(model.state.value.canSave)
            images.replaceFailure = null
            model.save()
            runCurrent()
            assertEquals(2, images.replaceCalls)
            assertNull(model.state.value.candidate)
        }
}

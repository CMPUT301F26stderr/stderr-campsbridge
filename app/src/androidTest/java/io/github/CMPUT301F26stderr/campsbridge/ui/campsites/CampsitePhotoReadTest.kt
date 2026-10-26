package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.PhotoContentReader
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.ControlledPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Clock

/** Verifies route disposal during a native content read without relying on a fast provider race. */
class CampsitePhotoReadTest {
    @get:Rule val compose = createComposeRule()
    private val sites = FakeCampsiteRepository()
    private val images = FakeImageRepository()
    private val store = ViewModelStore()
    private val reader = HeldPhotoReader()

    /** Stops the retained draft and confirms no upload occurred. */
    @After fun close() {
        compose.runOnIdle {
            reader.gate.complete(Unit)
            store.clear()
            assertEquals(0, images.replaceCalls)
            assertEquals(0, sites.writeCalls)
        }
    }

    /** Configuration-like route disposal reports recoverable interruption and rejects late bytes. */
    @Test fun disposedReadReportsFailureOnTheRetainedDraft() {
        val model =
            CampsiteSetupViewModel(
                "account-1",
                null,
                sites,
                images,
                Clock.systemUTC(),
                SavedStateHandle(),
            ).also { store.put("setup", it) }
        val picker = ControlledPhotoPicker(fixturePhotoUri())
        var mounted by mutableStateOf(true)
        compose.setContent {
            if (mounted) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                    CampsbridgeTheme {
                        CampsiteSetupRoute(model, {}, {}, {}, photoReader = reader)
                    }
                }
            }
        }
        compose.runOnIdle {
            model.editName("Camp")
            model.next()
        }
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.waitUntil(5_000) { reader.started.isCompleted }
        compose.runOnIdle { mounted = false }
        compose.waitUntil(5_000) { reader.cancelled.isCompleted }
        compose.runOnIdle {
            reader.gate.complete(Unit)
            mounted = true
        }
        compose.waitUntil(5_000) { reader.returned.isCompleted }
        compose
            .onNodeWithText("Could not read that photo. Your previous choice is retained.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Photo selected").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                CampsitePhotoIntent.Keep,
                (model.state.value as CampsiteSetupUiState.Content).photoIntent,
            )
        }
    }
}

/** Models provider work that can finish despite cancellation after the real picker callback. */
private class HeldPhotoReader : PhotoContentReader {
    val gate = CompletableDeferred<Unit>()
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    val returned = CompletableDeferred<Unit>()

    /** Returns late metadata despite cancellation to exercise the caller's active-job guard.
     * @param reference Native picker identity.
     */
    override suspend fun read(reference: String): SelectedPhoto {
        started.complete(Unit)
        try {
            gate.await()
        } catch (_: CancellationException) {
            cancelled.complete(Unit)
            withContext(NonCancellable) { gate.await() }
        }
        returned.complete(Unit)
        return SelectedPhoto(
            reference,
            ImageUpload("image/png") { ByteArrayInputStream(byteArrayOf(1)) },
        )
    }
}

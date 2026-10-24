package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.preview.sampleAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

/** Verifies profile wiring around the shared image controller, separate from image-rule tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val photo =
        SelectedPhoto(
            "content://fixtures/photo",
            ImageUpload("image/png") {
                ByteArrayInputStream(byteArrayOf(1))
            },
        )

    /** Account emissions preserve the independent candidate; explicit save uses the account slot. */
    @Test fun accountUpdatesPreservePhotoAndSaveTargetsTheAccount() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount())
            val images = FakeImageRepository()
            val model = ProfileViewModel("account-1", accounts, images)
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                runCurrent()
                model.selectPhoto(photo)
                accounts.account.value = sampleAccount().copy(name = "Updated profile")
                runCurrent()
                assertEquals(photo, model.photoState.value.candidate)
                assertEquals(
                    "Updated profile",
                    (model.state.value as ProfileUiState.Content).account.name,
                )
                model.savePhoto()
                runCurrent()
                assertEquals(
                    listOf(ImageTarget(ImageTargetType.ACCOUNT, "account-1")),
                    images.replacementTargets,
                )
                assertNull(model.photoState.value.candidate)
                assertEquals(0, accounts.writes)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** No photo mutation can be initiated after a profile becomes unavailable. */
    @Test fun unavailableProfileCannotStartPhotoActions() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount())
            val images = FakeImageRepository()
            val model = ProfileViewModel("account-1", accounts, images)
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                runCurrent()
                model.selectPhoto(photo)
                runCurrent()
                assertEquals(photo, model.photoState.value.candidate)
                accounts.account.value = null
                runCurrent()
                model.selectPhoto(photo.copy(previewReference = "other"))
                model.savePhoto()
                model.removePhoto()
                runCurrent()
                assertEquals(photo, model.photoState.value.candidate)
                assertEquals(0, images.replaceCalls)
                assertEquals(0, images.deleteCalls)
            } finally {
                store.clear()
                runCurrent()
            }
        }

    /** Clearing the profile owner cancels its image listener and pending display work. */
    @Test fun clearCancelsTheOwnedImageController() =
        runTest {
            val images =
                FakeImageRepository().apply {
                    attachment.value =
                        ImageAttachment(
                            ImageTarget(ImageTargetType.ACCOUNT, "account-1"),
                            "old",
                            Instant.EPOCH,
                        )
                    urlGates["old"] = CompletableDeferred()
                }
            val model =
                ProfileViewModel("account-1", FakeAccountRepository(sampleAccount()), images)
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                runCurrent()
                assertEquals(1, images.activeObservers)
                val before = model.photoState.value
                store.clear()
                runCurrent()
                images.urlGates.getValue("old").complete("late")
                runCurrent()
                assertEquals(before, model.photoState.value)
                assertEquals(0, images.activeObservers)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

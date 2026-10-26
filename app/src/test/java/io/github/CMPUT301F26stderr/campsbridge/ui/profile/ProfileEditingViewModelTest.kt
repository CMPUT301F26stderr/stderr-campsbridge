package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImagePublicationException
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/** Profile editor orchestration; injected fixture results do not implement reserved parsers. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** New account emissions cannot replace dirty text; cancelling starts fresh next time. */
    @Test fun confirmedRefreshPreservesDirtyDraftAndCancelDoesNotSave() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName("  unsaved name  ")
                f.accounts.account.value = sampleAccount().copy(name = "Server update")
                runCurrent()
                f.model.beginPersonalEdit()
                assertEquals("  unsaved name  ", f.content().personalDraft?.nameText)
                assertEquals("Server update", f.content().account.name)
                assertTrue(f.model.cancelPersonalEdit())
                assertNull(f.content().personalDraft)
                f.model.beginPersonalEdit()
                assertEquals("Server update", f.content().personalDraft?.nameText)
                assertEquals(0, f.accounts.writes)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Name save uses current contacts and completion is explicit and consumed once. */
    @Test fun nameSavePreservesLatestContactsAndUnrelatedFields() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName(" Taylor ")
                val refreshed = sampleAccount().copy(email = "latest@example.com", phone = "123")
                f.accounts.account.value = refreshed
                runCurrent()
                f.model.saveName()
                runCurrent()
                assertEquals(
                    listOf(ProfileUpdate("Taylor", "latest@example.com", "123")),
                    f.accounts.profileUpdates,
                )
                assertEquals(refreshed.copy(name = "Taylor"), f.accounts.account.value)
                assertNull(f.content().personalDraft)
                assertEquals(ProfileEditor.PERSONAL, f.content().completedEditor)
                assertFalse(f.model.cancelPersonalEdit())
                f.model.acknowledgeCompletion(ProfileEditor.CONTACT)
                assertEquals(ProfileEditor.PERSONAL, f.content().completedEditor)
                f.model.acknowledgeCompletion(ProfileEditor.PERSONAL)
                assertNull(f.content().completedEditor)
                f.model.saveName()
                runCurrent()
                assertEquals(1, f.accounts.profileWrites)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Valid(null) clears a contact rather than being confused with a missing dependency. */
    @Test fun contactSavePreservesLatestNameAndAllowsClearing() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginContactEdit()
                f.model.editEmail(" new@example.com ")
                f.model.editPhone("")
                f.accounts.account.value =
                    sampleAccount().copy(name = "Current name", phone = "old")
                runCurrent()
                f.model.saveContacts()
                runCurrent()
                assertEquals(
                    listOf(ProfileUpdate("Current name", "new@example.com", null)),
                    f.accounts.profileUpdates,
                )
                assertEquals(ProfileEditor.CONTACT, f.content().completedEditor)
                assertNull(f.content().contactDraft)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Both invalid contact fields have independent errors without discarding raw input. */
    @Test fun invalidContactsAndNameDoNotWrite() =
        runTest {
            val f =
                Fixture(
                    parseName = { DisplayNameInput.Required },
                    parseEmail = { OptionalContactInput.Invalid },
                    parsePhone = { OptionalContactInput.Invalid },
                )
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName(" ")
                f.model.saveName()
                assertTrue(f.content().nameError)
                assertEquals(" ", f.content().personalDraft?.nameText)
                f.model.cancelPersonalEdit()
                f.model.beginContactEdit()
                f.model.editEmail(" invalid ")
                f.model.editPhone(" words ")
                f.model.saveContacts()
                runCurrent()
                assertTrue(f.content().emailError)
                assertTrue(f.content().phoneError)
                assertEquals(ContactDetailsDraft(" invalid ", " words "), f.content().contactDraft)
                f.model.editEmail("different")
                assertFalse(f.content().emailError)
                assertTrue(f.content().phoneError)
                assertEquals(0, f.accounts.writes)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Missing human-task implementations leave inputs inspectable without faking success. */
    @Test fun missingHelpersBlockOnlyDependentSubmission() =
        runTest {
            val f = Fixture(parseName = null, parseEmail = null, parsePhone = null)
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName("Pending")
                f.model.saveName()
                assertTrue(f.content().nameValidationPending)
                assertEquals("Pending", f.content().personalDraft?.nameText)
                assertTrue(f.model.cancelPersonalEdit())
                f.model.beginContactEdit()
                f.model.editEmail("pending@example.com")
                f.model.saveContacts()
                runCurrent()
                assertTrue(f.content().contactValidationPending)
                assertEquals(0, f.accounts.writes)
                assertNull(f.content().completedEditor)
                assertTrue(f.model.cancelContactEdit())
                f.model.selectPhoto(photo())
                assertEquals(
                    "content://fixture/photo",
                    f.model.photoState.value.candidate
                        ?.previewReference,
                )
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Busy state is set before launching IO; direct repeated actions and exits are guarded. */
    @Test fun heldSaveBlocksDuplicateEditsExitAndPhotoActions() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.accounts.profileGate = CompletableDeferred()
                f.model.beginPersonalEdit()
                f.model.editName("Taylor")
                f.model.saveName()
                assertEquals(ProfileOperation.SAVING_NAME, f.content().operation)
                f.model.saveName()
                f.model.editName("Too late")
                f.model.selectPhoto(photo())
                assertFalse(f.model.cancelPersonalEdit())
                runCurrent()
                assertEquals(1, f.accounts.profileWrites)
                assertEquals("Taylor", f.content().personalDraft?.nameText)
                assertNull(f.model.photoState.value.candidate)
                f.accounts.profileGate!!.complete(Unit)
                runCurrent()
                assertEquals(ProfileOperation.IDLE, f.content().operation)
                assertEquals(ProfileEditor.PERSONAL, f.content().completedEditor)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Failed field saves keep input and allow an explicit retry without touching photos. */
    @Test fun saveFailureRetainsDraftAndRetryCanComplete() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginContactEdit()
                f.model.editEmail(" new@example.com ")
                f.accounts.profileFailure = IOException("offline")
                f.model.saveContacts()
                runCurrent()
                assertTrue(f.content().saveFailed)
                assertEquals(ProfileOperation.IDLE, f.content().operation)
                assertEquals(" new@example.com ", f.content().contactDraft?.emailText)
                assertNull(f.content().completedEditor)
                f.accounts.profileFailure = null
                f.model.saveContacts()
                runCurrent()
                assertFalse(f.content().saveFailed)
                assertEquals(2, f.accounts.profileWrites)
                assertEquals(0, f.images.replaceCalls)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Image mutation owns its own lifetime and blocks conflicting field completion/exit. */
    @Test fun heldPhotoSaveBlocksNameSaveAndExit() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName("Taylor")
                f.images.uploadGate = CompletableDeferred()
                f.model.selectPhoto(photo())
                f.model.savePhoto()
                runCurrent()
                f.model.saveName()
                assertFalse(f.model.cancelPersonalEdit())
                assertEquals(0, f.accounts.profileWrites)
                f.images.uploadGate!!.complete(Unit)
                runCurrent()
                f.model.saveName()
                runCurrent()
                assertEquals(1, f.accounts.profileWrites)
                assertEquals(1, f.images.replaceCalls)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Uncertain photo publication is retained without trapping the independent name editor. */
    @Test fun idleUncertainPhotoDoesNotBlockNameSaveOrExit() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.images.replaceFailure =
                    ImagePublicationException(
                        ImageTarget(ImageTargetType.ACCOUNT, "account-1"),
                        "unconfirmed",
                        IOException("ack lost"),
                    )
                f.model.selectPhoto(photo())
                f.model.savePhoto()
                runCurrent()
                f.model.retryPhoto()
                runCurrent()
                assertEquals("unconfirmed", f.model.photoState.value.unconfirmedBlobId)
                assertTrue(f.model.cancelPersonalEdit())
                assertEquals("unconfirmed", f.model.photoState.value.unconfirmedBlobId)
                f.model.beginPersonalEdit()
                f.model.saveName()
                runCurrent()
                assertEquals(1, f.accounts.profileWrites)
                assertEquals("unconfirmed", f.model.photoState.value.unconfirmedBlobId)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Recreated editors restore small input and never replay a cancelled write. */
    @Test fun restoredDraftDoesNotReplayInterruptedSave() =
        runTest {
            val saved = SavedStateHandle()
            val first = Fixture(saved = saved)
            runCurrent()
            first.model.beginPersonalEdit()
            first.model.editName(" Taylor ")
            first.accounts.profileGate = CompletableDeferred()
            first.model.saveName()
            runCurrent()
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            first.close()
            runCurrent()
            val restored = Fixture(saved = snapshot)
            try {
                runCurrent()
                assertEquals(" Taylor ", restored.content().personalDraft?.nameText)
                assertEquals(ProfileOperation.IDLE, restored.content().operation)
                assertNull(restored.content().completedEditor)
                assertEquals(0, restored.accounts.profileWrites)
            } finally {
                restored.close()
                runCurrent()
            }
        }

    /** Retrying a failed observation restores the same draft instead of reinitializing it. */
    @Test fun observationFailureAndRetryPreserveEditor() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginContactEdit()
                f.model.editPhone(" unsaved ")
                f.accounts.streamFailure.value = IOException("offline")
                runCurrent()
                assertEquals(ProfileUiState.Failure, f.model.state.value)
                f.accounts.streamFailure.value = null
                f.model.retry()
                runCurrent()
                assertEquals(" unsaved ", f.content().contactDraft?.phoneText)
                assertEquals(0, f.accounts.writes)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Removed/failed accounts cannot start new mutations; failed editors can still be left. */
    @Test fun failedReadPreventsWritesAndStillAllowsDiscard() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.editName("Taylor")
                f.accounts.streamFailure.value = IOException("offline")
                runCurrent()
                f.model.saveName()
                f.model.selectPhoto(photo())
                runCurrent()
                assertEquals(0, f.accounts.writes)
                assertNull(f.model.photoState.value.candidate)
                assertTrue(f.model.cancelPersonalEdit())
                f.accounts.streamFailure.value = null
                f.accounts.account.value = null
                f.model.retry()
                runCurrent()
                f.model.beginContactEdit()
                f.model.saveContacts()
                assertEquals(ProfileUiState.Unavailable, f.model.state.value)
                assertEquals(0, f.accounts.writes)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** An interrupted-write marker requires a confirmed read, never automatic resubmission. */
    @Test fun failedRestorationReadRequiresExplicitRetry() =
        runTest {
            val f =
                Fixture(
                    saved =
                        SavedStateHandle(
                            mapOf(
                                "profileName" to "Taylor",
                                "profileWritePending" to true,
                            ),
                        ),
                )
            try {
                f.accounts.readFailure = IOException("offline")
                runCurrent()
                assertEquals(ProfileUiState.Failure, f.model.state.value)
                f.accounts.readFailure = null
                f.model.retry()
                runCurrent()
                assertEquals("Taylor", f.content().personalDraft?.nameText)
                assertTrue(f.content().saveFailed)
                assertEquals(0, f.accounts.writes)
                assertNull(f.content().completedEditor)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Confirmation survives owner recreation until the resumed editor consumes navigation. */
    @Test fun confirmedCompletionSurvivesRecreationWithoutAnotherSave() =
        runTest {
            val saved = SavedStateHandle()
            val first = Fixture(saved = saved)
            runCurrent()
            first.model.beginPersonalEdit()
            first.model.saveName()
            runCurrent()
            val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            first.close()
            runCurrent()
            val restored = Fixture(saved = snapshot)
            try {
                runCurrent()
                restored.model.beginPersonalEdit()
                assertEquals(ProfileEditor.PERSONAL, restored.model.completion.value)
                assertNull(restored.content().personalDraft)
                assertEquals(0, restored.accounts.writes)
                restored.model.acknowledgeCompletion(ProfileEditor.PERSONAL)
                assertNull(restored.model.completion.value)
            } finally {
                restored.close()
                runCurrent()
            }
        }

    /** Read failure cannot hide a confirmed save's navigation completion. */
    @Test fun confirmedCompletionIsIndependentOfAccountReadFailure() =
        runTest {
            val f = Fixture()
            try {
                runCurrent()
                f.model.beginPersonalEdit()
                f.model.saveName()
                runCurrent()
                f.accounts.streamFailure.value = IOException("offline")
                runCurrent()
                assertEquals(ProfileUiState.Failure, f.model.state.value)
                assertEquals(ProfileEditor.PERSONAL, f.model.completion.value)
                f.model.acknowledgeCompletion(ProfileEditor.PERSONAL)
                assertNull(f.model.completion.value)
                assertTrue(f.model.cancelPersonalEdit())
                assertEquals(1, f.accounts.profileWrites)
            } finally {
                f.close()
                runCurrent()
            }
        }

    /** Clearing the owner propagates cancellation instead of emitting a failed or saved result. */
    @Test fun clearingOwnerCancelsPendingWriteWithoutCompletion() =
        runTest {
            val f = Fixture()
            runCurrent()
            f.accounts.profileGate = CompletableDeferred()
            f.model.beginPersonalEdit()
            f.model.editName("Taylor")
            f.model.saveName()
            runCurrent()
            val before = f.model.state.value
            f.close()
            runCurrent()
            f.accounts.profileGate!!.complete(Unit)
            runCurrent()
            assertEquals(before, f.model.state.value)
            assertEquals(
                "Alex Chen",
                f.accounts.account.value
                    ?.name,
            )
            assertEquals(0, f.accounts.activeObservers)
            assertEquals(0, f.images.activeObservers)
        }

    /** Owns controlled dependencies and one production model.
     * @param saved Restorable editor input.
     * @param parseName Fixed fixture parser result, or pending dependency.
     * @param parseEmail Fixed fixture parser result, or pending dependency.
     * @param parsePhone Fixed fixture parser result, or pending dependency.
     */
    private class Fixture(
        saved: SavedStateHandle = SavedStateHandle(),
        parseName: ((String) -> DisplayNameInput)? = { DisplayNameInput.Valid("Taylor") },
        parseEmail: (
            (
                String,
            ) -> OptionalContactInput
        )? = { OptionalContactInput.Valid("new@example.com") },
        parsePhone: ((String) -> OptionalContactInput)? = { OptionalContactInput.Valid(null) },
    ) : AutoCloseable {
        val accounts = FakeAccountRepository(sampleAccount()).apply { allowWrites = true }
        val images = FakeImageRepository()
        val model =
            ProfileViewModel(
                "account-1",
                accounts,
                images,
                saved,
                parseName,
                parseEmail,
                parsePhone,
            )
        private val store = ViewModelStore().apply { put("profile", model) }

        /** Returns the observed content after the shared scheduler has advanced. */
        fun content(): ProfileUiState.Content =
            model.state.value as ProfileUiState.Content

        /** Cancels field writes and all account/image observations. */
        override fun close() {
            store.clear()
        }
    }

    /** Supplies native selection metadata without loading a real image in JVM tests. */
    private fun photo(): SelectedPhoto =
        SelectedPhoto(
            "content://fixture/photo",
            ImageUpload("image/png") { ByteArrayInputStream(byteArrayOf(1)) },
        )
}

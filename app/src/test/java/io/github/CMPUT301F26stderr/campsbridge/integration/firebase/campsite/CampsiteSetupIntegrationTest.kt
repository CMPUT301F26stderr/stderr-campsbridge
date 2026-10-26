package io.github.CMPUT301F26stderr.campsbridge.integration.firebase.campsite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.FirebaseEmulatorRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.firebase.registeredActor
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

/** Exercises the real setup controller through authenticated Firestore and Storage repositories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteSetupIntegrationTest {
    @get:Rule val firebase = FirebaseEmulatorRule()

    @get:Rule val main = MainDispatcherRule()

    /** Another account can read the actual published details, photo and offered nights after completion. */
    @Test fun publicationMakesTheSavedSetupVisibleToAnotherAccount() =
        runTest(timeout = 60.seconds) {
            val owner = firebase.registeredActor(Role.OWNER)
            val entrant = firebase.registeredActor(Role.ENTRANT)
            val details = CampingFixtures.campsite().details
            val date = CampingFixtures.arrival
            val model =
                CampsiteSetupViewModel(
                    owner.id,
                    null,
                    owner.container.campsites,
                    owner.container.images,
                    firebase.clock,
                    SavedStateHandle(),
                    newId = { "setup-integration" },
                    remainingAvailabilityEdits = { requested, _ -> requested },
                )
            val store = ViewModelStore().apply { put("setup", model) }
            try {
                model.state.first { it is CampsiteSetupUiState.Content }
                model.editName(details.name)
                model.editDescription(details.description)
                model.next()
                model.selectPhoto(
                    SelectedPhoto(
                        "content://test/selected-image",
                        ImageUpload(
                            "image/png",
                        ) { ByteArrayInputStream("image-fixture".toByteArray()) },
                    ),
                )
                model.next()
                model.selectLocation(details.location)
                model.editLocationLabel(details.locationLabel)
                model.editTimeZone(details.timeZone)
                model.next()
                model.editCapacity("8")
                model.editPrice("65.00")
                repeat(3) { model.next() }
                assertEquals(
                    CampsiteStep.AVAILABILITY,
                    (model.state.value as CampsiteSetupUiState.Content).draft.step,
                )
                model.toggleDate(date)
                model.next()
                model.publish()
                val id = model.completion.first { it != null }!!
                assertEquals("setup-integration", id)
                val published = entrant.container.campsites.get(id)!!
                assertTrue(published.isPublished)
                assertEquals(owner.id, published.ownerId)
                assertEquals(details.name, published.details.name)
                assertEquals(6500L, published.details.nightlyPriceCents)
                assertTrue(
                    entrant.container.campsites
                        .observePublished()
                        .first()
                        .any { it.id == id },
                )
                val target = ImageTarget(ImageTargetType.CAMPSITE, id)
                val attachment = entrant.container.images.get(target)
                assertNotNull(attachment)
                assertEquals(target, attachment!!.target)
                val nights =
                    entrant.container.campsites
                        .observeCalendar(
                            id,
                            date,
                            date.plusDays(1),
                        ).first()
                assertEquals(listOf(date), nights.map { it.date })
                assertTrue(nights.single().isFree)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

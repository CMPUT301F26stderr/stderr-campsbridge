package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.ControlledPhotoPicker
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixturePhotoUri
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** Real setup/navigation contracts for acknowledged details and attachment saves. */
class CampsiteDraftSaveFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("draft % save", published = false)

    /** Closes graph-owned subscriptions and pending controlled operations. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** A held save guards all conflicting controls, then opens the actual saved record once. */
    @Test fun savedDraftWaitsForAcknowledgementAndReturnsToManagement() {
        val gate = CompletableDeferred<Unit>()
        host.campsites.updateHandler = { id, _, details ->
            gate.await()
            host.campsites.records.value = listOf(site.copy(id = id, details = details))
        }
        start()
        compose.onNodeWithText("Campsite name").performTextReplacement("Saved River")
        review()
        compose.onNodeWithText("Save draft").assertIsEnabled().performClick()
        compose.onNodeWithText("Save draft").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Cancel").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled().performClick()
        pressBack()
        compose.onNodeWithText("Step 8 of 8").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Edit Introduce your campsite",
            ).performScrollTo()
            .assertIsNotEnabled()
        compose.onNodeWithText("Publish").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, host.campsites.updateRequests.size)
            assertEquals(
                site.id,
                host.campsites.updateRequests
                    .single()
                    .first,
            )
            gate.complete(Unit)
        }
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("Saved River").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My campsites").assertIsDisplayed()
        compose.onNodeWithText("Step 8 of 8").assertDoesNotExist()
    }

    /** A failed explicit save keeps the editable review and retries against the same identity. */
    @Test fun failedDetailsSavePreservesReviewAndOffersExplicitRetry() {
        host.campsites.updateHandler = { _, _, _ -> throw IOException("offline") }
        start()
        compose.onNodeWithText("Campsite name").performTextReplacement("Retry River")
        review()
        compose.onNodeWithText("Save draft").performClick()
        compose
            .onNodeWithText(
                "Could not save campsite details.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Retry River").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            host.campsites.updateHandler = { id, _, details ->
                host.campsites.records.value = listOf(site.copy(id = id, details = details))
            }
        }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(site.id, site.id), host.campsites.updateRequests.map { it.first })
            assertTrue(host.campsites.createRequests.isEmpty())
        }
    }

    /** Native photo selection is staged until details finish and saves to the actual parent. */
    @Test fun selectedPhotoWaitsForDetailsBeforePublishing() {
        val detailsGate = CompletableDeferred<Unit>()
        val photoGate = CompletableDeferred<Unit>()
        host.campsites.updateHandler = { id, _, details ->
            detailsGate.await()
            host.campsites.records.value = listOf(site.copy(id = id, details = details))
        }
        host.images.uploadGate = photoGate
        start(ControlledPhotoPicker(fixturePhotoUri()))
        compose.onNodeWithText("Campsite name").performTextReplacement("Photo River")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Photo selected").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(6) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Save draft").performClick()
        compose.runOnIdle {
            assertEquals(0, host.images.replaceCalls)
            detailsGate.complete(Unit)
        }
        compose.waitUntil(5_000) { host.images.replaceCalls == 1 }
        compose.onNodeWithText("Step 8 of 8").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle { photoGate.complete(Unit) }
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                ImageTarget(ImageTargetType.CAMPSITE, site.id),
                host.images.attachment.value
                    ?.target,
            )
            assertEquals(1, host.images.replaceCalls)
        }
    }

    /** Unknown removal remains on Review with recovery enabled and abandonment disabled. */
    @Test fun failedRemovalRequiresRetryBeforeLeaving() {
        host.images.attachment.value =
            ImageAttachment(
                ImageTarget(ImageTargetType.CAMPSITE, site.id),
                "saved-photo",
                Instant.parse("2027-05-01T00:00:00Z"),
            )
        host.images.deleteFailure = IOException("acknowledgement unknown")
        start()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        repeat(6) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Save draft").performClick()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Edit Add a photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Retry save").assertIsEnabled()
        compose.runOnIdle { host.images.deleteFailure = null }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, host.images.deleteCalls) }
    }

    /** Unimplemented night persistence cannot silently discard the user's requested dates. */
    @Test fun pendingDateChangesCannotBeDroppedBySavingDetailsOnly() {
        start()
        repeat(6) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithTag("calendar-date-2027-05-14").performScrollTo().performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Save draft").assertIsNotEnabled().performClick()
        compose
            .onNodeWithText(
                "Saving date changes is pending in this build. Your date choices are retained.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.campsites.writeCalls) }
    }

    /** The real review keeps partial progress and retries only the failed requested date. */
    @Test fun partialDateSaveRetainsOnlyTheFailedDateAndRetriesIt() {
        val first = LocalDate.of(2027, 5, 14)
        val second = first.plusDays(1)
        var failSecond = true
        host.remainingAvailabilityEdits = { requested, _ -> requested }
        host.campsites.offerHandler = { _, _, dates, _ ->
            if (dates.single() == second && failSecond) throw IOException("second date failed")
        }
        start()
        repeat(6) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithTag("calendar-date-$second").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-date-$first").performScrollTo().performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Save draft").assertIsEnabled().performClick()
        compose
            .onNodeWithText(
                "Could not save all date changes. Confirmed changes are kept. Retry to finish the remaining dates.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Date choices: 1 offered; 0 not offered",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(first, second),
                host.campsites.offerRequests.map { it.dates.single() },
            )
            failSecond = false
        }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(first, second, second),
                host.campsites.offerRequests.map { it.dates.single() },
            )
            assertEquals(0, host.images.replaceCalls)
        }
    }

    /** Starts the real saved-draft parent with optional native selection fixture.
     * @param picker Controlled Activity Result owner for the native photo adapter.
     */
    private fun start(picker: ControlledPhotoPicker? = null) {
        host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
        host.campsites.records.value = listOf(site)
        compose.setContent {
            if (picker == null) {
                host.Content()
            } else {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides picker,
                ) { host.Content() }
            }
        }
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("River Camp").performClick()
        compose.onNodeWithText("Continue setup").performScrollTo().performClick()
    }

    /** Walks a valid saved form through its eight real steps without staging date changes. */
    private fun review() {
        repeat(7) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Step 8 of 8").assertIsDisplayed()
    }
}

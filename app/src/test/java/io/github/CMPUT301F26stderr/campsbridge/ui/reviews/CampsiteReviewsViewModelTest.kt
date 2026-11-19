package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** RV-01 through RV-04 contracts for observed feedback and independent star/text actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteReviewsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val site = CampingFixtures.campsite()
    private val actor = CampingFixtures.account().copy(roles = Role.entries.toSet())
    private val sites = FakeCampsiteRepository(listOf(site))
    private val accounts = FakeAccountRepository(actor)
    private val reviews = FakeReviewRepository()
    private val stars = mutableListOf<Triple<String, String, Long>>()
    private val texts = mutableListOf<Triple<String, String, String?>>()

    /** RV-01: component records remain readable without labels and contexts expose distinct actions. */
    @Test fun observedComponentsAndRetainedEmptyAuthorProjectThroughEachContext() =
        test {
            val onlyStars = review("stars", 4, null)
            val onlyText = review("text", null, "Quiet beside the river.")
            val both = review("both", 5, "Good shade.")
            val own = review(actor.id, null, null)
            reviews.ratings(site.id).value = listOf(onlyStars, onlyText, both, own)
            for (context in Role.entries) {
                val model = model(context, key = context.name)
                runCurrent()
                val state = model.state.value
                assertTrue(
                    "Expected observed feedback content, got ${state.feedback}",
                    state.feedback is FeedbackRead.Content,
                )
                assertEquals(
                    setOf("stars", "text", "both"),
                    (state.feedback as FeedbackRead.Content)
                        .reviews
                        .map {
                            it.authorId
                        }.toSet(),
                )
                assertEquals(own, state.ownReview)
                assertEquals(context == Role.ENTRANT, state.canEdit)
                assertEquals(context == Role.ADMIN, state.canModerate)
                assertEquals(null, state.authorNames[onlyText.authorId])
                assertTrue(stars.isEmpty())
                assertTrue(texts.isEmpty())
            }
        }

    /** RV-02: one explicit star request stays pending even if a matching cached record arrives. */
    @Test fun matchingObservedStarsCannotSettleThePendingOperation() =
        test {
            val gate = CompletableDeferred<Unit>()
            reviews.setRatingHandler = { site, author, value ->
                stars.add(Triple(site, author, value))
                gate.await()
            }
            try {
                val model = model()
                runCurrent()
                assertTrue(stars.isEmpty())
                model.selectRating(3)
                model.saveRating()
                model.selectRating(4)
                model.saveRating()
                model.retryRating()
                runCurrent()
                assertEquals(listOf(Triple(site.id, actor.id, 3L)), stars)
                assertEquals(RatingEditState(3, RatingSaveStatus.SAVING), model.state.value.rating)
                reviews.ratings(site.id).value = listOf(review(actor.id, 3, null))
                runCurrent()
                assertEquals(RatingSaveStatus.SAVING, model.state.value.rating.status)
                gate.complete(Unit)
                runCurrent()
                assertEquals(RatingEditState(3, RatingSaveStatus.SAVED), model.state.value.rating)
                assertTrue(texts.isEmpty())
            } finally {
                gate.complete(Unit)
            }
        }

    /** RV-03: failed stars retain their value for Retry and a later explicit different choice. */
    @Test fun failedRatingRetryAndLaterChoicePreserveTheRawWrittenDraft() =
        test {
            reviews.setRatingHandler =
                { site, author, value ->
                    stars.add(Triple(site, author, value))
                    throw IOException("offline")
                }
            val model = model()
            runCurrent()
            model.editText("  Written draft\nKeep this line.  ")
            model.selectRating(3)
            model.saveRating()
            runCurrent()
            assertEquals(RatingEditState(3, RatingSaveStatus.NOT_SAVED), model.state.value.rating)
            reviews.setRatingHandler = { site, author, value -> stars.add(Triple(site, author, value)) }
            model.retryRating()
            runCurrent()
            model.selectRating(4)
            model.saveRating()
            runCurrent()
            assertEquals(listOf(3L, 3L, 4L), stars.map { it.third })
            assertEquals("  Written draft\nKeep this line.  ", model.state.value.textInput)
            assertTrue(texts.isEmpty())
        }

    /** RV-03: reconstruction keeps the interrupted value unconfirmed until explicit same-value Retry. */
    @Test fun interruptedRatingRestoresWithoutAutomaticallySubmittingAnything() =
        test {
            val gate = CompletableDeferred<Unit>()
            val saved = SavedStateHandle()
            reviews.setRatingHandler = { site, author, value ->
                stars.add(Triple(site, author, value))
                gate.await()
            }
            try {
                val first = model(saved = saved)
                runCurrent()
                first.editText("Retained written draft")
                first.selectRating(3)
                first.saveRating()
                runCurrent()
                val restoredPrimitives = saved.keys().associateWith { saved.get<Any?>(it) }
                store.clear()
                runCurrent()
                reviews.setRatingHandler = { site, author, value -> stars.add(Triple(site, author, value)) }
                val restored = model(saved = SavedStateHandle(restoredPrimitives))
                runCurrent()
                assertEquals(listOf(3L), stars.map { it.third })
                assertEquals(RatingEditState(3, RatingSaveStatus.NOT_SAVED), restored.state.value.rating)
                assertEquals("Retained written draft", restored.state.value.textInput)
                restored.retryRating()
                runCurrent()
                assertEquals(listOf(3L, 3L), stars.map { it.third })
                assertEquals(RatingSaveStatus.SAVED, restored.state.value.rating.status)
                assertTrue(texts.isEmpty())
            } finally {
                gate.complete(Unit)
            }
        }

    /** RV-04: written saves preserve stars; clearing saved text needs explicit confirmation. */
    @Test fun explicitWrittenSaveAndConfirmedClearChangeOnlyTheWrittenComponent() =
        test {
            reviews.ratings(site.id).value = listOf(review(actor.id, 4, "Original review"))
            reviews.setReviewTextHandler = { site, author, body ->
                texts.add(Triple(site, author, body))
                reviews.ratings(site).value = listOf(review(author, 4, body))
            }
            val model = model()
            runCurrent()
            model.editText("New review\nTwo lines.")
            model.saveText()
            runCurrent()
            assertEquals(listOf(Triple(site.id, actor.id, "New review\nTwo lines.")), texts)
            model.editText("   ")
            model.saveText()
            assertTrue(model.state.value.confirmingClear)
            model.dismissClear()
            model.confirmClear()
            runCurrent()
            assertEquals(1, texts.size)
            model.saveText()
            model.confirmClear()
            runCurrent()
            assertEquals(Triple(site.id, actor.id, null), texts.last())
            assertEquals(
                4L,
                model.state.value.ownReview
                    ?.rating,
            )
            assertTrue(stars.isEmpty())
        }

    /** RV-04: failed text keeps exact input and moderation never resubmits the retained draft. */
    @Test fun failedTextAndObservedModerationKeepExplicitSubmissionBoundaries() =
        test {
            reviews.ratings(site.id).value = listOf(review(actor.id, 3, "Saved text"))
            reviews.setReviewTextHandler =
                { site, author, body ->
                    texts.add(Triple(site, author, body))
                    throw IOException("offline")
                }
            reviews.setRatingHandler = { _, _, _ -> Unit }
            val model = model()
            runCurrent()
            model.editText("  Retry this raw text\nLater.  ")
            model.saveText()
            runCurrent()
            assertTrue(model.state.value.textFailed)
            assertEquals("  Retry this raw text\nLater.  ", model.state.value.textInput)
            model.selectRating(5)
            model.saveRating()
            runCurrent()
            assertEquals("  Retry this raw text\nLater.  ", model.state.value.textInput)
            reviews.ratings(site.id).value = listOf(review(actor.id, null, null))
            runCurrent()
            assertEquals(1, texts.size)
            assertEquals("  Retry this raw text\nLater.  ", model.state.value.textInput)
            assertFalse(model.state.value.textSaving)
        }

    /** Choosing stars is local; only explicit Save sends the latest valid choice. */
    @Test fun starDraftNeedsSaveAndLaterObservedChangesRemainAuthoritative() =
        test {
            reviews.ratings(site.id).value = listOf(review(actor.id, 2, "Saved body"))
            reviews.setRatingHandler = { site, author, value -> stars.add(Triple(site, author, value)) }
            val model = model()
            runCurrent()
            model.selectRating(0)
            model.selectRating(6)
            assertEquals(2L, model.state.value.rating.value)
            model.selectRating(3)
            model.selectRating(5)
            runCurrent()
            assertTrue(stars.isEmpty())
            assertFalse(model.requestBack())
            model.dismissDiscard()
            model.saveRating()
            runCurrent()
            assertEquals(listOf(5L), stars.map { it.third })
            assertTrue(texts.isEmpty())
            reviews.ratings(site.id).value = listOf(review(actor.id, 1, "Saved body"))
            runCurrent()
            assertEquals(1L, model.state.value.rating.value)
        }

    /** Text-only feedback and unsaved stars use independent repository operations. */
    @Test fun writtenOnlySaveAndDiscardNeverPublishLocalStars() =
        test {
            reviews.setReviewTextHandler = { site, author, body -> texts.add(Triple(site, author, body)) }
            val model = model()
            runCurrent()
            model.selectRating(4)
            model.editText("Text without a saved rating")
            model.saveText()
            runCurrent()
            assertEquals(listOf("Text without a saved rating"), texts.map { it.third })
            assertTrue(stars.isEmpty())
            assertFalse(model.requestBack())
            assertTrue(model.confirmDiscard())
            assertEquals(null, model.state.value.rating.value)
            model.saveRating()
            runCurrent()
            assertTrue(stars.isEmpty())
        }

    /** Source failure blocks writes; Retry restores reads without replaying a draft. */
    @Test fun failedSourceAndUnavailableListingBlockBothSaves() =
        test {
            val model = model()
            runCurrent()
            model.selectRating(4)
            model.editText("Keep this draft")
            reviews.failure(site.id).value = IOException("offline")
            runCurrent()
            assertEquals(FeedbackRead.Failure, model.state.value.feedback)
            assertFalse(model.state.value.canEdit)
            model.saveRating()
            model.saveText()
            runCurrent()
            assertTrue(stars.isEmpty())
            assertTrue(texts.isEmpty())
            reviews.failure(site.id).value = null
            model.retry()
            runCurrent()
            assertTrue(model.state.value.canEdit)
            assertEquals(4L, model.state.value.rating.value)
            assertEquals("Keep this draft", model.state.value.textInput)
            assertTrue(stars.isEmpty())
            sites.records.value = emptyList()
            runCurrent()
            model.saveRating()
            model.saveText()
            assertFalse(model.state.value.canEdit)
            assertTrue(stars.isEmpty())
            assertTrue(texts.isEmpty())
        }

    /** Clearing the entry stops sources and makes subsequent actions inert. */
    @Test fun clearedEntryCannotRestartReadsOrSaveRetainedDrafts() =
        test {
            val model = model()
            runCurrent()
            model.selectRating(5)
            model.editText("Local only")
            store.clear()
            runCurrent()
            val before = model.state.value
            model.retry()
            model.retryNames()
            model.saveRating()
            model.saveText()
            model.editText("Too late")
            runCurrent()
            assertEquals(before, model.state.value)
            assertTrue(stars.isEmpty())
            assertTrue(texts.isEmpty())
            assertEquals(0, reviews.activeObservers)
        }

    /** Builds one actual controller without any booking prerequisite.
     * @param context Requested workspace presentation.
     * @param key Distinct controller owner for contextual comparisons.
     * @param saved Retained small input primitives.
     */
    private fun model(
        context: Role = Role.ENTRANT,
        key: String = "reviews",
        saved: SavedStateHandle = SavedStateHandle(),
    ): CampsiteReviewsViewModel =
        CampsiteReviewsViewModel(
            actor.id,
            site.id,
            context,
            sites,
            reviews,
            accounts,
            saved,
        ).also { store.put(key, it) }

    /** Supplies a real component record without inventing a saved average.
     * @param author Actual author identity.
     * @param rating Optional saved stars.
     * @param body Optional saved written component.
     */
    private fun review(
        author: String,
        rating: Long?,
        body: String?,
    ): Review =
        Review(site.id, author, rating, body, CampingFixtures.now, CampingFixtures.now)

    /** Bounds every observation and held action to this test's store.
     * @param block Public state/action assertions for the fixed catalogue cases.
     */
    private fun test(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

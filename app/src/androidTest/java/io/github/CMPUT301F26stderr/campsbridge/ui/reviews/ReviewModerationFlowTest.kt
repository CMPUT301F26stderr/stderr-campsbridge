/*
 * Actual saved campsite feedback navigation; no separate administrator review browser.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
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

// UI 测试：从真实的营地页面进入“Ratings and reviews”，点击删除按钮，
// 检查确认对话框、结果提示和保存的数据。

/** Actual saved campsite feedback navigation; no separate administrator review browser. */
class ReviewModerationFlowTest {
    @get:Rule val compose = createComposeRule()

    // 共享数据：测试 App host、一个营地和一条原始评论；fail 和 gate 用来模拟删除失败或卡住。
    private val host = TestAppHost()
    private val site = sampleCampsite("site/moderation ?1", "owner")
    private val original =
        Review(site.id, "author/one", 4, "Keep this independent written feedback.", Instant.EPOCH, Instant.EPOCH)
    private val rows = host.reviews.ratings(site.id)
    private var fail = false
    private var gate: CompletableDeferred<Unit>? = null

    // 每个测试结束后放行卡住的删除，并关闭 host。
    @After fun close() {
        compose.runOnIdle {
            gate?.complete(Unit)
            host.close()
        }
    }

    // 用指定角色登录，准备营地和评论，接上假的删除操作，然后打开这个营地的评分和评论页面。
    private fun openFeedback(role: Role = Role.ADMIN) {
        host.accounts.account.value = sampleAccount(setOf(role))
        host.campsites.records.value = listOf(site)
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        rows.value = listOf(original)
        host.moderation.removeRatingHandler = { id, author, _ -> remove(id, author, rating = true) }
        host.moderation.removeReviewTextHandler = { id, author, _ -> remove(id, author, rating = false) }
        compose.setContent { host.Content() }
        compose.onNodeWithText(site.details.name).performClick()
        click("Ratings and reviews")
    }

    // 假的删除：可以被 gate 卡住或因 fail 抛错，否则只清空这位作者的评分或文字。
    private suspend fun remove(
        siteId: String,
        author: String,
        rating: Boolean,
    ) {
        gate?.await()
        if (fail) throw IOException("offline")
        rows.value =
            rows.value.map {
                if (it.campsiteId != siteId || it.authorId != author) {
                    it
                } else if (rating) {
                    it.copy(rating = null)
                } else {
                    it.copy(body = null)
                }
            }
    }

    // 分别点删除评分和删除文字评论：确认框显示营地 ID 和作者 ID，点 Cancel 后没有删除请求，数据不变。
    @Test fun cancellingEitherComponentDoesNotWrite() {
        openFeedback()
        for (label in listOf("Remove rating", "Remove written review")) {
            click(label)
            compose.onNodeWithText("Campsite ID: ${site.id}").assertIsDisplayed()
            compose.onNodeWithText("Author ID: ${original.authorId}").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
        }
        compose.runOnIdle {
            assertTrue(host.moderation.requests.isEmpty())
            assertEquals(listOf(original), rows.value)
        }
    }

    // 删除评分：确认框显示 4 星；完成后文字还在、显示 No ratings、删除按钮消失；
    // 回到上一页后摘要也更新了。
    @Test fun ratingRemovalRefreshesTheSavedSummaryAndKeepsWrittenFeedback() {
        openFeedback()
        click("Remove rating")
        compose.onNodeWithText("Selected rating: 4 out of 5").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.onNodeWithText("Return to feedback").performClick()
        compose.onNodeWithText(original.body!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No ratings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove rating").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original.copy(rating = null), rows.value.single()) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("No ratings").assertIsDisplayed()
    }

    // 删除文字：完成后文字消失，但 4.0 星的平均分还在，营地本身也没有被删除。
    @Test fun writtenRemovalKeepsTheSavedStarsAndCampsite() {
        openFeedback()
        click("Remove written review")
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.onNodeWithText("Return to feedback").performClick()
        compose.onNodeWithText(original.body!!).assertDoesNotExist()
        compose.onNodeWithText("4.0 / 5 (1 rating)").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original.copy(body = null), rows.value.single())
            assertEquals(listOf(site), host.campsites.records.value)
        }
    }

    // 评分版本：第一次失败后重试，仍然删除原来那位作者的评分（见 failureRetry）。
    @Test fun retryRetainsOriginalRatingAfterTheListChanges() =
        failureRetry(rating = true)

    // 文字版本：第一次失败后重试，仍然删除原来那位作者的文字（见 failureRetry）。
    @Test fun retryRetainsOriginalTextAfterTheListChanges() =
        failureRetry(rating = false)

    // 第一次删除失败 -> 显示重试提示。然后列表里多了另一位作者的评论，
    // Retry 后仍然只删原作者的选中部分，另一位作者不受影响；一共发了两次相同的请求。
    private fun failureRetry(rating: Boolean) {
        fail = true
        openFeedback()
        click(if (rating) "Remove rating" else "Remove written review")
        compose.onNodeWithText("Remove").performClick()
        compose
            .onNodeWithText(
                "Removal did not finish. Retry the same selected feedback component.",
            ).assertIsDisplayed()
        val other = original.copy(authorId = "new-author", rating = 5, body = "Other review")
        compose.runOnIdle {
            rows.value = listOf(other, original)
            fail = false
        }
        compose.onNodeWithText("Author ID: ${original.authorId}").assertIsDisplayed()
        compose.onNodeWithText("Retry removal").performClick()
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(other, rows.value.first())
            assertEquals(if (rating) null else original.rating, rows.value.last().rating)
            assertEquals(if (rating) original.body else null, rows.value.last().body)
            val prefix = if (rating) "rating" else "review"
            assertEquals(List(2) { "$prefix:${site.id}:${original.authorId}" }, host.moderation.requests)
        }
    }

    // 删除卡住时 Remove 和 Cancel 都不能点，也不会提前显示成功；只发一次请求，放行后才显示成功。
    @Test fun heldRemovalCannotBeRepeatedOrReportedAsComplete() {
        gate = CompletableDeferred()
        openFeedback()
        click("Remove rating")
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Selected feedback component removed.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, host.moderation.requests.size)
            gate!!.complete(Unit)
        }
        compose.onNodeWithText("Selected feedback component removed.").assertIsDisplayed()
    }

    // 点了删除评分后评分变成 5 星，再确认：显示原内容已变的说明，不发删除请求。
    @Test fun changedSelectedRatingIsExplainedWithoutRemovingItsReplacement() {
        openFeedback()
        click("Remove rating")
        compose.runOnIdle { rows.value = listOf(original.copy(rating = 5)) }
        compose.onNodeWithText("Remove").performClick()
        compose
            .onNodeWithText(
                "The original component is missing, has changed, or is no longer available to this administrator. No replacement will be removed.",
            ).assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.moderation.requests.isEmpty()) }
    }

    // 普通用户（ENTRANT）能看到评论，但看不到删除按钮，也不会发出删除请求。
    @Test fun entrantFeedbackDoesNotExposeModeration() {
        openFeedback(Role.ENTRANT)
        compose.onNodeWithText(original.body!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove rating").assertDoesNotExist()
        compose.onNodeWithText("Remove written review").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.moderation.requests.isEmpty()) }
    }

    // 先滚动到按钮，再点击。
    private fun click(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
}

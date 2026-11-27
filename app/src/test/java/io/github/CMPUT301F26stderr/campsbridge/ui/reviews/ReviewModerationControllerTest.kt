/*
 * Checks that admins remove a campsite rating or written review independently, with confirmation, retry and recovery.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewModerationControllerTest {
    @get:Rule val main = MainDispatcherRule()

    // 测试：请求后取消不会删除。request -> 状态 CONFIRMING -> dismiss() -> 回到 IDLE，没有任何写入。
    @Test fun cancellationDoesNotWrite() =
        runTest {
            val f = Fixture(backgroundScope)
            f.request()
            assertEquals(ReviewModerationPhase.CONFIRMING, f.controller.state.value.phase)
            f.controller.dismiss()
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
        }

    // 评分和文字各测一次：确认后状态 COMPLETE，只有选中部分变成 null，
    // 另一部分和创建时间不变；两次成功共刷新列表 2 次。
    @Test fun ratingAndTextRemovalPreserveTheIndependentComponentAndCreationTime() =
        runTest {
            val f = Fixture(backgroundScope)
            for (component in ReviewModerationComponent.entries) {
                f.rows.value = listOf(f.original)
                f.request(component)
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
                val result = f.rows.value.single()
                assertEquals(if (component == ReviewModerationComponent.RATING) null else 4L, result.rating)
                assertEquals(if (component == ReviewModerationComponent.TEXT) null else f.original.body, result.body)
                assertEquals(f.original.createdAt, result.createdAt)
                f.controller.dismiss()
            }
            assertEquals(2, f.refreshes)
        }

    // 第一次删除失败 -> FAILED。这时 request 另一位作者会被忽略；
    // 重试后两次写入都是同一作者、同一营地的 RATING，文字还在。
    @Test fun failureAndRetryKeepTheSameAuthorCampsiteAndComponent() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.FAILED, f.controller.state.value.phase)
            f.controller.request(f.original.copy(authorId = "other"), ReviewModerationComponent.TEXT, "Other", null)
            f.failure = null
            f.controller.retry()
            runCurrent()
            assertEquals(listOf("RATING:${f.site.id}:author", "RATING:${f.site.id}:author"), f.writes)
            assertEquals(
                f.original.body,
                f.rows.value
                    .single()
                    .body,
            )
        }

    // 确认前作者改了文字，但我们删的是评分：照样 COMPLETE，新文字不会被覆盖。
    @Test fun changedOtherComponentDoesNotBlockOrOverwriteIt() =
        runTest {
            val f = Fixture(backgroundScope)
            f.request()
            f.rows.value = listOf(f.original.copy(body = "New written feedback"))
            f.controller.confirm()
            runCurrent()
            assertEquals(
                "New written feedback",
                f.rows.value
                    .single()
                    .body,
            )
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
        }

    // 确认前选中的评分或文字被换成新值：状态 UNAVAILABLE，没有写入，不会删掉新内容。
    @Test fun changedSelectedRatingOrTextCannotRemoveAReplacement() =
        runTest {
            for (component in ReviewModerationComponent.entries) {
                val f = Fixture(backgroundScope)
                f.request(component)
                f.rows.value = listOf(f.original.copy(rating = 5, body = "Replacement"))
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
                assertTrue(f.writes.isEmpty())
            }
        }

    // 三种情况：评论没了、评分已经没了、营地没了。都应该 UNAVAILABLE，而且没有写入。
    @Test fun missingAuthorRatingOrCampsiteDoesNotWrite() =
        runTest {
            for (missing in 0..2) {
                val f = Fixture(backgroundScope)
                f.request()
                when (missing) {
                    0 -> f.rows.value = emptyList()
                    1 -> f.rows.value = listOf(f.original.copy(rating = null))
                    2 -> f.sites.records.value = emptyList()
                }
                f.controller.confirm()
                runCurrent()
                assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
                assertTrue(f.writes.isEmpty())
            }
        }

    // 别的营地的评论不能发起请求（还是 IDLE）；
    // 确认前管理员失去 ADMIN 角色 -> UNAVAILABLE，没有写入。
    @Test fun roleLossAndForeignCampsiteCannotSubmit() =
        runTest {
            val f = Fixture(backgroundScope)
            f.controller.request(
                f.original.copy(campsiteId = "foreign"),
                ReviewModerationComponent.RATING,
                "Foreign",
                null,
            )
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            f.request()
            f.accounts.account.value = f.admin.copy(roles = setOf(Role.OWNER))
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.UNAVAILABLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
        }

    // 删除卡住（gate 未放行）时再点确认、重试、换目标、关闭都无效，
    // 状态保持 REMOVING，最后只写入一次。
    @Test fun duplicateClicksCannotRetargetAnInFlightRemoval() =
        runTest {
            val f = Fixture(backgroundScope)
            f.gate = CompletableDeferred()
            f.request()
            f.controller.confirm()
            runCurrent()
            f.controller.confirm()
            f.controller.retry()
            f.controller.request(f.original.copy(authorId = "other"), ReviewModerationComponent.TEXT, "Other", null)
            f.controller.dismiss()
            assertEquals(ReviewModerationPhase.REMOVING, f.controller.state.value.phase)
            f.gate!!.complete(Unit)
            runCurrent()
            assertEquals(1, f.writes.size)
        }

    // 删除其实成功了但返回报错：重新读取发现评分已经没了 -> COMPLETE；之后 retry 不会再写一次。
    @Test fun committedButUnacknowledgedRemovalDoesNotReplayTheWrite() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failAfterCommit = true
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
            f.controller.retry()
            runCurrent()
            assertEquals(1, f.writes.size)
        }

    // 删除文字失败后关闭 controller 再新建一个（模拟页面重建）：目标从 SavedStateHandle 恢复，
    // 不会自动写入；手动 retry 后 COMPLETE，4 星评分保留。
    @Test fun recreationRetainsTheConfirmedTargetWithoutAutomaticallyWriting() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request(ReviewModerationComponent.TEXT)
            f.controller.confirm()
            runCurrent()
            f.controller.close()
            f.controller = f.create()
            runCurrent()
            assertEquals(1, f.writes.size)
            assertEquals(
                "author",
                f.controller.state.value.target
                    ?.authorId,
            )
            f.failure = null
            f.controller.retry()
            runCurrent()
            assertEquals(ReviewModerationPhase.COMPLETE, f.controller.state.value.phase)
            assertEquals(
                4L,
                f.rows.value
                    .single()
                    .rating,
            )
        }

    // 读取评论失败 -> UNKNOWN 且没有写入。重试进行中调用 close()，
    // 迟到的结果被丢弃：状态不变、retry 无效、也没有刷新。
    @Test fun failedReadsAreRetryableWithoutWritingAndClosingStopsLateResults() =
        runTest {
            val f = Fixture(backgroundScope)
            f.reviews.failure(f.site.id).value = IOException("read failed")
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.UNKNOWN, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
            f.reviews.failure(f.site.id).value = null
            f.gate = CompletableDeferred()
            f.controller.retry()
            runCurrent()
            f.controller.close()
            val state = f.controller.state.value
            f.gate!!.complete(Unit)
            runCurrent()
            f.controller.retry()
            assertEquals(state, f.controller.state.value)
            assertEquals(0, f.refreshes)
        }

    // 失败后关闭对话框会保留原请求；这时 request 另一部分被忽略，
    // resume() 打开的还是原来的目标，也没有多写入。
    @Test fun dismissedFailureResumesTheOriginalRequest() =
        runTest {
            val f = Fixture(backgroundScope)
            f.failure = IOException("offline")
            f.request()
            f.controller.confirm()
            runCurrent()
            val target = f.controller.state.value.target
            f.controller.dismiss()
            assertEquals(false, f.controller.state.value.dialogVisible)
            f.request(ReviewModerationComponent.TEXT)
            f.controller.resume()
            assertEquals(target, f.controller.state.value.target)
            assertEquals(true, f.controller.state.value.dialogVisible)
            assertEquals(1, f.writes.size)
        }

    // 页面不允许管理（allowed = false）时，request 和 confirm 都无效，保持 IDLE，没有写入。
    @Test fun nonAdminPresentationCannotRequestOrConfirmModeration() =
        runTest {
            val f = Fixture(backgroundScope)
            f.allowed = false
            f.request()
            f.controller.confirm()
            runCurrent()
            assertEquals(ReviewModerationPhase.IDLE, f.controller.state.value.phase)
            assertTrue(f.writes.isEmpty())
        }

    // 共享测试环境：一个管理员、一个营地、一条 4 星加文字的原始评论和假的 repository。
    // failure、failAfterCommit、gate 分别模拟删除失败、删除成功但返回报错、请求卡住。
    private class Fixture(
        private val scope: CoroutineScope,
    ) {
        val admin = CampingFixtures.account().copy(roles = setOf(Role.ADMIN))
        val site = CampingFixtures.campsite()
        val original = Review(site.id, "author", 4, "Keep this written feedback.", Instant.EPOCH, Instant.EPOCH)
        val accounts = FakeAccountRepository(admin)
        val sites = FakeCampsiteRepository(listOf(site))
        val reviews = FakeReviewRepository()
        val rows = reviews.ratings(site.id).also { it.value = listOf(original) }
        val moderation = FakeModerationRepository()
        val saved = SavedStateHandle()
        var allowed = true
        var refreshes = 0
        var failure: Exception? = null
        var failAfterCommit = false
        var gate: CompletableDeferred<Unit>? = null
        val writes = mutableListOf<String>()
        var controller = create()

        init {
            // 把假的删除操作接到 remove()，这样每次删除都会记录在 writes 里。
            moderation.removeRatingHandler =
                { site, author, _ -> remove(site, author, ReviewModerationComponent.RATING) }
            moderation.removeReviewTextHandler =
                { site, author, _ -> remove(site, author, ReviewModerationComponent.TEXT) }
        }

        // 假的删除：记录写入 -> 等 gate -> 按 failure 抛错 -> 修改数据 -> failAfterCommit 时改完再抛错。
        private suspend fun remove(
            site: String,
            author: String,
            component: ReviewModerationComponent,
        ) {
            writes += "$component:$site:$author"
            gate?.await()
            failure?.let { throw it }
            rows.value =
                rows.value.map {
                    if (it.campsiteId != site || it.authorId != author) {
                        it
                    } else if (component == ReviewModerationComponent.RATING) {
                        it.copy(rating = null)
                    } else {
                        it.copy(body = null)
                    }
                }
            if (failAfterCommit) throw IOException("response lost")
        }

        // 用原始评论发起请求，默认删除评分。
        fun request(component: ReviewModerationComponent = ReviewModerationComponent.RATING) =
            controller.request(original, component, site.details.name, "Taylor")

        // 用同一个 SavedStateHandle 创建 controller，重建测试会再调用一次来模拟重建。
        fun create() =
            ReviewModerationController(
                admin.id,
                site.id,
                accounts,
                sites,
                reviews,
                moderation,
                saved,
                scope,
                { allowed },
                { refreshes++ },
            )
    }
}

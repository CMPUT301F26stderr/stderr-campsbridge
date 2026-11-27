/*
 * Fixed collaborating-owner contracts; persistence suites own transaction/race proofs.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Cancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// CollaboratingOwnersViewModel 的单元测试: 用 fake repository 控制数据和保存结果,
// 只检查 ViewModel 的状态和行为; Firestore 事务由其他持久化测试负责。

/** Fixed collaborating-owner contracts; persistence suites own transaction/race proofs. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollaboratingOwnersTest {
    @get:Rule val main = MainDispatcherRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val clock = MutableClock(anchor)

    // 测试账号: Alex 是组织者; Sam(owner-b) 已是成员且有营地; Sam(owner-c) 可以新加;
    // Taylor 有营地但不是成员; entrant 不是营地主, removed 已被删除, 这两个不能被选。
    private val organizer = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-a", name = "Alex")
    private val contributor = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-b", name = "Sam")
    private val addition = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-c", name = "Sam")
    private val other = sampleAccount(setOf(Role.OWNER)).copy(id = "owner-d", name = "Taylor")
    private val entrant = sampleAccount().copy(id = "entrant-e", name = "Sam")
    private val removed = addition.copy(id = "removed", removal = Removal(anchor, "admin"))
    private val profiles = MutableStateFlow(listOf(organizer, contributor, addition, other, entrant, removed))

    // 账号列表来自上面可修改的 profiles, 测试中可以随时删除或加入账号。
    private val accounts =
        object : AccountRepository by FakeAccountRepository() {
            /** Supplies this picker's controlled profile table without implementing persistence. */
            override fun observeAll(): Flow<List<Account>> =
                profiles
        }
    private val siteB = sampleCampsite("site-b", contributor.id)
    private val siteD = sampleCampsite("site-d", other.id)
    private val campsites = FakeCampsiteRepository(listOf(siteB, siteD))
    private val offerB = LotteryOffer(siteB.id, listOf(LocalDate.of(2027, 5, 18)))
    private val offerD = LotteryOffer(siteD.id, listOf(LocalDate.of(2027, 5, 22)))
    private val initialMembers = setOf(organizer.id, contributor.id)

    // 已发布的抽签: 报名在 anchor 之后 1 小时开始, 初始成员是组织者和 owner-b。
    private val lottery =
        Lottery(
            "lottery-a",
            organizer.id,
            "Shared May stays",
            "Quiet stays.",
            LotteryConfiguration(
                anchor.plusSeconds(3600),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(172800),
                5,
                null,
            ),
            0,
            anchor,
            anchor,
            participantOwnerIds = initialMembers,
        )
    private val lotteries = FakeLotteryRepository(listOf(lottery))

    // 记录每次 updateParticipants 调用, 用来检查保存了什么、保存了几次。
    private val requests = mutableListOf<Triple<String, String, Set<String>>>()
    private val store = ViewModelStore()
    private var modelNumber = 0

    // 每个测试前: owner-b 的营地 site-b 在抽签里; 假的保存操作会记录请求并更新抽签成员。

    /** Supplies one contributor allocation and an explicit fake membership write outcome. */
    @Before fun configure() {
        lotteries.serverOffers[lottery.id] = listOf(offerB)
        lotteries.liveOffers.value = mapOf(lottery.id to listOf(offerB))
        lotteries.updateParticipantsHandler = { id, actor, desired ->
            requests += Triple(id, actor, desired)
            lotteries.records.value =
                lotteries.records.value.map { if (it.id == id) it.copy(participantOwnerIds = desired) else it }
        }
    }

    // 每个测试后: 清除所有 ViewModel, 并检查数据监听都已释放 (没有泄漏)。

    /** Releases the real controller's required saved-context observers. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, lotteries.activeRecordObservers)
        assertEquals(0, lotteries.activeOfferObservers)
    }

    // 证明: 搜索 "sam" 只过滤新的营地主, 已有成员一直显示; 同名的 Sam 会显示 ID。
    // 组织者和有营地的成员不能取消; 成员的账号消失后仍保留在成员里; 整个过程没有保存。

    /** CO-01: search filters new owners, retains member context and explains identity/selection locks. */
    @Test fun filtersNewOwnersWithoutLosingExistingMembersOrTheirLocks() =
        runTest {
            val picker = model()
            runCurrent()
            picker.setQuery("sam")
            val state = picker.state.value
            assertEquals(LotteryTargetLoad.READY, state.targetLoad)
            assertEquals(setOf(organizer.id, contributor.id, addition.id), state.owners.map { it.id }.toSet())
            val organizerRow = state.owners.single { it.id == organizer.id }
            val contributedRow = state.owners.single { it.id == contributor.id }
            assertTrue(organizerRow.isOrganizer)
            assertFalse(organizerRow.canChange)
            assertTrue(contributedRow.hasAllocations)
            assertFalse(contributedRow.canChange)
            assertTrue(state.owners.filter { it.id in setOf(contributor.id, addition.id) }.all { it.showId })
            picker.setOwnerSelected(organizer.id, false)
            picker.setOwnerSelected(contributor.id, false)
            assertEquals(initialMembers, picker.state.value.selectedOwnerIds)
            profiles.value = profiles.value.filterNot { it.id == contributor.id }
            runCurrent()
            assertEquals(
                null,
                picker.state.value.owners
                    .single { it.id == contributor.id }
                    .name,
            )
            assertTrue(contributor.id in picker.state.value.selectedOwnerIds)
            picker.setQuery("no matching owner")
            assertEquals(
                initialMembers,
                picker.state.value.owners
                    .map { it.id }
                    .toSet(),
            )
            assertTrue(requests.isEmpty())
        }

    // 证明: 不是组织者的合作营地主 (owner-b) 不能编辑; 勾选和 Save 都没有效果, 也没有保存请求。

    /** CO-01: participating co-owners cannot submit or alter organizer membership. */
    @Test fun contributorCannotChangeMembership() =
        runTest {
            val picker = model(contributor.id)
            runCurrent()
            assertFalse(picker.state.value.canEdit)
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            assertEquals(initialMembers, picker.state.value.selectedOwnerIds)
            assertTrue(requests.isEmpty())
        }

    // 证明: 勾选只改本地状态, 不会马上保存; 点 Save 后才发出一次请求, 内容是完整成员集合
    // 和组织者 ID; 保存后 completion 变成抽签 ID (页面会返回)。

    /** CO-02: checkbox changes stay local until Save sends the complete set and actual organizer. */
    @Test fun savesTheCompleteDesiredSetOnlyFromItsExplicitAction() =
        runTest {
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            val desired = initialMembers + addition.id
            assertEquals(desired, picker.state.value.selectedOwnerIds)
            assertTrue(requests.isEmpty())
            picker.save()
            runCurrent()
            assertEquals(listOf(Triple(lottery.id, organizer.id, desired)), requests)
            assertEquals(lottery.id, picker.completion.value)
        }

    // 证明: 保存失败 (模拟断网 IOException) 时显示 saveFailed, 搜索文字和勾选都保留, 方便重试。

    /** CO-02: a failed write keeps query and desired membership available for explicit recovery. */
    @Test fun failedSaveRetainsSearchAndSelections() =
        runTest {
            lotteries.updateParticipantsHandler = { id, actor, desired ->
                requests += Triple(id, actor, desired)
                throw IOException("offline")
            }
            val picker = model()
            runCurrent()
            picker.setQuery("sam")
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            assertTrue(picker.state.value.saveFailed)
            assertEquals("sam", picker.state.value.query)
            assertEquals(initialMembers + addition.id, picker.state.value.selectedOwnerIds)
            assertEquals(1, requests.size)
        }

    // 证明: 保存遇到 CONFLICT (别人刚加了有营地的 Taylor) 时, 显示最新成员并提示检查;
    // 本地勾选保留, 但没勾 Taylor 就不能保存。勾上 Taylor 再点 Save 才成功, 共两次请求。

    /** CO-02: conflict reads expose changed membership/allocations while preserving desired local input. */
    @Test fun conflictRefreshRequiresReviewAndAnExplicitResave() =
        runTest {
            val freshMembers = initialMembers + other.id
            lotteries.updateParticipantsHandler = { id, actor, desired ->
                requests += Triple(id, actor, desired)
                lotteries.getHandler = { lottery.copy(participantOwnerIds = freshMembers) }
                lotteries.getOffersHandler = { listOf(offerB, offerD) }
                throw PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Membership changed")
            }
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            picker.save()
            runCurrent()
            val refreshed = picker.state.value
            assertTrue(refreshed.contextChanged)
            assertEquals(freshMembers, refreshed.savedOwnerIds)
            assertEquals(initialMembers + addition.id, refreshed.selectedOwnerIds)
            assertTrue(refreshed.owners.single { it.id == other.id }.hasAllocations)
            assertFalse(refreshed.canSave)
            assertEquals(1, requests.size)
            picker.setOwnerSelected(other.id, true)
            lotteries.updateParticipantsHandler = { id, actor, desired -> requests += Triple(id, actor, desired) }
            picker.save()
            runCurrent()
            assertEquals(2, requests.size)
            assertEquals(Triple(lottery.id, organizer.id, initialMembers + addition.id + other.id), requests.last())
            assertEquals(lottery.id, picker.completion.value)
        }

    // 证明: 报名开始、抽签被取消、抽签不存在, 这三种情况都会禁止保存;
    // 但用户的本地勾选不会丢失, 也没有发出任何保存请求。

    /** CO-03: opening registration, cancellation and unavailable context block Save without losing input. */
    @Test fun changedEligibilityKeepsLocalInputButBlocksSubmission() =
        runTest {
            for (change in listOf("opening", "cancellation", "unavailable")) {
                store.clear()
                runCurrent()
                clock.advance(Duration.between(clock.instant(), anchor))
                lotteries.records.value = listOf(lottery)
                val picker = model()
                runCurrent()
                picker.setOwnerSelected(addition.id, true)
                when (change) {
                    "opening" -> {
                        clock.advance(Duration.ofHours(1))
                    }

                    "cancellation" -> {
                        lotteries.records.value =
                            listOf(lottery.copy(cancellation = Cancellation(anchor, organizer.id)))
                    }

                    "unavailable" -> {
                        lotteries.records.value = emptyList()
                    }
                }
                picker.refreshTime()
                runCurrent()
                picker.save()
                runCurrent()
                assertFalse(picker.state.value.canSave)
                assertEquals(initialMembers + addition.id, picker.state.value.selectedOwnerIds)
                assertTrue(requests.isEmpty())
            }
        }

    // 证明: 没有发布的草稿抽签不受报名时间限制, 过了报名开始时间仍然可以保存成员。

    /** CO-03: an unpublished saved draft remains editable at its configured opening. */
    @Test fun unpublishedDraftKeepsItsExistingEditingRule() =
        runTest {
            lotteries.records.value = listOf(lottery.copy(publishedAt = null))
            val picker = model()
            runCurrent()
            picker.setOwnerSelected(addition.id, true)
            clock.advance(Duration.ofHours(1))
            picker.refreshTime()
            assertTrue(picker.state.value.canSave)
            picker.save()
            runCurrent()
            assertEquals(listOf(Triple(lottery.id, organizer.id, initialMembers + addition.id)), requests)
        }

    // 辅助函数: 用指定用户 (默认是组织者) 创建真实的 ViewModel, 放进 store 以便测试后清理。

    /** Creates the actual picker controller with one controlled actor and shared related records.
     * @param actorId Organizer or participating co-owner identity.
     */
    private fun model(actorId: String = organizer.id): CollaboratingOwnersViewModel =
        CollaboratingOwnersViewModel(
            lottery.id,
            actorId,
            accounts,
            campsites,
            lotteries,
            clock,
            SavedStateHandle(),
        ).also {
            store.put("owners-${modelNumber++}", it)
        }
}

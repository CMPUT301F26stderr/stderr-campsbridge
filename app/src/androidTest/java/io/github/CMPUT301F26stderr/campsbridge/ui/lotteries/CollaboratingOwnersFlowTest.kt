/*
 * Confirmed owner membership across two real hosting roots sharing one saved source.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

// Compose UI 测试: 两个真实的 app 界面 (组织者和新加的营地主) 共用同一个假的抽签数据,
// 检查从界面上保存成员后, 两个营地主都能看到同一个抽签。

/** Confirmed owner membership across two real hosting roots sharing one saved source. */
class CollaboratingOwnersFlowTest {
    @get:Rule val compose = createComposeRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val clock = MutableClock(anchor)
    private val organizer = sampleAccount(setOf(Role.OWNER))
    private val addition = organizer.copy(id = "owner-b", name = "Sam Rivera")
    private val contributor = organizer.copy(id = "owner-c", name = "Taylor")
    private val siteA = sampleCampsite("site-a", organizer.id)
    private val siteC = sampleCampsite("site-c", contributor.id)
    private val campsites = FakeCampsiteRepository(listOf(siteA, siteC))
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
            participantOwnerIds = setOf(organizer.id, contributor.id),
        )
    private val lotteries = FakeLotteryRepository(listOf(lottery))

    // 两个 app 根界面共用 lotteries; showingCoowner 决定显示组织者的还是 Sam Rivera 的界面。
    private val organizerRoot = TestAppHost(campsites, lotteries)
    private val coownerRoot = TestAppHost(campsites, lotteries)
    private val showingCoowner = mutableStateOf(false)

    // 记录每次保存的成员集合, 用来检查只保存了一次以及保存的内容。
    private val memberWrites = mutableListOf<Set<String>>()

    // 测试结束后关闭两个根界面, 释放所有数据监听。

    /** Closes both actual roots and every shared saved-record observer. */
    @After fun close() {
        compose.runOnIdle {
            organizerRoot.close()
            coownerRoot.close()
        }
    }

    // 证明: 组织者打开"Collaborating owners"页面, 自己和有营地的 Taylor 不能取消;
    // 勾选 Sam 后按返回并放弃, 不会保存; 再次勾选 Sam 并点 Save owners, 只保存一次;
    // 切换到 Sam 的界面后, 他的抽签列表里能看到同一个抽签。

    /** CO-04: cancel and Save use the real picker; both owners discover the same saved ID. */
    @Test fun savedMembershipIsSharedAcrossBothHostingLists() {
        // 准备: 两个界面分别以组织者和 Sam 登录; 假的保存操作检查请求来自组织者并更新成员。
        compose.runOnIdle {
            val profiles = MutableStateFlow(listOf(organizer, addition, contributor))
            val accountTable =
                object : AccountRepository by FakeAccountRepository() {
                    /** Supplies the owner identities exposed by the shared picker. */
                    override fun observeAll(): Flow<List<Account>> =
                        profiles
                }
            organizerRoot.accounts.account.value = organizer
            coownerRoot.accounts.account.value = addition
            coownerRoot.identity.pending = CompletableDeferred(addition.id)
            for (root in listOf(organizerRoot, coownerRoot)) {
                root.clock = clock
                root.collaboratingOwnerAccounts = accountTable
            }
            val offers = listOf(siteA, siteC).map { LotteryOffer(it.id, listOf(LocalDate.of(2027, 5, 18))) }
            lotteries.serverOffers[lottery.id] = offers
            lotteries.liveOffers.value = mapOf(lottery.id to offers)
            lotteries.updateParticipantsHandler = { id, actor, desired ->
                assertEquals(organizer.id, actor)
                memberWrites += desired
                lotteries.records.value =
                    lotteries.records.value.map {
                        if (it.id == id) it.copy(participantOwnerIds = desired) else it
                    }
            }
        }
        compose.setContent {
            key(showingCoowner.value) { if (showingCoowner.value) coownerRoot.Content() else organizerRoot.Content() }
        }
        // 组织者进入抽签详情, 打开合作营地主页面, 检查不能修改的行。
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(lottery.title).performClick()
        compose.onNodeWithText("Collaborating owners").performScrollTo().performClick()
        compose.onNodeWithText(organizer.name).assertIsNotEnabled()
        compose.onNodeWithText("Organizer").assertIsDisplayed()
        compose.onNodeWithText(contributor.name).assertIsNotEnabled()
        compose.onNodeWithText("Ask this owner to clear their campsites first.").assertIsDisplayed()
        // 勾选 Sam 后按返回并选择放弃: 不应该有任何保存。
        compose.onNodeWithText(addition.name).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.runOnIdle { assertTrue(memberWrites.isEmpty()) }
        // 重新打开: 之前的勾选已被放弃 (Sam 是未勾选); 这次勾选 Sam 并点 Save owners。
        compose.onNodeWithText("Collaborating owners").performScrollTo().performClick()
        compose.onNodeWithText(addition.name).assertIsOff().performClick()
        compose.onNodeWithText("Save owners").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(lottery.title).assertIsDisplayed()
        // 切换到 Sam 的界面: 他现在是成员, 能在抽签列表里打开同一个抽签。
        compose.runOnIdle { showingCoowner.value = true }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(lottery.title).assertIsDisplayed().performClick()
        compose.onNodeWithText("Included campsites").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(setOf(organizer.id, contributor.id, addition.id)), memberWrites)
            assertTrue(lotteries.participantRequests.containsAll(listOf(organizer.id, addition.id)))
        }
    }
}

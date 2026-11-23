/*
 * ML-02 and ML-03 use actual Logs/role navigation over one unchanged signed-in identity.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesGuestCapacity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** ML-02 and ML-03 use actual Logs/role navigation over one unchanged signed-in identity. */
class MessageLogFlowTest {
    @get:Rule val compose = createComposeRule()

    // TestAppHost 用 fake repository 运行真实的 app 界面和导航，测试可以直接改里面的数据。
    private val host = TestAppHost()

    /** Releases only this root's retained feature models and readers. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    // 证明（ML-02 UI 测试）：管理员在 Logs > Messages 搜索并打开详情，看到同一预订的整段对话，
    // 新回复实时出现；返回后和模拟重建后，标签和搜索词都保留；整个过程不发送、不标记已读。

    /** ML-02: Messages search/detail/back/recreation retain selection without participant writes. */
    @Test fun realMessageLogKeepsItsTabQueryAndReadOnlyInspectionThroughRestoration() {
        // 准备数据：管理员账号、一个营地和它的预订；owner 发了一条消息，entrant 回复，
        // 另外有一条属于别的预订的消息，它不应该出现在这段对话里。
        val admin = sampleAccount(setOf(Role.ADMIN))
        val site = sampleCampsite("site/B ?1", "owner-B")
        val stay = previewReservation().copy(id = "stay/B ?1", campsiteId = site.id, entrantId = "entrant-E")
        val message =
            Message(
                "message/B ?1",
                stay.id,
                site.ownerId,
                "Meet at the east gate.\nWe will show you your pitch.",
                Instant.EPOCH,
            )
        var sends = 0
        host.accounts.account.value = admin
        host.accounts.getHandler =
            { id ->
                when (id) {
                    admin.id -> admin
                    site.ownerId -> sampleAccount(setOf(Role.OWNER)).copy(id = id, name = "Jordan Host")
                    else -> null
                }
            }
        host.campsites.records.value = listOf(site)
        host.bookings.records.value = listOf(stay)
        val reply =
            message.copy(
                id = "reply/B ?1",
                senderId = stay.entrantId,
                body = "Thank you. We arrive at noon.",
                createdAt = Instant.EPOCH.plusSeconds(60),
            )
        val unrelated =
            message.copy(
                id = "other-message",
                reservationId = "other-stay",
                body = "Other reservation content.",
            )
        host.messages.records.value = listOf(reply, unrelated, message)
        // 如果日志页面尝试发送消息，就计数并报错；正确的结果是一次都不发送。
        host.messages.sendHandler = { _, _, _, _ ->
            sends++
            error("Logs cannot send")
        }
        // StateRestorationTester 之后用来模拟系统重建界面。
        val restoration = StateRestorationTester(compose)
        restoration.setContent { host.Content() }
        // 进入 Logs 的 Messages 标签，搜索 east gate，打开那条消息：详情里有原消息和回复，没有无关消息。
        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("Messages").performClick()
        compose.onNodeWithText("Search").performTextInput("east gate")
        compose.onNodeWithTag("admin-message-${message.id}").performScrollTo().performClick()
        compose.onNodeWithText("Message details").assertIsDisplayed()
        compose.onNodeWithText(message.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(reply.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(unrelated.body).assertDoesNotExist()
        // 详情页打开时 repository 多了一条新回复，页面应该马上显示出来。
        val followUp =
            reply.copy(
                id = "follow-up",
                body = "We will bring our booking confirmation.",
                createdAt = Instant.EPOCH.plusSeconds(120),
            )
        compose.runOnIdle {
            host.messages.records.value = listOf(followUp, reply, unrelated, message)
        }
        compose.onNodeWithText(followUp.body).performScrollTo().assertIsDisplayed()
        // 返回列表：仍然在 Messages 标签，搜索词还在；模拟重建以后也一样。
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Messages").assertIsSelected()
        compose.onNodeWithText("east gate").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Messages").assertIsSelected()
        compose.onNodeWithText("east gate").assertIsDisplayed()
        // 切到 Notifications 标签再切回来，原来的消息仍然能看到。
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Filter notification events").assertIsDisplayed()
        compose.onNodeWithText("Messages").performClick()
        compose.onNodeWithTag("admin-message-${message.id}").performScrollTo().assertIsDisplayed()
        // 最后确认整个过程只读：没有发送消息，也没有把消息或通知标记为已读。
        compose.runOnIdle {
            assertEquals(0, sends)
            assertTrue(host.messages.readRequests.isEmpty())
            assertTrue(host.notifications.readRequests.isEmpty())
        }
    }
}

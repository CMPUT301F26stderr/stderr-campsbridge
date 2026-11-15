package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AccountRemovalViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** AR-04 uses actual Activity recreation and its root operation owner beyond profile disposal. */
class AccountRemovalFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val host = TestAppHost()
    private var callback: ActivityLifecycleCallback? = null
    private val roots = mutableListOf<AccountRemovalViewModel>()
    private val gate = CompletableDeferred<Unit>()

    /** Releases only this test's Activity observer, held operation and shared root. */
    @After fun close() {
        callback?.let { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(it) }
        gate.complete(Unit)
        compose.runOnIdle { host.close() }
    }

    /** AR-04: marking removes the feature graph, while pending cleanup survives Activity recreation. */
    @Test fun profileDeletionKeepsRootProgressAndRetryAfterUnavailableAndActivityRecreation() {
        val actor = checkNotNull(host.accounts.account.value)
        val calls = mutableListOf<Pair<String, String>>()
        host.moderation.removeAccountHandler = { target, sender, _ ->
            calls += target to sender
            host.accounts.account.value = actor.copy(removal = Removal(Instant.parse("2027-05-10T18:00:00Z"), sender))
            gate.await()
        }
        val render: (ComponentActivity) -> Unit = { activity ->
            val factory =
                viewModelFactory {
                    initializer {
                        AccountRemovalViewModel(host.identity, host.accounts, host.moderation, createSavedStateHandle())
                    }
                }
            val model = ViewModelProvider(activity, factory)[AccountRemovalViewModel::class.java]
            roots += model
            activity.setContent { host.Content(accountRemoval = model) }
        }
        callback =
            ActivityLifecycleCallback { activity, stage ->
                if (stage == Stage.CREATED && activity is ComponentActivity) render(activity)
            }.also { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(it) }
        compose.activityRule.scenario.onActivity(render)
        compose.onNode(hasText("Profile") and hasClickAction()).performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Delete profile").performClick()
        compose.onNodeWithText("Delete permanently").performClick()
        compose.onNodeWithText("Removing profile…").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Removing profile…").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(roots.first(), roots.last())
            assertEquals(listOf(actor.id to actor.id), calls)
            gate.completeExceptionally(IOException("cleanup offline"))
        }
        compose.onNodeWithText("Retry cleanup").assertIsDisplayed()
        compose.runOnIdle {
            host.moderation.removeAccountHandler = { target, sender, _ -> calls += target to sender }
        }
        compose.onNodeWithText("Retry cleanup").performClick()
        compose.onNodeWithText("Account unavailable").assertIsDisplayed()
        compose.runOnIdle { assertEquals(List(2) { actor.id to actor.id }, calls) }
    }
}

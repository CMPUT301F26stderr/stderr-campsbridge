package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Retains existing discovery exercise contracts independently from later feature examples. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryTaskExamplesTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Exercises one search binding independently of its unfinished peers. */
    @Test fun discoverySearchIsIsolatedFromCapacityAndOrdering() =
        runTest {
            val calls = mutableListOf<String>()
            val session =
                TaskExampleSession(
                    TaskExample.TEXT_MATCH,
                    TaskExampleBindings(matchesText = { site, query ->
                        calls.add(query)
                        site.id == "task-river"
                    }),
                    files.root,
                )
            try {
                val model = session.explore()
                runCurrent()
                model.editQuery("river")
                model.submitSearch()
                runCurrent()
                val result = model.state.value.result as ExploreResult.Content
                assertEquals(listOf("task-river"), result.campsites.map { it.id })
                assertFalse(result.orderingPending)
                assertTrue("river" in calls)
                assertTrue(session.bindings.isConnected(TaskExample.TEXT_MATCH))
                assertFalse(session.bindings.isConnected(TaskExample.GUEST_MATCH))
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** Missing assigned behavior renders the real controller's pending state without a fallback. */
    @Test fun unfinishedSearchDoesNotPretendToMatch() =
        runTest {
            val session =
                TaskExampleSession(TaskExample.TEXT_MATCH, TaskExampleBindings(), files.root)
            try {
                val model = session.explore()
                runCurrent()
                assertEquals(ExploreResult.HelperPending, model.state.value.result)
            } finally {
                session.close()
                runCurrent()
            }
        }
}

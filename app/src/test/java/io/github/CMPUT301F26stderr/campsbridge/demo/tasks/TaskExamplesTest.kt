package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreResult
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Checks task examples against real controllers without supplying reserved algorithms. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskExamplesTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** Only the accepted B1/B2 examples are available; all actual helper ports remain deliberately absent. */
    @Test fun catalogueDistinguishesReadyAndFutureScaffolds() {
        assertEquals(23, TaskExample.entries.size)
        assertEquals(
            23,
            TaskExample.entries
                .map { it.id }
                .toSet()
                .size,
        )
        assertFalse(TaskExample.entries.any { it.id == "H14" })
        assertEquals(
            setOf("H01", "H02", "H03", "H04"),
            TaskExample.entries
                .filter {
                    it.scaffoldReady
                }.map { it.id }
                .toSet(),
        )
        TaskExample.entries.forEach { assertFalse(taskExampleBindings().isConnected(it)) }
    }

    /** Missing name validation leaves the editor pending and the sample account unchanged. */
    @Test fun missingNameLeavesOnlyItsSavePending() =
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

    /** Profile saving consumes the supplied parser result through the actual local repository. */
    @Test fun nameExampleUsesRealProfileSaveWithInjectedResult() =
        runTest {
            val session =
                TaskExampleSession(
                    TaskExample.DISPLAY_NAME,
                    TaskExampleBindings(parseName = {
                        DisplayNameInput.Valid("Contract result")
                    }),
                    files.root,
                )
            try {
                val model = session.profile()
                runCurrent()
                model.beginPersonalEdit()
                model.editName("raw input")
                model.saveName()
                runCurrent()
                assertEquals(
                    "Contract result",
                    session.accounts.get(TaskExampleData.ACCOUNT_ID)?.name,
                )
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** Reset replaces controllers and local records, preserving only the selected task. */
    @Test fun resetDiscardsThePreviousExample() =
        runTest {
            val host = TaskExamplesHostViewModel(files.root, TaskExampleBindings())
            val store = ViewModelStore().apply { put("host", host) }
            try {
                host.open(TaskExample.TEXT_MATCH)
                val previous = host.session.value!!
                previous.explore().editQuery("changed")
                runCurrent()
                host.reset()
                runCurrent()
                assertNotSame(previous, host.session.value)
                assertTrue(previous.isClosed)
                assertEquals(TaskExample.TEXT_MATCH, host.session.value!!.example)
                assertEquals(
                    "",
                    host.session.value!!
                        .explore()
                        .state.value.queryText,
                )
                host.showCatalogue()
                assertNull(host.session.value)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}

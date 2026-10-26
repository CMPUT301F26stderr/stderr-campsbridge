package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
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

/** Six frozen contracts for the B1 task host, using fixed results rather than parser algorithms. */
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
        TaskExample.entries.forEach { assertFalse(TaskExampleBindings().isConnected(it)) }
    }

    /** Missing name validation leaves the editor pending and the sample account unchanged. */
    @Test fun missingNameLeavesOnlyItsSavePending() =
        runTest {
            val session =
                TaskExampleSession(TaskExample.DISPLAY_NAME, TaskExampleBindings(), files.root)
            try {
                val model = session.profile()
                runCurrent()
                model.beginPersonalEdit()
                model.editName("changed")
                model.saveName()
                runCurrent()
                val content = model.state.value as ProfileUiState.Content
                assertTrue(content.nameValidationPending)
                assertEquals("changed", content.personalDraft?.nameText)
                assertEquals(
                    TaskExampleData.account,
                    session.accounts.get(TaskExampleData.ACCOUNT_ID),
                )
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** The real writer saves exactly the supplied result and retains unrelated account facts. */
    @Test fun suppliedNameResultPersistsWithoutChangingContactsRolesOrSettings() =
        runTest {
            val session =
                TaskExampleSession(
                    TaskExample.DISPLAY_NAME,
                    TaskExampleBindings(parseName = { DisplayNameInput.Valid("Contract result") }),
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
                    TaskExampleData.account.copy(name = "Contract result"),
                    session.accounts.get(TaskExampleData.ACCOUNT_ID),
                )
                assertTrue(session.bindings.isConnected(TaskExample.DISPLAY_NAME))
                assertFalse(session.bindings.isConnected(TaskExample.CONTACT))
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** Supplied contact results clear email while preserving unrelated fields. */
    @Test fun suppliedContactResultsPersistAndClearWithoutChangingOtherFacts() =
        runTest {
            val session =
                TaskExampleSession(
                    TaskExample.CONTACT,
                    TaskExampleBindings(
                        parseEmail = { OptionalContactInput.Valid(null) },
                        parsePhone = { OptionalContactInput.Valid("+1 (780) 555-0100") },
                    ),
                    files.root,
                )
            try {
                val model = session.profile()
                runCurrent()
                model.beginContactEdit()
                model.editEmail("")
                model.editPhone("raw input")
                model.saveContacts()
                runCurrent()
                assertEquals(
                    TaskExampleData.account.copy(email = null, phone = "+1 (780) 555-0100"),
                    session.accounts.get(TaskExampleData.ACCOUNT_ID),
                )
                assertTrue(session.bindings.isConnected(TaskExample.CONTACT))
                assertFalse(session.bindings.isConnected(TaskExample.DISPLAY_NAME))
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** Reset closes the prior graph and restores its selected task and sample input. */
    @Test fun resetReplacesControllersAndRestoresTheSelectedFixture() =
        runTest {
            val host = TaskExamplesHostViewModel(files.root, TaskExampleBindings())
            try {
                host.open(TaskExample.DISPLAY_NAME)
                val previous = checkNotNull(host.session.value)
                val model = previous.profile()
                runCurrent()
                model.beginPersonalEdit()
                model.editName("changed")
                host.reset()
                runCurrent()
                val current = checkNotNull(host.session.value)
                assertNotSame(previous, current)
                assertTrue(previous.isClosed)
                assertEquals(TaskExample.DISPLAY_NAME, current.example)
                assertEquals(
                    TaskExampleData.account,
                    current.accounts.get(TaskExampleData.ACCOUNT_ID),
                )
            } finally {
                host.showCatalogue()
                runCurrent()
            }
        }

    /** Catalogue return and actual ViewModel retirement release their selected graph. */
    @Test fun catalogueAndHostRetirementCloseTheirCurrentSessions() =
        runTest {
            val owner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            val host =
                ViewModelProvider(
                    owner,
                    viewModelFactory {
                        initializer { TaskExamplesHostViewModel(files.root, TaskExampleBindings()) }
                    },
                )[TaskExamplesHostViewModel::class.java]
            try {
                host.open(TaskExample.DISPLAY_NAME)
                val first = checkNotNull(host.session.value)
                host.showCatalogue()
                assertTrue(first.isClosed)
                assertNull(host.session.value)
                host.open(TaskExample.CONTACT)
                val second = checkNotNull(host.session.value)
                owner.viewModelStore.clear()
                runCurrent()
                assertTrue(second.isClosed)
                assertNull(host.session.value)
            } finally {
                owner.viewModelStore.clear()
                runCurrent()
            }
        }
}

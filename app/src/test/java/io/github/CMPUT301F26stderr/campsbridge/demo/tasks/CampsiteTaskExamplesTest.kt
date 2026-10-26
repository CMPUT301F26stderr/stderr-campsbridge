package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Checks B2 example wiring using literal supplied outcomes, never helper algorithms. */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteTaskExamplesTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val files = TemporaryFolder()

    /** The real owner list consumes only this task's supplied ordering and exact original records. */
    @Test fun suppliedOrderingReachesTheOwnerListWithoutPeerHelpers() =
        runTest {
            val expected =
                listOf(TaskExampleData.sites[1], TaskExampleData.sites[2], TaskExampleData.sites[0])
            var calls = 0
            val session =
                TaskExampleSession(
                    TaskExample.CAMPSITE_ORDER,
                    TaskExampleBindings(orderCampsites = {
                        assertEquals(TaskExampleData.sites, it)
                        calls++
                        expected
                    }),
                    files.root,
                )
            try {
                val model = session.campsiteList()
                runCurrent()
                val content = model.state.value as CampsiteListUiState.Content
                assertFalse(content.orderingPending)
                assertEquals(expected, content.campsites)
                assertTrue(calls > 0)
                assertFalse(session.bindings.isConnected(TaskExample.AVAILABILITY))
            } finally {
                session.close()
                runCurrent()
            }
        }

    /** The real editor writes the supplied changed date and preserves the booked sample night. */
    @Test fun suppliedDifferencesReachTheLocalWriterAndProtectTheBookedDate() =
        runTest {
            val date = TaskExampleData.today.plusDays(1)
            val booked = TaskExampleData.today.plusDays(3)
            var calls = 0
            val session =
                TaskExampleSession(
                    TaskExample.AVAILABILITY,
                    TaskExampleBindings(remainingEdits = { requested, confirmed ->
                        assertEquals(mapOf(date to false), requested)
                        assertEquals(mapOf(date to true), confirmed)
                        calls++
                        mapOf(date to false)
                    }),
                    files.root,
                )
            try {
                val model = session.availability()
                runCurrent()
                model.selectDates(setOf(date, booked))
                assertEquals(
                    setOf(date),
                    (model.state.value as AvailabilityUiState.Content).selectedDates,
                )
                model.setRequestedOffered(false)
                model.save()
                runCurrent()
                val content = model.state.value as AvailabilityUiState.Content
                assertTrue(content.saved)
                val nights =
                    (content.availability.inventory as CampsiteInventoryUiState.Content)
                        .nights
                assertFalse(nights.single { it.date == date }.isOffered)
                assertEquals("task-occupied", nights.single { it.date == booked }.reservationId)
                assertTrue(content.availability.requested.isEmpty())
                assertTrue(calls > 0)
                assertFalse(session.bindings.isConnected(TaskExample.CAMPSITE_ORDER))
            } finally {
                session.close()
                runCurrent()
            }
        }
}

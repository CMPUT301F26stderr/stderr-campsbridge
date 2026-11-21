package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.AppDisplayTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Fixed saved-lottery contracts for configuration boundaries, raw input and context review. */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDetailsEditTest {
    @get:Rule val main = MainDispatcherRule()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val configuration =
        LotteryConfiguration(
            anchor.plus(Duration.ofHours(1)),
            anchor.plus(Duration.ofDays(1)),
            anchor.plus(Duration.ofDays(1)),
            anchor.plus(Duration.ofDays(2)),
            5,
            null,
        )
    private val lottery =
        Lottery("lottery-a", "owner-a", "May family stays", "Quiet stays.", configuration, 0, anchor, anchor)
    private val repository = FakeLotteryRepository(listOf(lottery))
    private val clock = MutableClock(anchor)
    private val store = ViewModelStore()
    private var modelNumber = 0

    /** Configures an acknowledged update without implementing Firebase transaction behavior. */
    @Before fun configureSave() {
        repository.updateHandler = { request ->
            repository.records.value =
                repository.records.value.map {
                    if (it.id ==
                        request.id
                    ) {
                        it.copy(
                            title = request.title,
                            description = request.description,
                            configuration = request.configuration,
                        )
                    } else {
                        it
                    }
                }
        }
    }

    /** Releases all real controller subscriptions. */
    @After fun close() {
        store.clear()
        main.dispatcher.scheduler.runCurrent()
        assertEquals(0, repository.activeRecordObservers)
    }

    /** LD-02: published controls freeze exactly at opening; drafts retain their editing rule. */
    @Test fun freezesPublishedConfigurationAtRegistrationOpening() =
        runTest {
            val editor = model()
            runCurrent()
            assertTrue(content(editor).configurationEditable)
            clock.advance(Duration.ofHours(1))
            editor.refreshTime()
            assertFalse(content(editor).configurationEditable)
            clock.advance(Duration.ofMinutes(1))
            editor.refreshTime()
            assertFalse(content(editor).configurationEditable)
            repository.records.value = listOf(lottery.copy(publishedAt = null))
            runCurrent()
            val draftEditor = model()
            runCurrent()
            assertTrue(content(draftEditor).configurationEditable)
            draftEditor.editWinnerCount("7")
            draftEditor.save()
            runCurrent()
            assertEquals(
                7L,
                repository.detailsRequests
                    .single()
                    .configuration.winnerCount,
            )
        }

    /** LD-02: crossing the deadline never submits a previously edited schedule or count. */
    @Test fun descriptiveSaveAfterFreezeCarriesTheCurrentSavedConfiguration() =
        runTest {
            val editor = model()
            runCurrent()
            editor.editWinnerCount("7")
            val close = configuration.registrationClosesAt.atZone(AppDisplayTime.zone)
            editor.editScheduleDate(LotterySchedulePoint.REGISTRATION_CLOSE, close.toLocalDate().plusDays(1))
            clock.advance(Duration.ofHours(1))
            editor.refreshTime()
            editor.editDescription("Please arrive before sunset.")
            editor.save()
            runCurrent()
            assertEquals(configuration, repository.detailsRequests.single().configuration)
            assertEquals("Please arrive before sunset.", repository.detailsRequests.single().description)
            assertEquals(lottery.id, editor.completion.value)
        }

    /** LD-03: the agreed invalid vectors block persistence; optional positive capacity is explicit. */
    @Test fun validatesWindowWinnerAndCapacityWiring() =
        runTest {
            for (invalid in listOf("window", "winner", "capacity")) {
                val editor = model()
                runCurrent()
                when (invalid) {
                    "window" -> {
                        val beforeOpen = configuration.registrationOpensAt.minusSeconds(60).atZone(AppDisplayTime.zone)
                        editor.editScheduleDate(LotterySchedulePoint.REGISTRATION_CLOSE, beforeOpen.toLocalDate())
                        editor.editScheduleTime(LotterySchedulePoint.REGISTRATION_CLOSE, beforeOpen.toLocalTime())
                    }

                    "winner" -> {
                        editor.editWinnerCount("")
                    }

                    "capacity" -> {
                        editor.editWaitlistCapacity("0")
                    }
                }
                editor.save()
                runCurrent()
                val errors = content(editor).errors
                when (invalid) {
                    "window" -> assertTrue(errors.windows != null)
                    "winner" -> assertTrue(errors.winnerCount != null)
                    "capacity" -> assertTrue(errors.capacity != null)
                }
            }
            assertTrue(repository.detailsRequests.isEmpty())
            for ((raw, expected) in listOf("" to null, "7" to 7L)) {
                val editor = model()
                runCurrent()
                editor.editDescription("Capacity choice: $raw")
                editor.editWaitlistCapacity(raw)
                editor.save()
                runCurrent()
                assertEquals(
                    expected,
                    repository.detailsRequests
                        .last()
                        .configuration.waitlistCapacity,
                )
            }
        }

    /** LD-03: failed updates and reconstruction retain raw spelling without another submission. */
    @Test fun failedSaveAndReconstructionKeepRawInput() =
        runTest {
            repository.updateHandler = { throw IOException("offline") }
            val saved = SavedStateHandle()
            val editor = model(saved)
            runCurrent()
            editor.editTitle("  May family stays  ")
            editor.editWinnerCount(" 7 ")
            editor.editDescription("Arrive before sunset.\nLeave no trace.")
            editor.save()
            runCurrent()
            assertTrue(content(editor).saveFailed)
            val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            runCurrent()
            assertEquals(content(editor).draft, content(restored).draft)
            assertEquals("  May family stays  ", content(restored).draft.title)
            assertEquals(" 7 ", content(restored).draft.maximumWinnersText)
            assertEquals(1, repository.detailsRequests.size)
            assertNull(restored.completion.value)
        }

    /** LD-04: changed saved facts require review before a second explicit Save. */
    @Test fun changedContextRetainsDescriptionsUntilAnExplicitResave() =
        runTest {
            val editor = model()
            runCurrent()
            editor.editDescription("Please arrive before sunset.")
            val changed =
                lottery.copy(
                    title = "Updated saved title",
                    configuration = configuration.copy(winnerCount = 6),
                )
            repository.records.value = listOf(changed)
            runCurrent()
            editor.save()
            runCurrent()
            assertTrue(content(editor).contextChanged)
            assertEquals("Please arrive before sunset.", content(editor).draft.description)
            assertEquals(changed, content(editor).lottery)
            assertTrue(repository.detailsRequests.isEmpty())
            editor.save()
            runCurrent()
            assertEquals("Please arrive before sunset.", repository.detailsRequests.single().description)
            assertEquals(lottery.id, editor.completion.value)
        }

    /** LD-04: an uncertain update completes only when a saved read confirms its entire request. */
    @Test fun lostAcknowledgmentReconcilesAnIdenticalSavedRequest() =
        runTest {
            var committed: Lottery? = null
            repository.getHandler = { committed ?: lottery }
            repository.updateHandler = {
                committed =
                    lottery.copy(title = it.title, description = it.description, configuration = it.configuration)
                throw IOException("acknowledgment lost")
            }
            val editor = model()
            runCurrent()
            editor.editDescription("Please arrive before sunset.")
            editor.save()
            runCurrent()
            assertEquals(lottery.id, editor.completion.value)
            assertEquals(1, repository.detailsRequests.size)
            assertFalse(content(editor).saveFailed)
        }

    /** LD-04: failed or different reconciliation reads preserve recovery rather than report success. */
    @Test fun failedOrDifferentRecoveryReadKeepsTheRequestedInput() =
        runTest {
            for (outcome in listOf("failed", "different")) {
                repository.records.value = listOf(lottery)
                repository.detailsRequests.clear()
                var attempted = false
                repository.updateHandler = {
                    attempted = true
                    throw IOException("unconfirmed")
                }
                repository.getHandler = {
                    if (attempted && outcome == "failed") throw IOException("read failed")
                    if (attempted) lottery.copy(description = "Another saved description") else lottery
                }
                val editor = model()
                runCurrent()
                editor.editDescription("Please arrive before sunset.")
                editor.save()
                runCurrent()
                assertNull(editor.completion.value)
                assertTrue(content(editor).saveFailed)
                assertEquals("Please arrive before sunset.", content(editor).draft.description)
            }
        }

    /** Creates a real editor whose lifetime is explicitly closed by the case.
     * @param saved Primitive raw input to restore.
     */
    private fun model(saved: SavedStateHandle = SavedStateHandle()): LotteryDetailsEditViewModel =
        LotteryDetailsEditViewModel(lottery.id, lottery.ownerId, repository, clock, saved).also {
            store.put("details-${modelNumber++}", it)
        }

    /** Requires the actual editable state rather than interpreting loading as an empty form.
     * @param editor Current controller.
     */
    private fun content(editor: LotteryDetailsEditViewModel): LotteryDetailsEditUiState.Content {
        assertTrue(
            "Expected saved details content, got ${editor.state.value}",
            editor.state.value is LotteryDetailsEditUiState.Content,
        )
        return editor.state.value as LotteryDetailsEditUiState.Content
    }
}

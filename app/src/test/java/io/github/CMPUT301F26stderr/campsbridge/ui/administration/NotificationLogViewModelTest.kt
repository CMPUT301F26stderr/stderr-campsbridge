package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContent
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationSubjectContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Fixed list, original-event, optional-context and graph-lifetime contracts. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Loading remains distinct from the first confirmed empty result. */
    @Test fun loadingBecomesConfirmedEmpty() =
        logTest { f ->
            val model = f.model()
            assertEquals(NotificationLogEvents.Loading, model.state.value.events)
            runCurrent()
            assertEquals(NotificationLogEvents.Content(emptyList()), model.state.value.events)
            assertEquals(1, f.activeLists)
        }

    /** Source failure retains inputs and repeated retry opens one replacement observation. */
    @Test fun failedListRetriesWithoutLosingFilters() =
        logTest { f ->
            f.failure.value = IOException("offline")
            val model = f.model()
            model.editQuery(" trail ")
            model.selectType(NotificationType.OWNER_ANNOUNCEMENT)
            runCurrent()
            assertEquals(NotificationLogEvents.Failure, model.state.value.events)
            f.failure.value = null
            f.notifications.records.value = listOf(logEvent())
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(" trail ", model.state.value.query)
            assertEquals(listOf(logEvent()), (model.state.value.events as NotificationLogEvents.Content).events)
            assertEquals(2, f.listRequests)
            assertEquals(1, f.activeLists)
        }

    /** Filtering is literal, case-insensitive and local to stored event facts. */
    @Test fun queryAndTypeFilterLocallyWithoutContextReads() =
        logTest { f ->
            val announcement = logEvent()
            val system =
                logEvent("winner", NotificationType.LOTTERY_WON).copy(
                    subject = NotificationSubject(SubjectType.LOTTERY, "other-subject"),
                )
            f.notifications.records.value = listOf(announcement, system)
            val model = f.model()
            runCurrent()
            for (query in listOf("TRAIL", "drinking", "owner", "subject/A", "event/A ?1")) {
                model.editQuery(" $query ")
                assertEquals(listOf(announcement), (model.state.value.events as NotificationLogEvents.Content).events)
            }
            model.editQuery("")
            model.selectType(NotificationType.LOTTERY_WON)
            assertEquals(listOf(system), (model.state.value.events as NotificationLogEvents.Content).events)
            model.editQuery("[.*]")
            assertEquals(NotificationLogEvents.Content(emptyList()), model.state.value.events)
            assertEquals(1, f.listRequests)
            assertTrue(f.notifications.getRequests.isEmpty())
        }

    /** Newest creation time wins, with exact ID ascending for equal instants. */
    @Test fun orderUsesCreationTimeAndExactIdentity() =
        logTest { f ->
            val base = logEvent()
            val early = base.copy(id = "early", createdAt = base.createdAt.minusSeconds(1))
            val a = base.copy(id = "A")
            val b = base.copy(id = "a")
            f.notifications.records.value = listOf(early, b, a)
            val model = f.model()
            runCurrent()
            assertEquals(listOf(a, b, early), (model.state.value.events as NotificationLogEvents.Content).events)
        }

    /** Primitive filters and selected identity survive a new graph lifetime. */
    @Test fun filtersAndSelectionRestoreWithoutSavingRecords() =
        logTest { f ->
            val saved = SavedStateHandle()
            val first = f.model(saved)
            first.editQuery(" raw input ")
            first.selectType(NotificationType.NEW_MESSAGE)
            first.selectEvent("saved / ?id")
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            f.store.clear()
            runCurrent()
            val restored = f.model(SavedStateHandle(values), "restored")
            runCurrent()
            assertEquals(" raw input ", restored.state.value.query)
            assertEquals(NotificationType.NEW_MESSAGE, restored.state.value.type)
            assertEquals("saved / ?id", restored.detail.value.id)
            assertTrue(values.values.all { it == null || it is String })
        }

    /** Invalid saved category text safely means All without rewriting raw query text. */
    @Test fun invalidRestoredTypeIsIgnored() =
        logTest { f ->
            val model =
                f.model(
                    SavedStateHandle(
                        mapOf(
                            "notificationLogType" to "not-a-type",
                            "notificationLogQuery" to " raw ",
                        ),
                    ),
                )
            runCurrent()
            assertNull(model.state.value.type)
            assertEquals(" raw ", model.state.value.query)
        }

    /** The exact selected ID is read once across same-ID child changes. */
    @Test fun selectionReadsExactIdentityAndDoesNotAcknowledge() =
        logTest { f ->
            val event = logEvent()
            f.notifications.records.value = listOf(event)
            val model = f.model()
            runCurrent()
            model.selectEvent(event.id)
            model.selectEvent(event.id)
            runCurrent()
            assertEquals(event, model.detail.value.event)
            assertEquals(listOf(event.id), f.notifications.getRequests)
            assertTrue(f.notifications.readRequests.isEmpty())
            assertTrue(f.notifications.audienceRequests.isEmpty())
        }

    /** List-provided immutable facts stay visible while optional context is held. */
    @Test fun heldContextDoesNotHideKnownEventFacts() =
        logTest { f ->
            val event = logEvent()
            val held = CompletableDeferred<Unit>()
            f.notifications.records.value = listOf(event)
            f.lotteries.getHandler = {
                held.await()
                null
            }
            val model = f.model()
            runCurrent()
            model.selectEvent(event.id)
            runCurrent()
            assertEquals(event, model.detail.value.event)
            assertTrue(model.detail.value.isLoading)
            held.complete(Unit)
            runCurrent()
            assertEquals(
                NotificationContent.Loaded(event, NotificationSubjectContent.Unavailable),
                model.detail.value.content,
            )
        }

    /** Failed optional context preserves event facts and retries without replacing the list. */
    @Test fun failedContextRetryKeepsTheEventAndList() =
        logTest { f ->
            val event = logEvent()
            f.notifications.records.value = listOf(event)
            f.lotteries.getHandler = { throw IOException("context offline") }
            val model = f.model()
            runCurrent()
            model.selectEvent(event.id)
            runCurrent()
            assertEquals(
                NotificationContent.Loaded(event, NotificationSubjectContent.Failure),
                model.detail.value.content,
            )
            f.lotteries.getHandler = { null }
            model.retryDetail()
            model.retryDetail()
            runCurrent()
            assertEquals(
                NotificationContent.Loaded(event, NotificationSubjectContent.Unavailable),
                model.detail.value.content,
            )
            assertEquals(1, f.listRequests)
            assertEquals(2, f.notifications.getRequests.size)
        }

    /** Missing and failed event reads are distinct and never fabricate subject facts. */
    @Test fun eventAbsenceAndFailureRemainDifferent() =
        logTest { f ->
            val model = f.model()
            runCurrent()
            model.selectEvent("missing")
            runCurrent()
            assertEquals(NotificationContent.Unavailable, model.detail.value.content)
            f.notifications.getHandler = { throw IOException("event offline") }
            model.retryDetail()
            runCurrent()
            assertEquals(NotificationContent.Failure, model.detail.value.content)
            assertNull(model.detail.value.event)
        }

    /** A repository result with another event ID cannot replace the selected identity. */
    @Test fun mismatchedEventIsUnavailable() =
        logTest { f ->
            f.notifications.getHandler = { logEvent("other") }
            val model = f.model()
            runCurrent()
            model.selectEvent("wanted")
            runCurrent()
            assertEquals("wanted", model.detail.value.id)
            assertEquals(NotificationContent.Unavailable, model.detail.value.content)
        }

    /** A selected event remains independent of list filters and later aggregate failure. */
    @Test fun listChangesDoNotRetireTheSelectedEvent() =
        logTest { f ->
            val event = logEvent()
            f.notifications.records.value = listOf(event)
            val model = f.model()
            runCurrent()
            model.selectEvent(event.id)
            runCurrent()
            model.editQuery("no match")
            f.failure.value = IOException("list offline")
            runCurrent()
            assertEquals(NotificationLogEvents.Failure, model.state.value.events)
            assertEquals(event, model.detail.value.event)
            assertEquals(listOf(event.id), f.notifications.getRequests)
        }

    /** Retiring or replacing a selected ID rejects a noncooperative late result. */
    @Test fun changingIdentityRejectsLateSelectedReads() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getHandler =
                { id ->
                    if (id == "old") withContext(NonCancellable) { held.await() }
                    logEvent(id)
                }
            val model = f.model()
            runCurrent()
            model.selectEvent("old")
            runCurrent()
            model.selectEvent("new")
            runCurrent()
            held.complete(Unit)
            runCurrent()
            assertEquals(
                "new",
                model.detail.value.event
                    ?.id,
            )
            model.selectEvent(null)
            assertEquals(NotificationLogDetailUiState(), model.detail.value)
        }

    /** Cancellation is not shown as failed or empty data and permits explicit source retry. */
    @Test fun cancelledListCanRetry() =
        logTest { f ->
            f.listHandler = { flow { throw CancellationException("interrupted") } }
            val model = f.model()
            runCurrent()
            assertEquals(NotificationLogEvents.Loading, model.state.value.events)
            f.listHandler = null
            model.retry()
            runCurrent()
            assertEquals(NotificationLogEvents.Content(emptyList()), model.state.value.events)
        }

    /** Clearing the graph stops observations and prevents late selected content from publishing. */
    @Test fun graphClearRetiresAllReads() =
        logTest { f ->
            val held = CompletableDeferred<Unit>()
            f.notifications.getHandler = {
                withContext(NonCancellable) { held.await() }
                logEvent()
            }
            val model = f.model()
            runCurrent()
            model.selectEvent(logEvent().id)
            runCurrent()
            val before = model.detail.value
            f.store.clear()
            held.complete(Unit)
            runCurrent()
            assertEquals(0, f.activeLists)
            assertEquals(before, model.detail.value)
            assertTrue(f.notifications.readRequests.isEmpty())
        }
}

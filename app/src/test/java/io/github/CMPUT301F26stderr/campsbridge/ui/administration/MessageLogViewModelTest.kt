package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** ML-01 fixes message ordering/search and independent linked-read outcomes without writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageLogViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** ML-01: both participant directions have real saved bodies and locally searchable facts. */
    @Test fun realMessagesAreNewestFirstAndSearchableWithoutSendingOrAcknowledging() =
        runTest {
            val stay = CampingFixtures.reservation()
            val messages =
                FakeMessageRepository(
                    listOf(
                        Message("owner-message", stay.id, "owner", "Meet at the east gate.", CampingFixtures.now),
                        Message(
                            "entrant-message",
                            stay.id,
                            "entrant",
                            "We arrive after lunch.\nIs the gate open?",
                            CampingFixtures.now.plusSeconds(1),
                        ),
                    ),
                )
            var sends = 0
            messages.sendHandler = { _, _, _, _ ->
                sends++
                error("Logs cannot send")
            }
            val accounts = FakeAccountRepository()
            accounts.getHandler =
                { CampingFixtures.account(it).copy(name = if (it == "owner") "Jordan Host" else "Alex Camper") }
            val saved = SavedStateHandle()
            val model =
                MessageLogViewModel(
                    messages,
                    FakeBookingRepository(listOf(stay)),
                    FakeCampsiteRepository(listOf(CampingFixtures.campsite())),
                    accounts,
                    saved,
                )
            val store = ViewModelStore()
            store.put("log", model)
            try {
                runCurrent()
                assertEquals(MessageLogRead.READY, model.state.value.read)
                assertEquals(
                    listOf("entrant-message", "owner-message"),
                    model.state.value.rows
                        .map { it.message.id },
                )
                assertEquals(
                    listOf(
                        MessageLogDirection.ENTRANT_TO_OWNER,
                        MessageLogDirection.OWNER_TO_ENTRANT,
                    ),
                    model.state.value.rows.map {
                        it.direction
                    },
                )
                assertEquals(
                    "owner",
                    model.state.value.rows
                        .first()
                        .recipientId,
                )
                assertEquals(
                    "Jordan Host",
                    model.state.value.rows
                        .first()
                        .recipientLabel,
                )
                for (query in listOf("east gate", "Jordan", "OWNER TO ENTRANT", stay.id)) {
                    model.editQuery(query)
                    runCurrent()
                    assertTrue(
                        model.state.value.rows
                            .isNotEmpty(),
                    )
                }
                model.editQuery("does not match")
                runCurrent()
                assertTrue(
                    model.state.value.rows
                        .isEmpty(),
                )
                assertEquals("Meet at the east gate.", model.find("owner-message")?.message?.body)
                model.editQuery("  lunch  ")
                runCurrent()
                assertEquals(
                    listOf("entrant-message"),
                    model.state.value.rows
                        .map { it.message.id },
                )
                assertEquals("  lunch  ", model.state.value.query)
                assertTrue(messages.readRequests.isEmpty())
                assertEquals(0, sends)
            } finally {
                store.clear()
            }
        }

    /** ML-01: missing/failed descriptive facts retain saved messages; primary failure has explicit Retry. */
    @Test fun supplementalAbsenceAndFailureKeepContentWhilePrimaryFailureRemainsRetryable() =
        runTest {
            val stay = CampingFixtures.reservation()
            val message = Message("saved", stay.id, "owner", "Please use the east entrance.", CampingFixtures.now)
            for (vector in listOf(
                "missing-profile",
                "failed-profile",
                "missing-stay",
                "failed-stay",
                "missing-site",
                "failed-site",
                "failed-primary",
            )) {
                val messages = FakeMessageRepository(listOf(message))
                messages.logFailure.value = if (vector == "failed-primary") IOException("log offline") else null
                val bookings = FakeBookingRepository(if (vector == "missing-stay") emptyList() else listOf(stay))
                bookings.getHandler =
                    {
                        if (vector ==
                            "failed-stay"
                        ) {
                            throw IOException("stay offline")
                        }
                        ; if (vector == "missing-stay") null else stay
                    }
                val sites =
                    FakeCampsiteRepository(
                        if (vector ==
                            "missing-site"
                        ) {
                            emptyList()
                        } else {
                            listOf(CampingFixtures.campsite())
                        },
                    )
                sites.readFailure = if (vector == "failed-site") IOException("site offline") else null
                var sends = 0
                messages.sendHandler = { _, _, _, _ ->
                    sends++
                    error("Logs cannot send")
                }
                val accounts = FakeAccountRepository()
                accounts.getHandler =
                    {
                        if (vector ==
                            "failed-profile"
                        ) {
                            throw IOException("profile offline")
                        }
                        ; if (vector ==
                            "missing-profile"
                        ) {
                            null
                        } else {
                            CampingFixtures.account(it)
                        }
                    }
                val model = MessageLogViewModel(messages, bookings, sites, accounts, SavedStateHandle())
                val store = ViewModelStore()
                store.put(vector, model)
                try {
                    runCurrent()
                    if (vector == "failed-primary") {
                        assertEquals(MessageLogRead.FAILURE, model.state.value.read)
                        messages.logFailure.value = null
                        model.retry()
                        runCurrent()
                    }
                    assertEquals(MessageLogRead.READY, model.state.value.read)
                    assertEquals(
                        message,
                        model.state.value.rows
                            .single()
                            .message,
                    )
                    assertFalse(
                        model.state.value.rows
                            .single()
                            .isLoading,
                    )
                    if (vector in listOf("failed-profile", "failed-stay", "failed-site")) {
                        assertTrue(
                            model.state.value.rows
                                .single()
                                .contextFailed,
                        )
                        bookings.getHandler = { stay }
                        sites.readFailure = null
                        sites.records.value =
                            listOf(CampingFixtures.campsite())
                        accounts.getHandler = { CampingFixtures.account(it) }
                        model.retryContext(message.id)
                        runCurrent()
                        assertFalse(
                            model.state.value.rows
                                .single()
                                .contextFailed,
                        )
                        assertEquals(
                            MessageLogDirection.OWNER_TO_ENTRANT,
                            model.state.value.rows
                                .single()
                                .direction,
                        )
                    }
                    assertTrue(messages.readRequests.isEmpty())
                    assertEquals(0, sends)
                } finally {
                    store.clear()
                }
            }
        }
}

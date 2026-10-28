package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.StayDatesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.SearchFiltersRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.inbox.InboxRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.ParticipantListRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ContactDetailsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileDetailsScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileUiState

/** Shows implemented task content; pending baselines are handled by the catalogue host.
 * @param session Current local fixture graph.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun TaskExampleFeature(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    when (session.example) {
        TaskExample.DISPLAY_NAME, TaskExample.CONTACT -> {
            ProfileTaskExample(session, onBack)
        }

        TaskExample.LOTTERY_WINDOWS -> {
            LotteryWindowTaskExample(session.bindings, onBack)
        }

        TaskExample.PARTICIPANTS -> {
            ParticipantListRoute(session.participants(), onBack)
        }

        TaskExample.TEXT_MATCH, TaskExample.GUEST_MATCH -> {
            DiscoveryTaskExample(session, onBack)
        }

        TaskExample.CAMPSITE_ORDER -> {
            val model = session.campsiteList()
            val state by model.state.collectAsStateWithLifecycle()
            val tab by model.tab.collectAsStateWithLifecycle()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            val id = selected
            if (id == null) {
                AppScreen(stringResource(R.string.task_site_order), onBack = onBack) {
                    CampsiteListScreen(
                        state,
                        tab,
                        model::selectTab,
                        { selected = it },
                        model::retry,
                    )
                }
            } else {
                val site = TaskExampleData.sites.first { it.id == id }
                AppScreen(
                    site.details.name,
                    onBack = { selected = null },
                ) { Text(site.details.description) }
            }
        }

        TaskExample.AVAILABILITY -> {
            val model = session.availability()
            val state by model.state.collectAsStateWithLifecycle()
            val discard by model.discardPrompt.collectAsStateWithLifecycle()
            val exit by model.exitRequested.collectAsStateWithLifecycle()
            LaunchedEffect(exit) { if (exit) onBack() }
            AvailabilityScreen(
                state,
                model::requestExit,
                model::retry,
                model::changeMonth,
                model::selectDates,
                model::setRequestedOffered,
                model::save,
                model::retryCalendar,
                model::dismissDiscard,
                model::confirmDiscard,
                discardPrompt = discard,
            )
        }

        TaskExample.INBOX_ORDER, TaskExample.UNREAD_COUNT -> {
            InboxTaskExample(session, onBack)
        }

        TaskExample.BOOKING_NIGHTS -> {
            BookingDatesTaskExample(session, onBack)
        }

        else -> {
            Unit
        }
    }
}

/** Uses the actual local profile writer and displays its confirmed local outcome.
 * @param session Current task graph.
 * @param onBack Returns to the catalogue.
 */
@Composable
private fun ProfileTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    val model = session.profile()
    val state by model.state.collectAsStateWithLifecycle()
    val completion by model.completion.collectAsStateWithLifecycle()
    var saved by rememberSaveable { mutableStateOf(false) }
    val contact = session.example == TaskExample.CONTACT
    LaunchedEffect(state is ProfileUiState.Content, completion) {
        completion?.let {
            model.acknowledgeCompletion(it)
            saved = true
        }
        if (state is ProfileUiState.Content) {
            if (contact) model.beginContactEdit() else model.beginPersonalEdit()
        }
    }
    Column {
        if (saved) Text(stringResource(R.string.task_saved))
        if (contact) {
            ContactDetailsScreen(
                state,
                model::editEmail,
                model::editPhone,
                model::saveContacts,
                onBack,
                model::retry,
            )
        } else {
            ProfileDetailsScreen(state, model::editName, model::saveName, onBack, model::retry)
        }
    }
}

/** Opens the actual working filter child without requiring another helper or screen journey.
 * @param session Current supplied listings and isolated predicate.
 * @param onBack Returns to the catalogue.
 */
@Composable
private fun DiscoveryTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    val model = session.explore()
    var filter by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val id = selected
    when {
        filter -> {
            SearchFiltersRoute(model, { filter = false })
        }

        id != null -> {
            val site = TaskExampleData.sites.first { it.id == id }
            AppScreen(
                site.details.name,
                onBack = { selected = null },
            ) { Text(site.details.description) }
        }

        else -> {
            AppScreen(stringResource(session.example.title), onBack = onBack) {
                ExploreRoute(model, { filter = true }, { selected = it })
            }
        }
    }
}

/** Reads the real saved target and acknowledges its local receipt independently.
 * @param session Current fixed event graph.
 * @param onBack Returns to the catalogue.
 */
@Composable
private fun InboxTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    var lottery by rememberSaveable { mutableStateOf<String?>(null) }
    val id = lottery
    if (id == null) {
        AppScreen(stringResource(R.string.inbox_title), onBack = onBack) {
            InboxRoute(session.inbox(), { request ->
                if (request.subject.type == SubjectType.LOTTERY) lottery = request.subject.id
            })
        }
    } else {
        LotteryDetailRoute(session.lottery(id), { lottery = null }, { lottery = null })
    }
}

/** Shows only the actual winner's dates step; this example never exposes a reservation write.
 * @param session Current fixed win and physical inventory.
 * @param onBack Returns to the catalogue.
 */
@Composable
private fun BookingDatesTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    val model = session.booking()
    val state by model.state.collectAsStateWithLifecycle()
    DisposableEffect(model) {
        model.setDatesVisible(true)
        onDispose { model.setDatesVisible(false) }
    }
    AppScreen(stringResource(R.string.task_booking), onBack = onBack) {
        val content = state as? BookingUiState.Content
        if (content != null) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StayDatesStep(
                    content,
                    model::changeMonth,
                    model::selectDate,
                    model::clearDates,
                    model::retryVisibleInventory,
                    model::retrySelectedInventory,
                    true,
                )
            }
        } else if (state == BookingUiState.Loading) {
            LoadingContent()
        } else {
            Text(stringResource(R.string.task_record_unavailable))
        }
    }
}

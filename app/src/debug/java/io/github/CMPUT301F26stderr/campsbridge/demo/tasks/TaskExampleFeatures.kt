package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreRoute
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.SearchFiltersRoute
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

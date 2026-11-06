package io.github.CMPUT301F26stderr.campsbridge.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.di.adminBrowseViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.availabilityViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.bookingViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.campsiteSetupViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.exploreViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.inboxViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotterySetupViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.notificationLogViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.onboardingViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.profileViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.reservationDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.reservationListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.LocalDemoMap
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.AndroidNotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.io.File

/** Default debug launcher; real screen controllers use an explicitly local demo graph. */
class OfflineDemoActivity : ComponentActivity() {
    /** Launches usable sample content without accessing application Firebase services.
     * @param savedInstanceState Previously saved Android activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val directory = File(applicationContext.filesDir, "offline-demo")
        val factory = viewModelFactory { initializer { DemoHostViewModel(directory) } }
        val host = ViewModelProvider(this, factory)[DemoHostViewModel::class.java]
        setContent {
            val current by host.session.collectAsStateWithLifecycle()
            val failure by host.failure.collectAsStateWithLifecycle()
            val requestedDestination by host.destination.collectAsStateWithLifecycle()
            val session = current
            if (session == null) {
                CampsbridgeTheme {
                    if (failure) {
                        TextButton({ host.reset() }) { Text(stringResource(R.string.demo_reset)) }
                    } else {
                        LoadingContent()
                    }
                }
                return@setContent
            }
            val nextAction by remember(session.store) {
                session.store.observe {
                    journeyAction(session.store.clock.instant())
                }
            }.collectAsStateWithLifecycle(
                initialValue = session.store.read { journeyAction(session.store.clock.instant()) },
            )
            val savedAreas = rememberSaveableStateHolder()
            CampsbridgeTheme {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    DemoToolbar(
                        host::select,
                        { host.reset() },
                        {
                            startActivity(
                                Intent(this@OfflineDemoActivity, DemoActivity::class.java),
                            )
                        },
                        {
                            startActivity(
                                Intent(
                                    this@OfflineDemoActivity,
                                    io.github.CMPUT301F26stderr.campsbridge.demo.tasks
                                        .TaskExamplesActivity::class.java,
                                ),
                            )
                        },
                        { step ->
                            startActivity(
                                Intent(
                                    this@OfflineDemoActivity,
                                    OnboardingPreviewActivity::class.java,
                                ).putExtra("step", step.name),
                            )
                        },
                        session.scenario,
                        nextAction,
                        host::follow,
                        failure,
                    )
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                        savedAreas.SaveableStateProvider(
                            "${System.identityHashCode(session.store)}/${session.scenario.name}",
                        ) {
                            CompositionLocalProvider(
                                LocalViewModelStoreOwner provides session,
                                LocalDemoMap provides
                                    { pin, select, modifier -> DemoLocationMap(pin, select, modifier) },
                            ) {
                                AppRoot(
                                    session.model,
                                    { id -> onboardingViewModelFactory(session.services, id) },
                                    { id -> profileViewModelFactory(session.services, id) },
                                    { id -> campsiteListViewModelFactory(session.services, id) },
                                    { accountId, id, context ->
                                        campsiteDetailViewModelFactory(
                                            session.services,
                                            accountId,
                                            id,
                                            context,
                                        )
                                    },
                                    {
                                        accountId,
                                        id,
                                        ->
                                        campsiteSetupViewModelFactory(
                                            session.services,
                                            accountId,
                                            id,
                                        )
                                    },
                                    {
                                        accountId,
                                        id,
                                        ->
                                        availabilityViewModelFactory(
                                            session.services,
                                            accountId,
                                            id,
                                        )
                                    },
                                    { exploreViewModelFactory(session.services) },
                                    {
                                        ownerId,
                                        id,
                                        ->
                                        lotterySetupViewModelFactory(session.services, ownerId, id)
                                    },
                                    {
                                        accountId,
                                        id,
                                        context,
                                        ->
                                        lotteryDetailViewModelFactory(
                                            session.services,
                                            accountId,
                                            id,
                                            context,
                                        )
                                    },
                                    {
                                        accountId,
                                        context,
                                        campsiteId,
                                        ->
                                        lotteryListViewModelFactory(
                                            session.services,
                                            accountId,
                                            context,
                                            campsiteId,
                                        )
                                    },
                                    {
                                        accountId,
                                        id,
                                        context,
                                        ->
                                        reservationDetailViewModelFactory(
                                            session.services,
                                            accountId,
                                            id,
                                            context,
                                        )
                                    },
                                    { accountId ->
                                        inboxViewModelFactory(session.services, accountId)
                                    },
                                    { accountId, lotteryId, campsiteId ->
                                        bookingViewModelFactory(
                                            session.services,
                                            accountId,
                                            lotteryId,
                                            campsiteId,
                                        )
                                    },
                                    { accountId, context, campsiteId ->
                                        reservationListViewModelFactory(
                                            session.services,
                                            accountId,
                                            context,
                                            campsiteId,
                                        )
                                    },
                                    { adminBrowseViewModelFactory(session.services) },
                                    { notificationLogViewModelFactory(session.services) },
                                    AndroidNotificationPermission(this@OfflineDemoActivity),
                                    session.notificationEntries,
                                    requestedDestination,
                                    host::consumeDestination,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gives the team visible scenario, reset and inspection actions without shell commands.
 * @param onScenario Opens a retained area/person.
 * @param onReset Restores the shared baseline after confirmation.
 * @param onGallery Opens the component gallery.
 * @param onTaskExamples Opens direct local examples for individual teammate tasks.
 * @param onPreview Opens a passive onboarding state for inspection.
 * @param scenario Current person.
 * @param nextAction Contextual saved-record handoff.
 * @param onNext Executes that handoff.
 * @param failed Whether a local operation needs retry.
 */
@Composable
private fun DemoToolbar(
    onScenario: (DemoScenario) -> Unit,
    onReset: () -> Unit,
    onGallery: () -> Unit,
    onTaskExamples: () -> Unit,
    onPreview: (OnboardingStep) -> Unit,
    scenario: DemoScenario,
    nextAction: DemoJourneyAction,
    onNext: (DemoJourneyAction) -> Unit,
    failed: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var previews by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (area in listOf(DemoScenario.ENTRANT, DemoScenario.OWNER, DemoScenario.ADMIN)) {
                TextButton({ onScenario(area) }, enabled = area != scenario) { Text(stringResource(area.areaLabel())) }
            }
        }
        if (failed) Text(stringResource(R.string.demo_operation_failed))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.demo_offline),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(scenario.personLabel()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box {
                        TextButton({ expanded = true }) { Text(stringResource(R.string.demo_tools)) }
                        DropdownMenu(expanded, { expanded = false }) {
                            for (scenario in DemoScenario.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(scenario.label())) },
                                    onClick = {
                                        expanded = false
                                        onScenario(scenario)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.demo_about)) },
                                onClick = {
                                    expanded = false
                                    about = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.demo_reset)) },
                                onClick = {
                                    expanded = false
                                    reset = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.task_examples)) },
                                onClick = {
                                    expanded = false
                                    onTaskExamples()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.demo_previews)) },
                                onClick = {
                                    expanded = false
                                    previews = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.demo_gallery)) },
                                onClick = {
                                    expanded = false
                                    onGallery()
                                },
                            )
                        }
                    }
                }
                TextButton({ onNext(nextAction) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(nextAction.label))
                }
            }
        }
    }
    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text(stringResource(R.string.demo_reset_title)) },
            text = { Text(stringResource(R.string.demo_reset_question)) },
            confirmButton = {
                TextButton({
                    reset = false
                    onReset()
                }) { Text(stringResource(R.string.demo_reset)) }
            },
            dismissButton = {
                TextButton({ reset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(stringResource(R.string.demo_about)) },
            text = {
                Text(stringResource(R.string.demo_sample_explanation))
            },
            confirmButton = {
                TextButton({ about = false }) { Text(stringResource(R.string.onboarding_close)) }
            },
        )
    }
    if (previews) {
        AlertDialog(
            onDismissRequest = { previews = false },
            title = { Text(stringResource(R.string.demo_previews)) },
            text = {
                Column {
                    Text(stringResource(R.string.demo_preview_explanation))
                    for (step in OnboardingStep.entries) {
                        TextButton({
                            previews = false
                            onPreview(step)
                        }) {
                            Text(stringResource(step.label()))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ previews = false }) { Text(stringResource(R.string.onboarding_close)) }
            },
        )
    }
}

/** Resolves the localized name of this demo starting state. */
private fun DemoScenario.label(): Int =
    when (this) {
        DemoScenario.ENTRANT -> R.string.demo_entrant
        DemoScenario.OWNER -> R.string.demo_owner
        DemoScenario.NEW_ACCOUNT -> R.string.demo_new_account
        DemoScenario.ADMIN -> R.string.demo_admin
    }

/** Resolves the localized onboarding preview name. */
private fun OnboardingStep.label(): Int =
    when (this) {
        OnboardingStep.WELCOME -> R.string.demo_welcome
        OnboardingStep.INTENT -> R.string.onboarding_intent_label
        OnboardingStep.NAME -> R.string.onboarding_name
        OnboardingStep.INTERESTS -> R.string.onboarding_interests_label
        OnboardingStep.OWNER -> R.string.onboarding_host_label
    }

/** Names the direct area without exposing account setup details. */
private fun DemoScenario.areaLabel(): Int =
    when (this) {
        DemoScenario.ENTRANT -> R.string.demo_camping
        DemoScenario.OWNER -> R.string.demo_hosting
        DemoScenario.ADMIN -> R.string.demo_admin_area
        DemoScenario.NEW_ACCOUNT -> R.string.demo_new_account
    }

/** Identifies whose actions this area records. */
private fun DemoScenario.personLabel(): Int =
    when (this) {
        DemoScenario.ENTRANT -> R.string.demo_alex
        DemoScenario.OWNER -> R.string.demo_jordan
        DemoScenario.ADMIN -> R.string.demo_taylor
        DemoScenario.NEW_ACCOUNT -> R.string.demo_new_account
    }

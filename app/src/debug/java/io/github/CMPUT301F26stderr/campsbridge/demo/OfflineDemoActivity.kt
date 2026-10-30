package io.github.CMPUT301F26stderr.campsbridge.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import io.github.CMPUT301F26stderr.campsbridge.di.inboxViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotteryListViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.lotterySetupViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.notificationLogViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.onboardingViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.profileViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.di.reservationDetailViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.AndroidNotificationPermission
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
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
        val directory = File(applicationContext.cacheDir, "offline-demo")
        val factory = viewModelFactory { initializer { DemoHostViewModel(directory) } }
        val host = ViewModelProvider(this, factory)[DemoHostViewModel::class.java]
        setContent {
            val session by host.session.collectAsStateWithLifecycle()
            CampsbridgeTheme {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    DemoToolbar(
                        host::reset,
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
                    )
                    Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                        key(session) {
                            CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
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
                                    { demoExploreViewModelFactory(session.services) },
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
                                        demoReservationListViewModelFactory(
                                            session.services,
                                            accountId,
                                            context,
                                            campsiteId,
                                            session.preview.reservationCategories,
                                        )
                                    },
                                    { adminBrowseViewModelFactory(session.services) },
                                    { notificationLogViewModelFactory(session.services) },
                                    AndroidNotificationPermission(this@OfflineDemoActivity),
                                    session.notificationEntries,
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
 * @param onScenario Replaces the local demo graph.
 * @param onReset Restores the currently selected starting scenario.
 * @param onGallery Opens the component gallery.
 * @param onTaskExamples Opens direct local examples for individual teammate tasks.
 * @param onPreview Opens a passive onboarding state for inspection.
 */
@Composable
private fun DemoToolbar(
    onScenario: (DemoScenario) -> Unit,
    onReset: () -> Unit,
    onGallery: () -> Unit,
    onTaskExamples: () -> Unit,
    onPreview: (OnboardingStep) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var previews by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
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
                    stringResource(R.string.demo_sample_label),
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
                            onReset()
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
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(stringResource(R.string.demo_about)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(stringResource(R.string.demo_sample_explanation))
                    Text(stringResource(R.string.demo_sample_ordering))
                }
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

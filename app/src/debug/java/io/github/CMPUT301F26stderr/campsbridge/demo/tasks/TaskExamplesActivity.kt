package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.io.File

/** Debug-only direct task launcher, retaining real feature owners across activity recreation. */
class TaskExamplesActivity : ComponentActivity() {
    /** Shows task fixtures without accessing Firebase services.
     * @param savedInstanceState Previous Android activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory =
            viewModelFactory {
                initializer {
                    TaskExamplesHostViewModel(
                        File(applicationContext.cacheDir, "task-examples"),
                        taskExampleBindings(),
                    )
                }
            }
        val host = ViewModelProvider(this, factory)[TaskExamplesHostViewModel::class.java]
        setContent { CampsbridgeTheme { TaskExamplesContent(host, ::finish) } }
    }
}

/** Offers each task directly, with explicit future states and a resettable local graph.
 * @param host Activity-retained selection and controller owner.
 * @param onExit Returns to the offline demo.
 */
@Composable
fun TaskExamplesContent(
    host: TaskExamplesHostViewModel,
    onExit: () -> Unit,
) {
    val session by host.session.collectAsStateWithLifecycle()
    val selected = session
    if (selected == null) {
        AppScreen(stringResource(R.string.task_examples), onBack = onExit) {
            LazyColumn(
                modifier = Modifier.testTag("task-catalogue"),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                item { Text(stringResource(R.string.task_examples_description)) }
                item {
                    Text(
                        stringResource(R.string.task_unfinished_features),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(TaskExample.entries, key = { it.id }) { task ->
                    OutlinedCard(
                        onClick = { host.open(task) },
                        modifier = Modifier.fillMaxWidth().testTag(task.id),
                    ) {
                        Column(Modifier.padding(Spacing.medium)) {
                            Text(
                                stringResource(
                                    R.string.task_label,
                                    task.id,
                                    stringResource(task.title),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    if (task.scaffoldReady) {
                                        R.string.task_ready
                                    } else {
                                        R.string.task_placeholder
                                    },
                                    task.baseline,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    BackHandler { host.showCatalogue() }
    key(selected) {
        var instructions by rememberSaveable { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            host::showCatalogue,
                        ) { Text(stringResource(R.string.task_catalogue)) }
                        TextButton(host::reset) { Text(stringResource(R.string.task_reset)) }
                    }
                    Text(
                        stringResource(
                            R.string.task_label,
                            selected.example.id,
                            stringResource(selected.example.title),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.task_local_only),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        { instructions = !instructions },
                    ) { Text(stringResource(R.string.task_instructions)) }
                    if (instructions) {
                        Text(stringResource(selected.example.instructions))
                        Text(
                            stringResource(
                                if (selected.bindings.isConnected(selected.example)) {
                                    R.string.task_connected
                                } else {
                                    R.string.task_pending
                                },
                            ),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                if (selected.example.scaffoldReady) {
                    TaskExampleFeature(selected, host::showCatalogue)
                } else {
                    AppScreen(
                        stringResource(selected.example.title),
                        onBack = host::showCatalogue,
                    ) {
                        Text(stringResource(R.string.task_placeholder, selected.example.baseline))
                        Text(stringResource(R.string.task_future))
                    }
                }
            }
        }
    }
}

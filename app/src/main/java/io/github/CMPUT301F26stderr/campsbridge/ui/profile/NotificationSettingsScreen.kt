/*
 * Keeps saved app preference, account read feedback and actual Android allowance separate.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.platform.notifications.NotificationPermissionState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.SwitchRow
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Keeps saved app preference, account read feedback and actual Android allowance separate.
 * @param source Existing profile parent's account read outcome.
 * @param preference Acknowledged/pending preference and explicit retry feedback.
 * @param device Actual or explicitly supplied device facts.
 * @param onToggle Requests one saved preference change.
 * @param onRetrySave Explicit retry of the retained failed target.
 * @param onRetrySource Retries account observation only.
 * @param onRequestPermission Explicit user request through the platform launcher.
 * @param onSystemSettings Opens Android settings without saving an app preference.
 * @param onBack Returns to the actual Settings caller.
 * @param modifier Caller layout.
 * @param settingsUnavailable Whether Android has no activity to handle the settings Intent.
 */
@Composable
fun NotificationSettingsScreen(
    source: ProfileUiState,
    preference: NotificationPreferenceUiState,
    device: NotificationPermissionState,
    onToggle: (Boolean) -> Unit,
    onRetrySave: () -> Unit,
    onRetrySource: () -> Unit,
    onRequestPermission: () -> Unit,
    onSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    settingsUnavailable: Boolean = false,
) {
    AppScreen(stringResource(R.string.notification_settings_title), modifier, onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (source) {
                ProfileUiState.Loading -> {
                    LoadingContent(stringResource(R.string.profile_loading))
                }

                ProfileUiState.Failure -> {
                    FailureContent(
                        stringResource(R.string.profile_failure),
                        onRetrySource,
                    )
                }

                ProfileUiState.Unavailable -> {
                    Text(
                        stringResource(R.string.account_unavailable_body),
                    )
                }

                is ProfileUiState.Content -> {
                    Unit
                }
            }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            ) {
                SwitchRow(
                    stringResource(R.string.notification_app_alerts),
                    preference.enabled == true,
                    onToggle,
                    supportingText = stringResource(R.string.notification_inbox_retained),
                    enabled = preference.canChange,
                )
                if (preference.isSaving || preference.saveFailed) {
                    Column(
                        Modifier.padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        if (preference.isSaving) {
                            LoadingContent(
                                stringResource(R.string.notification_preference_saving),
                            )
                        }
                        if (preference.saveFailed) {
                            Text(stringResource(R.string.notification_preference_failed))
                            AppActionButton(
                                stringResource(R.string.notification_retry_save),
                                onRetrySave,
                                enabled = preference.canChange,
                                style = ActionStyle.SECONDARY,
                            )
                        }
                    }
                }
            }
            SummarySection(stringResource(R.string.notification_android_heading)) {
                Text(
                    stringResource(
                        if (device.canPost) {
                            R.string.notification_android_allowed
                        } else {
                            R.string.notification_android_blocked
                        },
                    ),
                )
                Text(
                    stringResource(
                        when (device.channelEnabled) {
                            null -> R.string.notification_channel_absent
                            false -> R.string.notification_channel_blocked
                            true -> R.string.notification_channel_allowed
                        },
                    ),
                )
                if (device.requiresRuntimePermission && !device.permissionGranted) {
                    AppActionButton(
                        stringResource(R.string.notification_request_permission),
                        onRequestPermission,
                        Modifier.fillMaxWidth(),
                    )
                }
                AppActionButton(
                    stringResource(R.string.notification_open_settings),
                    onSystemSettings,
                    Modifier.fillMaxWidth(),
                    style = ActionStyle.SECONDARY,
                )
                if (settingsUnavailable) {
                    Text(
                        stringResource(R.string.notification_settings_unavailable),
                    )
                }
            }
        }
    }
}

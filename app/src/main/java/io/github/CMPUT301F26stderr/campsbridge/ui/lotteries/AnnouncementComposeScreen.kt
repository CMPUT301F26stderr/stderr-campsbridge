package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.forms.AppTextField
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Displays one local announcement and its explicit immutable-event recovery.
 * @param state Saved context, local input and retained send outcome.
 * @param onBack Shared Back applies pending/discard rules.
 * @param onTitle Retains raw title.
 * @param onBody Retains raw multiline body.
 * @param onSend Freezes one new valid request.
 * @param onRetry Refreshes failed saved context only.
 * @param onRetrySend Reconciles the original request before retrying it.
 * @param onEditAttempt Explicitly abandons an absent/conflicting attempt.
 * @param onDismissDiscard Keeps the unsent form.
 * @param onConfirmDiscard Abandons only local composition.
 */
@Composable
fun AnnouncementComposeScreen(
    state: AnnouncementComposeUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onRetrySend: () -> Unit,
    onEditAttempt: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
) {
    val pending = state.phase in setOf(AnnouncementPhase.SENDING, AnnouncementPhase.CHECKING)
    val editable = state.canSend && state.attempt == null && !state.sent
    AppScreen(
        stringResource(R.string.announcement_title),
        onBack = onBack,
        backEnabled = !pending,
        bottomContent = {
            if (state.read == AnnouncementRead.READY || state.attempt != null) {
                ActionFooter {
                    when (state.phase) {
                        AnnouncementPhase.IDLE -> {
                            AppActionButton(
                                stringResource(R.string.announcement_send),
                                onSend,
                                Modifier.fillMaxWidth(),
                                enabled = state.canSend && state.hasRecipients && !state.sent,
                            )
                        }

                        AnnouncementPhase.SENDING -> {
                            AppActionButton(
                                stringResource(R.string.announcement_sending),
                                {},
                                Modifier.fillMaxWidth(),
                                enabled = false,
                                isSubmitting = true,
                                progressDescription = stringResource(R.string.announcement_sending),
                            )
                        }

                        AnnouncementPhase.CHECKING -> {
                            LoadingContent(stringResource(R.string.announcement_checking))
                        }

                        AnnouncementPhase.RETRY -> {
                            Text(stringResource(R.string.announcement_check_failed))
                            TextButton(
                                onRetrySend,
                                enabled = state.canSend,
                            ) { Text(stringResource(R.string.announcement_retry)) }
                        }

                        AnnouncementPhase.ABSENT -> {
                            Text(stringResource(R.string.announcement_absent))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                TextButton(
                                    onRetrySend,
                                    enabled = state.canSend && state.hasRecipients,
                                ) { Text(stringResource(R.string.announcement_retry)) }
                                TextButton(onEditAttempt) { Text(stringResource(R.string.announcement_edit)) }
                            }
                        }

                        AnnouncementPhase.CONFLICT -> {
                            Text(stringResource(R.string.announcement_conflict))
                            TextButton(onEditAttempt) { Text(stringResource(R.string.announcement_new)) }
                        }
                    }
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (state.read) {
                AnnouncementRead.LOADING -> {
                    LoadingContent()
                }

                AnnouncementRead.FAILURE -> {
                    FailureContent(stringResource(R.string.announcement_load_failed), onRetry)
                }

                AnnouncementRead.UNAVAILABLE -> {
                    Text(stringResource(R.string.announcement_unavailable))
                    TextButton(onRetry) { Text(stringResource(R.string.retry)) }
                }

                AnnouncementRead.READY -> {
                    Unit
                }
            }
            if (state.read == AnnouncementRead.READY && !state.hasRecipients && !state.sent && !pending) {
                Text(stringResource(R.string.announcement_empty_audience))
            }
            if (state.lottery != null || state.attempt != null) {
                state.lottery?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
                Text(stringResource(R.string.announcement_audience), style = MaterialTheme.typography.bodyMedium)
                AppTextField(
                    state.title,
                    onTitle,
                    stringResource(R.string.announcement_title_label),
                    Modifier.testTag("announcement-title"),
                    errorMessage =
                        if (state.titleInvalid) {
                            stringResource(
                                R.string.announcement_title_required,
                            )
                        } else {
                            null
                        },
                    enabled = editable,
                    singleLine = false,
                    maxLines = 3,
                )
                AppTextField(
                    state.body,
                    onBody,
                    stringResource(R.string.announcement_body_label),
                    Modifier.testTag("announcement-body"),
                    errorMessage = if (state.bodyInvalid) stringResource(R.string.announcement_body_required) else null,
                    enabled = editable,
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                )
            }
        }
    }
    if (state.confirmingDiscard) {
        val unknown = state.phase == AnnouncementPhase.RETRY
        ConfirmationDialog(
            title =
                stringResource(
                    if (unknown) R.string.announcement_leave_unknown_title else R.string.announcement_discard_title,
                ),
            description =
                stringResource(
                    if (unknown) R.string.announcement_leave_unknown_body else R.string.announcement_discard_body,
                ),
            onConfirm = onConfirmDiscard,
            onCancel = onDismissDiscard,
            onDismiss = onDismissDiscard,
            confirmLabel =
                stringResource(
                    if (unknown) R.string.announcement_leave_unknown_confirm else R.string.booking_discard,
                ),
            cancelLabel = stringResource(R.string.booking_keep_draft),
            destructive = true,
        )
    }
}

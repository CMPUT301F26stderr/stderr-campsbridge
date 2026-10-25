package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.ActionStyle
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.ConfirmationDialog
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** Owner availability from frames 09.04/09.08 using the ordinary shared calendar and actions.
 * @param state Confirmed parent and local edit outcome.
 * @param onBack Requests clean exit or discard confirmation.
 * @param onRetry Retries a failed parent read.
 * @param onMonth Selects another displayed range.
 * @param onSelectDates Changes the working selection without requesting writes.
 * @param onSetOffered Applies Offer/Withdraw intent to selected dates locally.
 * @param onSave Saves remaining requested dates in place.
 * @param onRetryCalendar Retries only the failed visible range.
 * @param onDismissDiscard Keeps local intent.
 * @param onConfirmDiscard Abandons remaining local intent, retaining confirmed writes.
 * @param modifier Caller layout.
 * @param discardPrompt Confirmation remains available during restored parent loading/failure.
 */
@Composable
fun AvailabilityScreen(
    state: AvailabilityUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onSelectDates: (Set<LocalDate>) -> Unit,
    onSetOffered: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetryCalendar: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    discardPrompt: Boolean = false,
) {
    val content = state as? AvailabilityUiState.Content
    val work = content?.availability
    val saving = work?.isSaving == true
    BackHandler { if (!saving) onBack() }
    AppScreen(
        stringResource(R.string.availability_title),
        modifier,
        onBack = onBack,
        backEnabled = !saving,
        bottomContent = {
            if (work != null) {
                AppActionButton(
                    stringResource(
                        if (work.failure ==
                            AvailabilitySaveFailure.WRITE
                        ) {
                            R.string.availability_retry_remaining
                        } else {
                            R.string.availability_save
                        },
                    ),
                    onSave,
                    Modifier.fillMaxWidth().padding(
                        horizontal = Spacing.screen,
                        vertical = Spacing.small,
                    ),
                    enabled =
                        work.requested.isNotEmpty() && work.pendingEdits != null &&
                            !discardPrompt,
                    isSubmitting = saving,
                    progressDescription = stringResource(R.string.availability_saving),
                )
            }
        },
    ) {
        when (state) {
            AvailabilityUiState.Loading -> {
                LoadingContent()
            }

            AvailabilityUiState.Failure -> {
                FailureContent(stringResource(R.string.campsite_detail_failure), onRetry)
            }

            AvailabilityUiState.Unavailable -> {
                Text(stringResource(R.string.campsite_unavailable))
            }

            is AvailabilityUiState.Content -> {
                val calendar = state.availability
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(state.campsite.details.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.availability_hint))
                    if (calendar.failure == AvailabilitySaveFailure.WRITE) {
                        Text(
                            stringResource(R.string.campsite_availability_save_failure),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (state.saved) {
                        Text(stringResource(R.string.availability_saved))
                    }
                    CampsiteCalendar(
                        offeredNightCalendarState(
                            calendar.month,
                            calendar.inventory,
                            calendar.requested,
                            state.selectedDates,
                        ),
                        onMonth,
                        { date ->
                            onSelectDates(
                                if (date in
                                    state.selectedDates
                                ) {
                                    state.selectedDates - date
                                } else {
                                    state.selectedDates +
                                        date
                                },
                            )
                        },
                        enabled = !saving && !discardPrompt,
                    )
                    when (calendar.inventory) {
                        CampsiteInventoryUiState.Loading -> {
                            LoadingContent()
                        }

                        CampsiteInventoryUiState.Failure -> {
                            FailureContent(
                                stringResource(R.string.campsite_calendar_failure),
                                onRetryCalendar,
                            )
                        }

                        is CampsiteInventoryUiState.Content -> {
                            Unit
                        }
                    }
                    Text(stringResource(R.string.availability_selected, state.selectedDates.size))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        for ((offered, label) in listOf(
                            true to R.string.availability_offer,
                            false to R.string.availability_withdraw,
                        )) {
                            AppActionButton(
                                stringResource(label),
                                { onSetOffered(offered) },
                                enabled =
                                    state.selectedDates.isNotEmpty() && !saving &&
                                        !discardPrompt &&
                                        calendar.inventory is CampsiteInventoryUiState.Content,
                                style = ActionStyle.SECONDARY,
                            )
                        }
                    }
                    Text(
                        stringResource(
                            R.string.campsite_requested_nights,
                            calendar.requested.values
                                .count {
                                    it
                                },
                            calendar.requested.values.count { !it },
                        ),
                    )
                    if (calendar.pendingEdits == null && calendar.requested.isNotEmpty()) {
                        Text(stringResource(R.string.campsite_date_save_pending))
                    }
                }
            }
        }
    }
    if (discardPrompt) {
        ConfirmationDialog(
            stringResource(R.string.availability_discard_title),
            stringResource(R.string.availability_discard_body),
            onConfirmDiscard,
            onDismissDiscard,
            onDismissDiscard,
            isSubmitting = saving,
            confirmLabel = stringResource(R.string.campsite_discard_confirm),
        )
    }
}

/** Renders supplied fixture state without a controller or simulated successful save.
 * @param state Preview-only state.
 */
@Composable
private fun AvailabilityPreviewContent(state: AvailabilityUiState) {
    CampsbridgeTheme { AvailabilityScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

/** Shows the full viewport, known inventory, explicit selection and local desired changes. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun AvailabilityPreview() {
    val month = YearMonth.of(2027, 5)
    AvailabilityPreviewContent(
        AvailabilityUiState.Content(
            previewCampsite(),
            CampsiteAvailabilityState(
                month,
                CampsiteInventoryUiState.Content(
                    listOf(CalendarNight(month.atDay(14), true, "reservation")),
                ),
                requested =
                    mapOf(month.atDay(21) to true),
                pendingEdits = mapOf(month.atDay(21) to true),
            ),
            selectedDates = setOf(month.atDay(22)),
        ),
    )
}

/** Shows retained remaining work after a failed second write, without a separate error screen. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilityPartialFailurePreview() {
    val month = YearMonth.of(2027, 5)
    AvailabilityPreviewContent(
        AvailabilityUiState.Content(
            previewCampsite(),
            CampsiteAvailabilityState(
                month,
                CampsiteInventoryUiState.Content(listOf(CalendarNight(month.atDay(21), true))),
                requested =
                    mapOf(month.atDay(22) to true),
                pendingEdits = mapOf(month.atDay(22) to true),
                failure = AvailabilitySaveFailure.WRITE,
            ),
        ),
    )
}

/** Shows pending source data without claiming known empty inventory. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilityLoadingPreview() {
    AvailabilityPreviewContent(AvailabilityUiState.Loading)
}

/** Shows parent retry with the same ordinary frame and return action. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilityFailurePreview() {
    AvailabilityPreviewContent(AvailabilityUiState.Failure)
}

/** Shows blocked controls during an explicit save. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilitySavingPreview() {
    AvailabilityPreviewContent(
        AvailabilityUiState.Content(
            previewCampsite(),
            CampsiteAvailabilityState(
                YearMonth.of(2027, 5),
                CampsiteInventoryUiState.Content(emptyList()),
                isSaving = true,
            ),
        ),
    )
}

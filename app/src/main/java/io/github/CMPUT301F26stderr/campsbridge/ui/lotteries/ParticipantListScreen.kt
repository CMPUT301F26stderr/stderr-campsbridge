package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.ChoiceOption
import io.github.CMPUT301F26stderr.campsbridge.ui.components.selection.DropdownField
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Connects the actual child to its saved parent's read lifetime and retained filter.
 * @param model Existing saved-detail graph owner.
 * @param onBack Pops only this child.
 */
@Composable
fun ParticipantListRoute(
    model: LotteryDetailViewModel,
    onBack: () -> Unit,
) {
    val parent by model.state.collectAsStateWithLifecycle()
    val state by model.participants.collectAsStateWithLifecycle()
    val selected by model.selectedParticipantState.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    DisposableEffect(model) {
        model.setParticipantsVisible(true)
        onDispose { model.setParticipantsVisible(false) }
    }
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    ParticipantListScreen(
        parent,
        state,
        selected,
        onBack,
        model::retry,
        model::selectParticipantState,
        model::retryParticipants,
        model::retryParticipantNames,
    )
}

/** Owner-only participant reading; filter/name failure never becomes a confirmed empty list.
 * @param parent Confirmed parent facts or its independent source outcome.
 * @param state Scoped entry and label outcomes.
 * @param selected Currently applied exact state; null means All.
 * @param onBack Returns to the same overview.
 * @param onRetryParent Retries the saved parent source.
 * @param onSelect Changes only a safe filter.
 * @param onRetryEntries Retries failed entries.
 * @param onRetryNames Retries failed labels only.
 * @param modifier Caller layout.
 */
@Composable
fun ParticipantListScreen(
    parent: LotteryDetailUiState,
    state: ParticipantListUiState,
    selected: EntryState?,
    onBack: () -> Unit,
    onRetryParent: () -> Unit,
    onSelect: (EntryState?) -> Unit,
    onRetryEntries: () -> Unit,
    onRetryNames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(stringResource(R.string.lottery_participants), modifier, onBack = onBack) {
        when (parent) {
            LotteryDetailUiState.Loading -> {
                LoadingContent()
            }

            LotteryDetailUiState.Failure -> {
                FailureContent(stringResource(R.string.lottery_load_failed), onRetryParent)
            }

            LotteryDetailUiState.Unavailable -> {
                Text(stringResource(R.string.lottery_unavailable))
            }

            is LotteryDetailUiState.Content -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    item {
                        Text(parent.lottery.title, style = MaterialTheme.typography.headlineSmall)
                        Text(lotteryLifecycleLabel(parent.lifecycle))
                        Text(
                            stringResource(
                                R.string.lottery_waiting_count,
                                parent.lottery.waitingCount,
                            ),
                        )
                    }
                    when (state) {
                        ParticipantListUiState.Loading -> {
                            item { LoadingContent() }
                        }

                        ParticipantListUiState.Unavailable -> {
                            item { Text(stringResource(R.string.lottery_unavailable)) }
                        }

                        ParticipantListUiState.Failure -> {
                            item {
                                FailureContent(
                                    stringResource(R.string.lottery_participants_failed),
                                    onRetryEntries,
                                )
                            }
                        }

                        is ParticipantListUiState.Content -> {
                            item {
                                DropdownField(
                                    stringResource(R.string.lottery_participant_filter),
                                    listOf(
                                        ChoiceOption(
                                            "ALL",
                                            stringResource(R.string.lottery_participants_all),
                                        ),
                                    ) +
                                        state.allowedStates.map {
                                            ChoiceOption(
                                                it.name,
                                                participantFilterLabel(it),
                                            )
                                        },
                                    selected?.name ?: "ALL",
                                    { id ->
                                        onSelect(EntryState.entries.firstOrNull { it.name == id })
                                    },
                                )
                            }
                            when {
                                state.filterPending -> {
                                    item {
                                        Text(
                                            stringResource(
                                                R.string.lottery_participant_filter_pending,
                                            ),
                                        )
                                    }
                                }

                                state.rows.isEmpty() -> {
                                    item {
                                        EmptyContent(
                                            stringResource(R.string.lottery_participants_empty),
                                        )
                                    }
                                }

                                else -> {
                                    if (state.rows.any { it.name == ParticipantName.Failure }) {
                                        item {
                                            FailureContent(
                                                stringResource(
                                                    R.string.lottery_participant_names_failed,
                                                ),
                                                onRetryNames,
                                            )
                                        }
                                    }
                                    items(
                                        state.rows,
                                        key = { it.accountId },
                                    ) { row -> ParticipantRowContent(row) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Displays canonical identity and display-safe state even when its supplemental name is absent.
 * @param row Confirmed participation with independent profile-label outcome.
 */
@Composable
private fun ParticipantRowContent(row: ParticipantRow) {
    val format =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(LocalConfiguration.current.locales[0])
            .withZone(ZoneId.systemDefault())
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            when (val name = row.name) {
                ParticipantName.Loading -> {
                    stringResource(R.string.lottery_participant_name_loading)
                }

                ParticipantName.Unavailable -> {
                    stringResource(
                        R.string.lottery_participant_name_unavailable,
                    )
                }

                ParticipantName.Failure -> {
                    stringResource(R.string.lottery_participant_name_failed)
                }

                is ParticipantName.Available -> {
                    name.name
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(row.accountId, style = MaterialTheme.typography.bodySmall)
        Text(participantStatusLabel(row.status))
        Text(
            stringResource(R.string.lottery_participant_joined, format.format(row.joinedAt)),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(Modifier.padding(top = Spacing.small))
    }
}

/** Localizes owner row facts without entrant-specific action wording.
 * @param status Shared draw-gated display status.
 */
@Composable
private fun participantStatusLabel(status: LotteryMembershipStatus): String =
    stringResource(
        when (status) {
            LotteryMembershipStatus.NOT_JOINED -> R.string.lottery_not_joined
            LotteryMembershipStatus.WAITING -> R.string.lottery_participant_waiting
            LotteryMembershipStatus.WITHDRAWN -> R.string.lottery_participant_withdrawn
            LotteryMembershipStatus.RESULTS_PENDING -> R.string.lottery_draw_pending
            LotteryMembershipStatus.WON -> R.string.lottery_participant_selected
            LotteryMembershipStatus.LOST -> R.string.lottery_participant_not_selected
            LotteryMembershipStatus.DECLINED -> R.string.lottery_participant_declined
        },
    )

/** Localizes only safe available choices supplied by the controller.
 * @param state Actual stored filter value, distinct from row presentation.
 */
@Composable
private fun participantFilterLabel(state: EntryState): String =
    stringResource(
        when (state) {
            EntryState.WAITING -> R.string.lottery_participant_waiting
            EntryState.WITHDRAWN -> R.string.lottery_participant_withdrawn
            EntryState.OFFERED -> R.string.lottery_participant_selected
            EntryState.LOST -> R.string.lottery_participant_not_selected
            EntryState.DECLINED -> R.string.lottery_participant_declined
        },
    )

/** Passive states cover unavailable names, partial results and explicit pending filtering. */
class ParticipantPreviewProvider : PreviewParameterProvider<ParticipantListUiState> {
    override val values =
        sequenceOf(
            ParticipantListUiState.Loading,
            ParticipantListUiState.Failure,
            ParticipantListUiState.Content(emptyList(), listOf(EntryState.WAITING)),
            ParticipantListUiState.Content(
                emptyList(),
                listOf(EntryState.WAITING),
                filterPending = true,
            ),
            ParticipantListUiState.Content(
                listOf(
                    ParticipantRow(
                        "entrant-A",
                        ParticipantName.Available("Alexandra River Camper"),
                        LotteryMembershipStatus.RESULTS_PENDING,
                        Instant.parse("2027-05-01T12:00:00Z"),
                    ),
                    ParticipantRow(
                        "entrant-B",
                        ParticipantName.Unavailable,
                        LotteryMembershipStatus.RESULTS_PENDING,
                        Instant.parse("2027-05-01T12:30:00Z"),
                    ),
                    ParticipantRow(
                        "entrant-C",
                        ParticipantName.Failure,
                        LotteryMembershipStatus.WITHDRAWN,
                        Instant.parse("2027-05-01T12:45:00Z"),
                    ),
                ),
                listOf(EntryState.WAITING, EntryState.WITHDRAWN),
            ),
        )
}

/** Shows the native child using supplied immutable content only.
 * @param state Representative entry-source outcome.
 */
@Preview(showBackground = true)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun ParticipantPreview(
    @PreviewParameter(ParticipantPreviewProvider::class) state: ParticipantListUiState,
) {
    val parent =
        LotteryDetailPreviewProvider()
            .values
            .filterIsInstance<LotteryDetailUiState.Content>()
            .first()
    CampsbridgeTheme {
        ParticipantListScreen(
            parent.copy(canContinue = false, lifecycle = LotteryLifecycle.DRAW_PENDING),
            state,
            null,
            {},
            {},
            {},
            {},
            {},
        )
    }
}

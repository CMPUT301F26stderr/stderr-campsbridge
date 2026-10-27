package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipAction
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipActions
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipRead
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipReadContent
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipStatus
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryMembershipUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Entrant overview entry; current server facts choose direct actions or the real choices child.
 * @param state Scoped association, own-entry and shared command outcomes.
 * @param timeZone Saved campsite's IANA timezone.
 * @param onRetry Retries only the association source.
 * @param onRetryMembership Retries only failed own-entry observation.
 * @param onJoin Joins the displayed sole lottery.
 * @param onRequestLeave Requests its explicit withdrawal dialog.
 * @param onDismissLeave Dismisses the displayed dialog without writing.
 * @param onConfirmLeave Rechecks and submits its withdrawal.
 * @param onOpenLottery Opens the existing entrant saved-detail graph by actual ID.
 * @param onOpenChoices Opens this saved parent's real choices child.
 */
@Composable
internal fun CampsiteLotteryEntry(
    state: CampsiteLotteriesUiState,
    timeZone: String,
    onRetry: () -> Unit,
    onRetryMembership: () -> Unit,
    onJoin: (String) -> Unit,
    onRequestLeave: (String) -> Unit,
    onDismissLeave: (String) -> Unit,
    onConfirmLeave: (String) -> Unit,
    onOpenLottery: (String) -> Unit,
    onOpenChoices: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        when (state) {
            CampsiteLotteriesUiState.Unavailable -> {
                Unit
            }

            CampsiteLotteriesUiState.Loading -> {
                LoadingContent()
            }

            CampsiteLotteriesUiState.Failure -> {
                FailureContent(
                    stringResource(R.string.campsite_lotteries_failed),
                    onRetry,
                )
            }

            is CampsiteLotteriesUiState.Content -> {
                val sole = state.choices.singleOrNull()?.lottery
                when {
                    state.choices.isEmpty() -> {
                        Text(stringResource(R.string.campsite_no_active_lottery))
                    }

                    sole == null -> {
                        AppActionButton(
                            stringResource(R.string.campsite_available_lotteries),
                            onOpenChoices,
                            Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            Text(
                                sole.title,
                                Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            TextButton({ onOpenLottery(sole.id) }) {
                                Text(stringResource(R.string.campsite_view_lottery))
                            }
                        }
                        Text(
                            campsiteLotteryDeadline(sole, timeZone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            LotteryMembershipReadContent(state.membership, onRetryMembership)
                        }
                        LotteryMembershipActions(
                            state.participation,
                            { onJoin(sole.id) },
                            { onRequestLeave(sole.id) },
                            { onDismissLeave(sole.id) },
                            { onConfirmLeave(sole.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Uses the known campsite zone without another row-level listing query.
 * @param lottery Actual current choice.
 * @param timeZone Saved campsite's zone.
 */
@Composable
internal fun campsiteLotteryDeadline(
    lottery: Lottery,
    timeZone: String,
): String =
    stringResource(
        R.string.lottery_registration_ends,
        formatDateTime(
            lottery.configuration.registrationClosesAt,
            ZoneId.of(timeZone),
            LocalConfiguration.current.locales[0],
        ),
    )

/** Passive real-shaped variants shared by overview-entry and choice-list previews. */
internal class CampsiteLotteryPreviewProvider : PreviewParameterProvider<CampsiteLotteriesUiState> {
    override val values: Sequence<CampsiteLotteriesUiState> =
        run {
            val now = Instant.parse("2027-05-01T12:00:00Z")
            val lottery =
                Lottery(
                    "river-weekend",
                    "host",
                    "River weekend camping",
                    "",
                    LotteryConfiguration(
                        now,
                        now.plusSeconds(86_400),
                        now.plusSeconds(86_400),
                        now.plusSeconds(172_800),
                        1,
                        null,
                    ),
                    0,
                    now,
                    now,
                )
            val choice =
                CampsiteLotteryChoice(
                    lottery,
                    CampsiteLotteryOfferRead.Ready(
                        LotteryOffer(
                            previewCampsite().id,
                            listOf(LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 16)),
                        ),
                    ),
                )
            val normal =
                CampsiteLotteriesUiState.Content(
                    listOf(choice),
                    LotteryMembershipRead.Content(LotteryMembershipStatus.NOT_JOINED),
                    LotteryMembershipUiState(canJoin = true),
                )
            sequenceOf(
                normal,
                normal.copy(
                    participation =
                        LotteryMembershipUiState(
                            canJoin = true,
                            action = LotteryMembershipAction.JOIN,
                        ),
                ),
                normal.copy(
                    membership = LotteryMembershipRead.Content(LotteryMembershipStatus.WAITING),
                    participation =
                        LotteryMembershipUiState(
                            canLeave = true,
                            isConfirmingLeave = true,
                        ),
                ),
                normal.copy(
                    membership = LotteryMembershipRead.Failure,
                    participation = LotteryMembershipUiState(),
                ),
                CampsiteLotteriesUiState.Content(
                    listOf(
                        choice,
                        CampsiteLotteryChoice(
                            lottery.copy(
                                id = "summer-break",
                                title = "A summer break in the Canadian Rockies",
                            ),
                            CampsiteLotteryOfferRead.Failure,
                        ),
                    ),
                ),
                CampsiteLotteriesUiState.Content(emptyList()),
                CampsiteLotteriesUiState.Loading,
                CampsiteLotteriesUiState.Failure,
            )
        }
}

/** Shows ordinary entry states with the same shared controls used by the actual saved route.
 * @param state Passive variant; callbacks cannot save or query.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CampsiteLotteryEntryPreview(
    @PreviewParameter(CampsiteLotteryPreviewProvider::class) state: CampsiteLotteriesUiState,
) {
    CampsbridgeTheme {
        CampsiteDetailScreen(
            CampsiteDetailUiState.Content(previewCampsite()),
            previewCampsitePhoto(),
            {},
            {},
            {},
            rating = RatingState.Ready(RatingSummary(0, null)),
            lotteryActions = {
                CampsiteLotteryEntry(
                    state,
                    previewCampsite().details.timeZone,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            },
        )
    }
}

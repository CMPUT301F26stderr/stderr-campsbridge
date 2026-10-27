package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.calendar.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.CampsiteCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.components.summaries.SummarySection
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** A working allocation child; its actions never save offered inventory or create a lottery.
 * @param state Actual parent input and current working selection/range.
 * @param onBack Discards only unapplied child edits.
 * @param onApply Applies known eligible working nights to the parent.
 * @param onMonth Changes the required visible-month range.
 * @param onDate Toggles one allowed date action.
 * @param onRetryInventory Retries the required inventory range.
 * @param onRetryChoices Retries the selected campsite source.
 */
@Composable
fun LotteryAllocationScreen(
    state: LotterySetupUiState,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onRetryInventory: () -> Unit,
    onRetryChoices: () -> Unit,
) {
    val allocation = state.allocation
    val site =
        (state.choices as? LotteryCampsiteChoices.Content)
            ?.campsites
            ?.firstOrNull { it.id == state.draft.campsiteId }
    AppScreen(stringResource(R.string.lottery_allocate), onBack = onBack, bottomContent = {
        ActionFooter {
            AppActionButton(
                stringResource(R.string.lottery_apply_nights),
                onApply,
                Modifier.fillMaxWidth(),
                enabled = site != null && allocation?.canApply == true,
            )
        }
    }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when {
                state.choices == LotteryCampsiteChoices.Loading -> {
                    LoadingContent()
                }

                state.choices == LotteryCampsiteChoices.Failure -> {
                    FailureContent(stringResource(R.string.lottery_site_failure), onRetryChoices)
                }

                site == null -> {
                    Text(stringResource(R.string.lottery_selected_site_unavailable))
                }

                allocation == null -> {
                    LoadingContent()
                }

                else -> {
                    Text(site.details.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        site.details.timeZone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (allocation.inventory) {
                        CalendarInventoryRead.Loading -> {
                            LoadingContent()
                        }

                        CalendarInventoryRead.Failure -> {
                            FailureContent(
                                stringResource(R.string.lottery_allocation_failure),
                                onRetryInventory,
                            )
                        }

                        is CalendarInventoryRead.Content -> {
                            CampsiteCalendar(
                                lotteryAllocationCalendarState(allocation),
                                onMonth,
                                onDate,
                            )
                        }
                    }
                    SummarySection(stringResource(R.string.lottery_selected_nights)) {
                        LotteryAllocatedNights(allocation.selected)
                    }
                    if (allocation.invalidSelection) {
                        Text(
                            stringResource(R.string.lottery_allocation_invalid),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        stringResource(R.string.lottery_allocation_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shows selected/free/booked dates using the same shared calendar as campsite availability. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun LotteryAllocationPreview() {
    val site = previewCampsite()
    val day = LocalDate.of(2027, 5, 14)
    CampsbridgeTheme {
        LotteryAllocationScreen(
            LotterySetupUiState(
                draft = LotteryDraft(step = LotteryStep.SITE, campsiteId = site.id),
                choices = LotteryCampsiteChoices.Content(listOf(site)),
                allocation =
                    LotteryAllocationUiState(
                        YearMonth.from(day),
                        setOf(day),
                        CalendarInventoryRead.Content(
                            listOf(
                                CalendarNight(day, true),
                                CalendarNight(day.plusDays(1), true, "preview-booking"),
                            ),
                        ),
                    ),
            ),
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

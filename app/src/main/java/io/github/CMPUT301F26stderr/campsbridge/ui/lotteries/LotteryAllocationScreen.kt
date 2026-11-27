/*
 * Displays a working lottery allocation through the shared calendar; changes stay local until applied.
 */

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import io.github.CMPUT301F26stderr.campsbridge.ui.components.calendar.NightRangeCalendar
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.ActionFooter
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/** A working allocation child; its actions never save offered inventory or create a lottery.
 * @param choices Current campsite-source outcome shared by creation and saved editing.
 * @param campsiteId Focused saved campsite identity.
 * @param allocation Temporary selection and required inventory range.
 * @param onBack Discards only unapplied child edits.
 * @param onApply Applies known eligible working nights to the parent.
 * @param onMonth Changes the required visible-month range.
 * @param onSelectionChange Replaces the working nights after adding or removing a range.
 * @param onRetryInventory Retries the required inventory range.
 * @param onRetryChoices Retries the selected campsite source.
 * @param enabled Whether the parent context still permits allocation changes.
 */
@Composable
fun LotteryAllocationScreen(
    choices: LotteryCampsiteChoices,
    campsiteId: String?,
    allocation: LotteryAllocationUiState?,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onSelectionChange: (Set<LocalDate>) -> Unit,
    onRetryInventory: () -> Unit,
    onRetryChoices: () -> Unit,
    enabled: Boolean = true,
) {
    val site =
        (choices as? LotteryCampsiteChoices.Content)
            ?.campsites
            ?.firstOrNull { it.id == campsiteId }
    AppScreen(stringResource(R.string.lottery_allocate), onBack = onBack, bottomContent = {
        ActionFooter {
            AppActionButton(
                stringResource(R.string.lottery_apply_nights),
                onApply,
                Modifier.fillMaxWidth(),
                enabled = enabled && site != null && allocation?.canApply == true,
            )
        }
    }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when {
                choices == LotteryCampsiteChoices.Loading -> {
                    LoadingContent()
                }

                choices == LotteryCampsiteChoices.Failure -> {
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
                    if (!enabled) AllocationLockedNotice()
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
                            NightRangeCalendar(
                                lotteryAllocationCalendarState(allocation),
                                onMonth,
                                onSelectionChange,
                                enabled = enabled,
                            )
                        }
                    }
                    Text(
                        pluralStringResource(
                            R.plurals.lottery_allocated_nights,
                            allocation.selected.size,
                            allocation.selected.size,
                        ),
                    )
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
            LotteryCampsiteChoices.Content(listOf(site)),
            site.id,
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
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

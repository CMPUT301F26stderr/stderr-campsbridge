/*
 * Shows owned facts and recovery without acquiring a repository or pretending to save.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.CalendarInventoryRead
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import java.time.LocalDate
import java.time.YearMonth

/** Shows owned facts and recovery without acquiring a repository or pretending to save.
 * @param state Local set or required-source outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large owned allocations", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun LotteryAllocationsEditPreview(
    @PreviewParameter(LotteryAllocationsEditPreviewProvider::class) state: LotteryAllocationsEditUiState,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryAllocationsEditScreen(
            state,
            (state as? LotteryAllocationsEditUiState.Content)?.isSaving == true,
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

/** Shows the separate owned listing picker with retained context and standard navigation rows. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large allocation choices", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun LotteryAllocationChoicePreview() {
    val content = LotteryAllocationsEditPreviewProvider().values.first()
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryAllocationChoiceScreen(content, {}, {}, {})
    }
}

/** Shows noncontiguous working dates in the actual shared calendar at both control scales. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large working allocation", showBackground = true, widthDp = 360, heightDp = 844, fontScale = 2f)
@Composable
private fun SavedAllocationCalendarPreview() {
    val content = LotteryAllocationsEditPreviewProvider().values.first() as LotteryAllocationsEditUiState.Content
    val day = LocalDate.of(2027, 5, 18)
    val allocation =
        LotteryAllocationUiState(
            YearMonth.from(day),
            setOf(day, day.plusDays(4)),
            CalendarInventoryRead.Content(listOf(0L, 1L, 4L).map { CalendarNight(day.plusDays(it), true) }),
        )
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        LotteryAllocationScreen(
            LotteryCampsiteChoices.Content(content.campsites),
            content.offers.first().campsiteId,
            allocation,
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}

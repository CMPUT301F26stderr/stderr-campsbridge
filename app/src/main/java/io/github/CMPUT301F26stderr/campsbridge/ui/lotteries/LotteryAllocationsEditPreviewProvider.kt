/*
 * Supplies real-shaped local sets, required-source outcomes and explicit operation feedback.
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

/** Supplies real-shaped local sets, required-source outcomes and explicit operation feedback. */
class LotteryAllocationsEditPreviewProvider : PreviewParameterProvider<LotteryAllocationsEditUiState> {
    private val site =
        previewCampsite().let {
            it.copy(details = it.details.copy(name = "Riverbend family campsite and riverside pitches"))
        }
    private val record =
        (LotteryDetailsEditPreviewProvider().values.first() as LotteryDetailsEditUiState.Content)
            .lottery
            .let { it.copy(participantOwnerIds = it.participantOwnerIds + site.ownerId) }
    private val content =
        LotteryAllocationsEditUiState.Content(
            record,
            listOf(LotteryOffer(site.id, listOf(18, 19, 22).map { LocalDate.of(2027, 5, it) })),
            listOf(site, site.copy(id = "site-b", details = site.details.copy(name = "Prairie Meadow"))),
            true,
            canSave = true,
            isDirty = true,
        )
    override val values: Sequence<LotteryAllocationsEditUiState> =
        sequenceOf(
            content,
            content.copy(offers = emptyList()),
            content.copy(isEditable = false, canSave = false),
            content.copy(isSaving = true, canSave = false),
            content.copy(saveFailed = true),
            content.copy(
                contextChanged = true,
                savedOffers = listOf(LotteryOffer(site.id, listOf(LocalDate.of(2027, 5, 19)))),
            ),
            LotteryAllocationsEditUiState.Loading,
            LotteryAllocationsEditUiState.Failure,
            LotteryAllocationsEditUiState.Unavailable,
        )
}
